/**
 * Guardian (one per person, deterministic). Turns watch/phone input and live body signals into
 * graph facts: conditions (private) and Episodes linked to context Bands, then hands a "plan"
 * task to that person's Planner. It also notices deviations from the person's own baseline and
 * ASKS before assuming anything.
 */
import { config } from "../config.js";
import { now } from "../graph/db.js";
import { bodyRead, homeRead } from "../graph/graphs.js";
import * as w from "../writeapi/index.js";
import { contextBands, type Context } from "../ingest/bands.js";
import { latestHr, meanHr, rmssd, latestRoom, hrSamples } from "../ingest/buffer.js";
import { bus, newId } from "../bus.js";
import { prompt, say } from "../channels.js";
import { proposeMorning, tryAnswerMorning } from "./morning.js";

const MIN = 60_000;

/** Chips on the watch/phone and what they mean in the graph. */
export const CHIPS: Record<string, { condition?: string; hours?: number; episode?: string; summary: string }> = {
  sick: { condition: "sick", hours: 18, episode: "unwell", summary: "said they feel sick" },
  fever: { condition: "fever", hours: 18, episode: "unwell", summary: "reported a fever" },
  cold: { condition: "cold_symptoms", hours: 24, episode: "unwell", summary: "reported cold symptoms" },
  headache: { condition: "headache", hours: 4, episode: "unwell", summary: "reported a headache" },
  too_hot: { episode: "too_hot", summary: "said it's too hot" },
  too_cold: { episode: "too_cold", summary: "said it's too cold" },
  stressed: { condition: "stressed", hours: 2, episode: "stress", summary: "said they feel stressed" },
  workout: { condition: "post_workout", hours: 1, episode: "recovery", summary: "just finished a workout" },
  sleep: { condition: "sleeping", hours: 9, episode: "sleep", summary: "is going to sleep" },
  period: { condition: "menstrual", hours: 24 * 4, summary: "logged their period" },
};

const lastEpisodeAt = new Map<string, number>();

export async function baseline(alias: string, signal: string): Promise<{ mean: number; sd: number } | null> {
  const rows = await bodyRead(alias, "get_baselines", { alias, now: now() });
  const b = rows.find((r) => r.signal === signal);
  return b ? { mean: b.mean, sd: b.sd || 1 } : null;
}

export async function currentContext(alias: string, kind: string, extra: Partial<Context> = {}): Promise<Context> {
  const [hrB, rmB, conds, present] = await Promise.all([
    baseline(alias, "hr"),
    baseline(alias, "rmssd"),
    bodyRead(alias, "active_conditions", { alias, now: now() }),
    homeRead("present_people", { room: w.DEFAULT_ROOM }),
  ]);
  const hr = meanHr(alias, 20_000);
  const rm = rmssd(alias, 120_000);
  const room = latestRoom();
  return {
    kind,
    at: now(),
    hrZ: hr != null && hrB ? (hr - hrB.mean) / hrB.sd : null,
    rmssdZ: rm != null && rmB ? (rm - rmB.mean) / rmB.sd : null,
    roomTemp: room?.temperature ?? null,
    humidity: room?.humidity ?? null,
    bright: room?.bright ?? null,
    occupants: present.length,
    conditions: conds.map((c) => c.kind),
    ...extra,
  };
}

/** Open an episode for this person and hand it to their planner. */
export async function raiseEpisode(alias: string, kind: string, summary: string, trigger: string, extra: Partial<Context> = {}) {
  const ctx = await currentContext(alias, kind, extra);
  const hr = latestHr(alias)?.hr;
  const fullSummary = `${alias} ${summary}${hr ? ` (HR ${Math.round(hr)})` : ""}${ctx.roomTemp != null ? `, room ${ctx.roomTemp.toFixed(1)} °C` : ""}`;
  const episode = await w.openEpisode(alias, { kind, bands: contextBands(ctx), summary: fullSummary, trigger });
  await w.linkEvent(alias, episode, { id: newId(`ev-${alias}`), kind: trigger, at: now(), source: trigger.split(":")[0] });
  lastEpisodeAt.set(`${alias}:${kind}`, now());
  bus.feed({ agent: `guardian:${alias}`, kind: "episode", person: alias, text: `Episode "${kind}": ${fullSummary}`, data: { episode, bands: contextBands(ctx).map((b) => b.key) } });
  const task = await w.createTask({ agent: `planner:${alias}`, kind: "plan", owner: alias, payload: { episode, kind } });
  return { episode, task };
}

