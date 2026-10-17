/**
 * Planner (one per person, LLM + graph). For an episode it:
 *  1. ranks interventions from the person's own Evidence (choose_intervention, Thompson UDF),
 *  2. checks each against what everyone present can accept (feasible_settings, multi-hop),
 *  3. lets the LLM pick the final plan and write the rationale - it may call more graph tools
 *     through MCP (similar_episodes, what_works, playbooks_for ...) before deciding,
 *  4. posts Intents for shared devices and hands an "arbitrate" task to the Arbiter,
 *  5. when the Arbiter hands back the result, records Decision -> Actions in the private graph
 *     and schedules an "evaluate" task for the Evaluator.
 */
import { config } from "../config.js";
import { now } from "../graph/db.js";
import { bodyRead, homeRead } from "../graph/graphs.js";
import * as w from "../writeapi/index.js";
import { bus } from "../bus.js";
import { pacer, say } from "../channels.js";
import { runAgent } from "../llm/agent.js";
import { llmAvailable } from "../llm/azure.js";
import { startWorker, type ClaimedTask } from "./worker.js";
import type { Considered } from "../writeapi/index.js";

const PRIORITY: Record<string, number> = { unwell: 3, recovery: 2, too_hot: 2, too_cold: 2, stress: 1, sleep: 1 };

interface Candidate {
  intervention: string;
  label: string;
  device: string | null;
  var: string | null;
  params: Record<string, number>;
  draw: number;
  mean: number;
  n: number;
  playbooks: string[];
  setting: Record<string, number> | null;
  feasible: boolean;
  limited: boolean;
  why: string | null;
  bounds: { id: string; var: string; min: number | null; max: number | null; mine: boolean }[];
}

function boundText(alias: string, row: any, edge: "min" | "max", value: number, unit: string, conditions: string[]): string {
  const mine = (row.bound_by as string[]).includes(alias);
  const others = (row.bound_by as string[]).some((b) => b !== alias);
  const who = others ? "someone present" : mine ? `your own comfort${conditions.length ? ` (${conditions.join(", ")})` : ""}` : "the device limits";
  return `${edge === "min" ? "floor" : "ceiling"} ${value}${unit} set by ${who}`;
}

/** Turn a ranked intervention into a concrete setting, clamped to the room's feasible band. */
function evaluateCandidate(alias: string, r: any, feasible: any[], conditions: string[]): Candidate {
  const params = JSON.parse(r.params ?? "{}");
  const c: Candidate = {
    intervention: r.intervention,
    label: r.label,
    device: r.device,
    var: r.var,
    params,
    draw: r.draw,
    mean: r.mean,
    n: r.n,
    playbooks: r.playbooks ?? [],
    setting: null,
    feasible: true,
    limited: false,
    why: null,
    bounds: [],
  };
  if (!r.device || r.device === "watch" || r.intervention === "none") return c;
  const row = feasible.find((f) => f.device === r.device && f.var === r.var);
  if (!row) {
    c.feasible = false;
    c.why = `no ${r.device} in this room`;
    return c;
  }
  c.bounds = (row.constraint_ids as string[]).map((id: string, i: number) => ({
    id,
    var: row.var,
    min: row.min,
    max: row.max,
    mine: row.bound_by[i] === alias,
  }));
  const clamp = (x: number) => Math.min(row.max ?? Infinity, Math.max(row.min ?? -Infinity, x));
  const overriddenMine = (row.overridden_for as string[] | undefined)?.includes(alias);
  if (r.device === "ac") {
    const current = row.power === "on" && row.setpoint != null ? row.setpoint : 26;
    const want = current + (params.delta ?? 0);
    const got = clamp(want);
    c.setting = { setpoint: got };
    if (row.power === "on" && got === current) {
      c.feasible = false;
      c.why = `AC already at ${got} °C — ${boundText(alias, row, want < got ? "min" : "max", got, " °C", conditions)}`;
    } else if (got !== want) {
      c.limited = true;
      c.why = `limited to ${got} °C — ${boundText(alias, row, want < got ? "min" : "max", got, " °C", conditions)}`;
    }
  } else if (r.device === "fan") {
    const want = params.level ?? 0;
    const got = clamp(want);
    c.setting = { level: got };
    if (got !== want) {
      if ((row.level ?? 0) === got) {
        c.feasible = false;
        c.why = `fan can't go to level ${want} — ${boundText(alias, row, want < got ? "min" : "max", got, "", conditions)}`;
      } else {
        c.limited = true;
        c.why = `fan limited to level ${got} — ${boundText(alias, row, want < got ? "min" : "max", got, "", conditions)}`;
      }
    }
  } else if (r.device === "light") {
    const want = params.brightness ?? 50;
    const got = clamp(want);
    c.setting = { brightness: got };
    if (got !== want) {
      c.limited = true;
      c.why = `brightness limited to ${got}% — ${boundText(alias, row, want < got ? "min" : "max", got, "%", conditions)}`;
    }
  }
  if (overriddenMine && c.why) c.why += "; your own limit gave way to a higher-priority need of someone present";
  else if (overriddenMine) c.why = "your own limit gave way to a higher-priority need of someone present";
  return c;
}

