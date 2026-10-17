/**
 * Evaluator (deterministic). When an action's window has passed, it measures how the person's
 * body actually responded (from the raw buffer, against their own baseline) or uses their explicit
 * feedback, then folds the effect into Evidence with one atomic Welford update per context band.
 * Next time the planner ranks interventions, the learned evidence is what it sees.
 */
import { config } from "../config.js";
import { now } from "../graph/db.js";
import { bodyRead } from "../graph/graphs.js";
import * as w from "../writeapi/index.js";
import { meanHr, rmssd, hrSamples } from "../ingest/buffer.js";
import { bus } from "../bus.js";
import { prompt } from "../channels.js";
import { baseline } from "./guardian.js";
import { startWorker, type ClaimedTask } from "./worker.js";

const clamp = (x: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, x));

async function feedbackSince(alias: string, since: number): Promise<number | null> {
  const { run, agentUser } = await import("../graph/db.js");
  const { bodyGraph } = await import("../config.js");
  const rows = await run(
    bodyGraph(alias),
    "MATCH (f:Feedback) WHERE f.at >= $since RETURN avg(f.rating) AS r, count(f) AS n",
    { since },
    { as: agentUser(alias), readOnly: true },
  );
  return rows[0]?.n ? (rows[0].r as number) : null;
}

export async function measure(alias: string, kind: string, actedAt: number) {
  const window = config.evalWindowSec * 1000;
  const before = meanHr(alias, 30_000, actedAt);
  const after = meanHr(alias, 30_000, actedAt + window);
  const hrB = (await baseline(alias, "hr")) ?? { mean: 72, sd: 8 };
  const fb = await feedbackSince(alias, actedAt);
  if (kind === "recovery" && before != null && after != null) {
    // Fraction of the way back to baseline within the window (1 = fully recovered).
    const gap = Math.max(before - hrB.mean, 5);
    const effect = clamp((before - after) / gap, -1, 1.5);
    const samples = hrSamples(alias, actedAt, actedAt + window);
    const target = hrB.mean + gap / 2;
    const half = samples.find((s) => s.hr <= target);
    return { effect, signal: "hr", d_hr: after - before, recover_s: half ? Math.round((half.t - actedAt) / 1000) : null, method: "hr_recovery" };
  }
  if (kind === "stress") {
    const r0 = rmssd(alias, 120_000, actedAt);
    const r1 = rmssd(alias, 120_000, actedAt + window);
    const rB = await baseline(alias, "rmssd");
    if (r0 != null && r1 != null) {
      return { effect: clamp((r1 - r0) / (rB?.sd || 15), -1.5, 2), signal: "rmssd", d_rmssd: r1 - r0, method: "rmssd_change" };
    }
    if (before != null && after != null) return { effect: clamp((before - after) / 10, -1, 1.5), signal: "hr", d_hr: after - before, method: "hr_drop" };
  }
  if (fb != null) return { effect: clamp(fb, -1, 1), signal: "thermal_comfort", method: "feedback" };
  return null;
}

export async function evaluate(t: ClaimedTask) {
  const { episode, kind, actions, actedAt } = t.payload;
  const alias = t.owner!;
  const m = await measure(alias, kind, actedAt);
  if (!m) {
    // Nothing measurable and no feedback: ask once, learn nothing (never invent an outcome).
    prompt(alias, `fb-${episode}`, "Did that help?", ["better", "worse"]);
    await w.closeEpisode(alias, episode, null);
    bus.feed({ agent: "evaluator", kind: "evaluate", person: alias, text: `No measurable signal for ${kind}; asked ${alias} for feedback instead` });
    return { learned: false };
  }
  const before = await bodyRead(alias, "what_works", { kind, limit: 5 });
  const updates = [];
  for (const action of actions as string[]) updates.push(...(await w.learn(alias, { action, ...m })));
  await w.closeEpisode(alias, episode, m.effect);
  const after = await bodyRead(alias, "what_works", { kind, limit: 5 });
  bus.feed({
    agent: "evaluator",
    kind: "learned",
    person: alias,
    text: `Measured ${m.method}: effect ${m.effect.toFixed(2)}${"d_hr" in m && m.d_hr != null ? ` (HR ${m.d_hr > 0 ? "+" : ""}${Math.round(m.d_hr as number)})` : ""}. Updated ${updates.length} evidence nodes.`,
    data: { before, after, updates, measure: m },
  });
  bus.emit("learned", { person: alias, kind, before, after });
  return { learned: true, effect: m.effect, updates: updates.length };
}

export function startEvaluator(): void {
  startWorker("evaluator", async (t) => (t.kind === "evaluate" ? evaluate(t) : null), 1000);
}