export async function handleChip(alias: string, chip: string, source = "watch") {
  if (chip === "better" || chip === "worse") {
    const r = await w.addFeedback(alias, chip === "better" ? 1 : -1, chip);
    await say(alias, chip === "better" ? "Good to hear. I'll remember that." : "Sorry. I'll remember that and try something else.");
    return { feedback: r };
  }
  if (chip === "morning") return proposeMorning(alias);
  if (chip === "fine" || chip === "well") {
    for (const k of ["sick", "fever", "cold_symptoms", "headache", "stressed"]) await w.clearCondition(alias, k, source).catch(() => undefined);
    await say(alias, "Glad you're feeling better. Back to your usual settings.");
    return { cleared: true };
  }
  const spec = CHIPS[chip];
  if (!spec) throw new Error(`unknown chip ${chip}`);
  if (!config.graphEnabled) return ablationChip(alias, chip);
  if (spec.condition) await w.setCondition(alias, spec.condition, { hours: spec.hours, source });
  if (!spec.episode) {
    await say(alias, "Noted.");
    return { condition: spec.condition };
  }
  return raiseEpisode(alias, spec.episode, spec.summary, `${source}:${chip}`);
}

/** Ablation: no graph -> one fixed rule per chip, no personalisation, no memory, no explanation. */
async function ablationChip(alias: string, chip: string) {
  const { apply } = await import("../devices/index.js");
  const rule: Record<string, [string, string, any]> = {
    sick: ["ac", "virtual", { setpoint: 27 }],
    too_hot: ["ac", "virtual", { setpoint: 22 }],
    workout: ["ac", "virtual", { setpoint: 22 }],
    too_cold: ["ac", "virtual", { setpoint: 27 }],
  };
  const r = rule[chip];
  if (r) await apply(r[0], r[1], r[2]);
  bus.feed({ agent: "fixed-rules", kind: "ablation", person: alias, text: `HOMEOS_GRAPH=off: fixed rule for "${chip}" → ${r ? JSON.stringify(r[2]) : "nothing"}` });
  await say(alias, r ? "Okay, adjusted the AC." : "Okay.");
  return { ablation: true };
}

/** Background watch on live signals: post-exercise and stress episodes, measured against YOUR baseline. */
export function startGuardian(alias: string): () => void {
  const h = setInterval(async () => {
    if (!config.graphEnabled) return;
    try {
      const recent = hrSamples(alias, now() - 25_000);
      if (recent.length < 15) return;
      const hrB = (await baseline(alias, "hr")) ?? { mean: 72, sd: 8 };
      const z = recent.reduce((a, b) => a + b.hr, 0) / recent.length;
      const hrZ = (z - hrB.mean) / hrB.sd;
      const since = (k: string) => now() - (lastEpisodeAt.get(`${alias}:${k}`) ?? 0);
      if (hrZ > 2.5 && since("recovery") > 10 * MIN) {
        await w.setCondition(alias, "post_workout", { hours: 1, source: "auto:hr" });
        await raiseEpisode(alias, "recovery", `heart rate ${Math.round(z)} bpm, ${hrZ.toFixed(1)} SD above their baseline`, "auto:hr", {
          activity: "post_exercise",
        });
        return;
      }
      const rm = rmssd(alias, 120_000);
      const rmB = await baseline(alias, "rmssd");
      if (rm != null && rmB && (rm - rmB.mean) / rmB.sd < -1.5 && hrZ < 1 && since("stress") > 20 * MIN) {
        lastEpisodeAt.set(`${alias}:stress`, now());
        const id = newId("prompt");
        prompt(alias, id, "Your HRV dropped well below your usual. Want a short breathing break?", ["yes", "no"]);
        pendingPrompts.set(id, { alias, kind: "stress" });
      }
    } catch (e) {
      console.error(`[guardian:${alias}]`, (e as Error).message);
    }
  }, 5000);
  return () => clearInterval(h);
}

/** Proactive check when ring/phone data arrives: resting HR well above baseline -> ask, don't assume. */
export async function checkRestingHr(alias: string, rhr: number) {
  const b = await baseline(alias, "hr_rest");
  if (!b || b.sd <= 0) return;
  const z = (rhr - b.mean) / b.sd;
  if (z > 1.5) {
    const id = newId("prompt");
    pendingPrompts.set(id, { alias, kind: "unwell" });
    prompt(alias, id, `Your resting heart rate is ${Math.round(rhr)}, about ${Math.round(rhr - b.mean)} above your usual. Feeling unwell?`, ["yes", "no"]);
  }
}

const pendingPrompts = new Map<string, { alias: string; kind: string }>();

export async function answerPrompt(alias: string, id: string, answer: string) {
  const morning = await tryAnswerMorning(alias, id, answer);
  if (morning) return morning;
  const p = pendingPrompts.get(id);
  if (!p || p.alias !== alias) return null;
  pendingPrompts.delete(id);
  if (answer !== "yes") return { ignored: true };
  if (p.kind === "unwell") return handleChip(alias, "sick", "prompt");
  if (p.kind === "stress") return raiseEpisode(alias, "stress", "accepted a breathing break after an HRV drop", "prompt:stress", { activity: "rest" });
  return null;
}