/** Deterministic plan: best feasible option per device, plus a breathing pacer when it ranks well. */
function defaultPlan(cands: Candidate[], kind: string): string[] {
  const picked: string[] = [];
  const devices = new Set<string>();
  for (const c of cands) {
    if (!c.feasible || c.intervention === "none") continue;
    const dev = c.device ?? "none";
    if (devices.has(dev)) continue;
    if (dev === "watch" && !["recovery", "stress"].includes(kind)) continue;
    devices.add(dev);
    picked.push(c.intervention);
    if (picked.length >= 2) break;
  }
  return picked;
}

async function llmPlan(alias: string, kind: string, episode: string, cands: Candidate[]): Promise<{ pick: string[]; rationale: string } | null> {
  if (!llmAvailable()) return null;
  const table = cands.slice(0, 8).map((c) => ({
    intervention: c.intervention,
    label: c.label,
    device: c.device,
    evidence_mean: Number(c.mean?.toFixed?.(2) ?? c.mean),
    evidence_n: c.n,
    thompson_draw: Number(c.draw?.toFixed?.(2) ?? c.draw),
    playbooks: c.playbooks,
    feasible: c.feasible,
    note: c.why,
  }));
  const system = `You are the HomeOS planner for ${alias}. You choose home actions for one episode from graph evidence.
Rules: pick 1-2 interventions, at most one per device, only feasible ones. Prefer higher thompson_draw and evidence; respect playbooks.
A "limited to ..." note is NOT a reason to skip an option: it means the setting was already adjusted to fit everyone present, so it is safe and still helps.
Unless every device option is infeasible, include the best device action (AC, fan or light) - a pacer alone is not enough for unwell/too_hot/too_cold.
For recovery/stress you may add one pacer_* (watch breathing pacer). You may call graph tools (similar_episodes, what_works, playbooks_for, get_context) if useful, at most twice.
Reply ONLY with JSON: {"pick":["name",...],"rationale":"one short sentence for ${alias}, mention any limit set by someone present without naming them"}`;
  try {
    const r = await Promise.race([
      runAgent({
        alias,
        system,
        maxSteps: 4,
        allow: (n) => ["similar_episodes", "what_works", "playbooks_for", "get_context"].includes(n),
        messages: [{ role: "user", content: JSON.stringify({ episode, kind, candidates: table }) }],
        onTool: (t) => bus.feed({ agent: `planner:${alias}`, kind: "tool", person: alias, text: `MCP tool → ${t.tool}` }),
      }),
      new Promise<null>((res) => setTimeout(() => res(null), 20_000)),
    ]);
    if (!r) return null;
    const m = r.text.match(/\{[\s\S]*\}/);
    if (!m) return null;
    const j = JSON.parse(m[0]);
    const valid = (j.pick as string[]).filter((p) => cands.some((c) => c.intervention === p && c.feasible));
    return valid.length ? { pick: valid, rationale: String(j.rationale ?? "") } : null;
  } catch (e) {
    console.warn(`[planner:${alias}] LLM plan failed:`, (e as Error).message);
    return null;
  }
}

