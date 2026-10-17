/**
 * Arbiter (home-level, deterministic). Owns the shared devices. It reads intents and everyone's
 * anonymous comfort constraints from the home graph (it cannot read anyone's private graph),
 * re-clamps each requested setting to the band all present people accept, takes an atomic device
 * lease, applies the setting, records an Arbitration node, and hands a "record" task back to the
 * owner's planner.
 */
import { now } from "../graph/db.js";
import { homeRead } from "../graph/graphs.js";
import * as w from "../writeapi/index.js";
import { apply } from "../devices/index.js";
import { bus, newId } from "../bus.js";
import { startWorker, type ClaimedTask } from "./worker.js";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function arbitrate(t: ClaimedTask) {
  const p = t.payload;
  const owner: string = p.owner;
  const feasible = await homeRead("feasible_settings", { room: w.DEFAULT_ROOM, now: now() });
  const applied: { intervention: string; device: string; setting: Record<string, number>; label: string }[] = [];
  const bounds: { id: string; var: string; min: number | null; max: number | null; mine: boolean }[] = [];
  const notes: string[] = [];
  const arbitrationId = newId("arb");

  for (const c of p.candidates ?? []) {
    if (!c.device || c.device === "watch" || !c.setting) continue;
    const row = feasible.find((f) => f.device === c.device && f.strength >= 1);
    if (!row) continue;
    const key = Object.keys(c.setting)[0];
    const want = Number(c.setting[key]);
    const got = Math.min(row.max ?? Infinity, Math.max(row.min ?? -Infinity, want));
    (row.constraint_ids as string[]).forEach((id: string, i: number) =>
      bounds.push({ id, var: row.var, min: row.min, max: row.max, mine: row.bound_by[i] === owner }),
    );
    let leased = false;
    for (let i = 0; i < 5 && !leased; i++) {
      leased = await w.acquireDevice(c.device, "arbiter", 10_000);
      if (!leased) await sleep(300);
    }
    if (!leased) {
      notes.push(`${c.device} busy`);
      continue;
    }
    try {
      const res = await apply(c.device, row.driver, { [key]: got });
      applied.push({ intervention: c.intervention, device: c.device, setting: { [key]: got }, label: c.label });
      if (got !== want) notes.push(`${c.device} ${key} limited to ${got}`);
      if (res.error) notes.push(`${c.device}: ${res.error}`);
    } finally {
      await w.releaseDevice(c.device, "arbiter");
    }
  }

  const otherBound = bounds.some((b) => !b.mine);
  const summary = `${owner}: ${applied.map((a) => `${a.device} ${JSON.stringify(a.setting)}`).join(", ") || "no device change"}${
    otherBound ? " (bounded by another person's comfort range)" : ""
  }`;
  await w.recordArbitration({
    id: arbitrationId,
    summary,
    intents: p.intents ?? [],
    constraints: [...new Set(bounds.map((b) => b.id))],
    applied: applied.map((a) => ({ device: a.device, setting: JSON.stringify(a.setting) })),
  });
  bus.feed({ agent: "arbiter", kind: "arbitration", person: owner, text: summary, data: { arbitration: arbitrationId, applied, notes } });

  await w.createTask({
    agent: `planner:${owner}`,
    kind: "record",
    owner,
    from: t.id,
    payload: {
      ...p,
      arbitration: arbitrationId,
      applied,
      bounds,
      summary,
      message: composeMessage(p, applied),
    },
  });
  return { arbitration: arbitrationId, applied: applied.length };
}

/** What the person hears on their watch. Never names who set a limit, or why. */
function composeMessage(p: any, applied: { device: string; setting: Record<string, number>; label: string }[]): string {
  const parts: string[] = [];
  for (const a of applied) {
    if (a.device === "ac") parts.push(`I've set the AC to ${a.setting.setpoint} degrees`);
    else if (a.device === "fan") parts.push(a.setting.level > 0 ? `turned the fan to level ${a.setting.level}` : "switched the fan off");
    else if (a.device === "light") parts.push(`dimmed the light to ${a.setting.brightness} percent`);
  }
  if (p.pacer) parts.push(`started a ${p.pacer.bpm} breaths-a-minute pacer on your watch`);
  let msg = parts.length ? parts.join(", ") + "." : "I'll leave the room as it is for now.";
  msg = msg[0].toUpperCase() + msg.slice(1);
  const cands = (p.candidates ?? []) as any[];
  const rejected = ((p.considered ?? []) as any[]).filter((c) => !c.chosen && c.rejected_because);
  const otherLimit = [...cands.map((c) => c.why ?? ""), ...rejected.map((c) => c.rejected_because)].some((w: string) => /someone present/.test(w));
  const ownLimit = [...cands.map((c) => c.why ?? ""), ...rejected.map((c) => c.rejected_because)].find((w: string) => /your own comfort/.test(w));
  if (otherLimit) msg += " Someone in the room needs a different temperature or airflow right now, so I worked within their limit.";
  else if (ownLimit) msg += " I kept within your own comfort range for today.";
  return msg;
}

export function startArbiter(): void {
  startWorker("arbiter", async (t) => (t.kind === "arbitrate" ? arbitrate(t) : null));
}