export async function plan(alias: string, t: ClaimedTask) {
  const { episode, kind } = t.payload;
  const tNow = now();
  const [ranking, feasible, conds] = await Promise.all([
    bodyRead(alias, "choose_intervention", { episode, alias, now: tNow }),
    homeRead("feasible_settings", { room: w.DEFAULT_ROOM, now: tNow }),
    bodyRead(alias, "active_conditions", { alias, now: tNow }),
  ]);
  const conditions = conds.map((c) => c.kind as string);
  // The "none" control arm is only used in consented experiments (Scientist), never in live care.
  const cands = ranking
    .filter((r) => !r.disliked && r.intervention !== "none")
    .map((r) => evaluateCandidate(alias, r, feasible, conditions));
  const disliked = ranking.filter((r) => r.disliked).map((r) => r.intervention as string);

  const ai = await llmPlan(alias, kind, episode, cands);
  const pick = ai?.pick ?? defaultPlan(cands, kind);
  const considered: Considered[] = [
    ...cands.slice(0, 8).map((c) => ({
      name: c.intervention,
      score: Number(c.draw),
      chosen: pick.includes(c.intervention),
      rejected_because: !c.feasible ? c.why : pick.includes(c.intervention) ? (c.limited ? c.why : null) : null,
    })),
    ...disliked.map((d) => ({ name: d, score: 0, chosen: false, rejected_because: `${alias} dislikes this${conditions.length ? ` while ${conditions.join(", ")}` : ""}` })),
  ];
  const chosen = cands.filter((c) => pick.includes(c.intervention));
  bus.feed({
    agent: `planner:${alias}`,
    kind: "plan",
    person: alias,
    text: `Plan: ${chosen.map((c) => c.label).join(" + ") || "do nothing"}${ai ? " (LLM via MCP)" : " (evidence ranking)"}`,
    data: { considered },
  });

  // Personal device: run the pacer now. Shared devices: post intents for the arbiter.
  const pacerPick = chosen.find((c) => c.device === "watch");
  if (pacerPick) pacer(alias, pacerPick.params.bpm, 120);
  const intents: string[] = [];
  for (const c of chosen.filter((c) => c.device && c.device !== "watch" && c.setting)) {
    intents.push(
      await w.postIntent({
        agent: `planner:${alias}`,
        var: c.var,
        target: Object.values(c.setting!)[0],
        device: c.device,
        intervention: c.intervention,
        priority: PRIORITY[kind] ?? 1,
        episode,
      }),
    );
  }
  const payload = {
    episode,
    kind,
    owner: alias,
    considered,
    rationale: ai?.rationale ?? "",
    planned_by: ai ? "llm+graph" : "graph",
    pacer: pacerPick ? { intervention: pacerPick.intervention, bpm: pacerPick.params.bpm } : null,
    candidates: chosen.map((c) => ({ intervention: c.intervention, label: c.label, device: c.device, setting: c.setting, bounds: c.bounds, why: c.why })),
    intents,
  };
  await w.createTask({ agent: "arbiter", kind: "arbitrate", owner: alias, payload, from: t.id });
  return { intents: intents.length, pacer: Boolean(pacerPick) };
}

/** Arbiter handed back what was applied: write provenance into the private graph and schedule evaluation. */
export async function record(alias: string, t: ClaimedTask) {
  const p = t.payload;
  const respected = (p.bounds ?? []) as { id: string; var: string; min: number | null; max: number | null; mine: boolean }[];
  const decision = await w.logDecision(alias, {
    episode: p.episode,
    by: `planner:${alias}`,
    rationale: p.rationale || p.summary || "",
    considered: p.considered,
    arbitration: p.arbitration ?? null,
    respected,
  });
  const evalDue = now() + config.evalWindowSec * 1000;
  const actions: string[] = [];
  for (const a of p.applied ?? []) {
    actions.push(await w.logAction(alias, { decision, intervention: a.intervention, device: a.device, applied: a.setting, evalDue }));
  }
  if (p.pacer) actions.push(await w.logAction(alias, { decision, intervention: p.pacer.intervention, device: "watch", applied: { bpm: p.pacer.bpm }, evalDue }));
  if (!actions.length) {
    const noneAction = await w.logAction(alias, { decision, intervention: "none", device: null, applied: {}, evalDue });
    actions.push(noneAction);
  }
  await w.createTask({
    agent: "evaluator",
    kind: "evaluate",
    owner: alias,
    payload: { episode: p.episode, kind: p.kind, decision, actions, actedAt: now() },
    from: t.id,
    dueAt: evalDue,
  });
  bus.feed({ agent: `planner:${alias}`, kind: "decision", person: alias, text: `Decision ${decision} recorded with ${actions.length} action(s)`, data: { decision } });
  bus.emit("decision", { person: alias, decision });
  await say(alias, p.message ?? "Done.", { agent: `planner:${alias}` });
  return { decision, actions };
}

export function startPlanner(alias: string): void {
  startWorker(`planner:${alias}`, async (t) => (t.kind === "plan" ? plan(alias, t) : t.kind === "record" ? record(alias, t) : null));
}
