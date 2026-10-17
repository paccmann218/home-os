/**
 * The write API: the ONLY code that writes to FalkorDB. Agents (and the LLM, through typed MCP
 * tools) call these functions; nothing ever sends free-form write Cypher. Body writes run as the
 * person's ACL user, so even a bug here cannot write into someone else's graph.
 */
import { bodyRead, bodyWrite, bodyAlgo, homeRead, homeWrite } from "../graph/graphs.js";
import { now } from "../graph/db.js";
import { FOREVER } from "../graph/constants.js";
import { bus, newId } from "../bus.js";
import { embed } from "../llm/azure.js";
import { HABIT_VARS, DEFAULT_LEVELS, type BandSpec } from "../ingest/bands.js";

const HOUR = 3600_000;
export const DEFAULT_ROOM = "bedroom";

// ---------- conditions, preferences, privacy boundary ----------

export async function setCondition(
  alias: string,
  kind: string,
  opts: { hours?: number; source?: string; confidence?: number; note?: string; label?: string; room?: string } = {},
) {
  const t = now();
  const until = t + (opts.hours ?? 18) * HOUR;
  await bodyWrite(alias, "set_condition", {
    alias,
    kind,
    label: opts.label ?? kind.replace(/_/g, " "),
    now: t,
    until,
    source: opts.source ?? "user",
    confidence: opts.confidence ?? 1.0,
    note: opts.note ?? null,
  });
  bus.feed({ agent: `guardian:${alias}`, kind: "condition", person: alias, text: `Condition "${kind}" recorded (private)`, data: { kind, until } });
  const exported = await refreshConstraints(alias, opts.room ?? DEFAULT_ROOM);
  return { kind, until, exported };
}

export async function clearCondition(alias: string, kind: string, source = "user", room = DEFAULT_ROOM) {
  const [r] = await bodyWrite(alias, "clear_condition", { alias, kind, now: now(), source });
  bus.feed({ agent: `guardian:${alias}`, kind: "condition", person: alias, text: `Condition "${kind}" ended` });
  await refreshConstraints(alias, room);
  return { ended: r?.ended ?? 0 };
}

export async function setPreference(alias: string, v: string, min: number | null, max: number | null, source = "user", room = DEFAULT_ROOM) {
  const [r] = await bodyWrite(alias, "set_preference", { alias, var: v, min, max, now: now(), forever: FOREVER, source });
  bus.feed({ agent: `coach:${alias}`, kind: "preference", person: alias, text: `Learned preference ${v} ${min ?? "…"}–${max ?? "…"}` });
  await refreshConstraints(alias, room);
  return r;
}

export async function setDislike(alias: string, intervention: string, when: string | null, source = "user") {
  const [r] = await bodyWrite(alias, "set_dislike", { alias, intervention, when, now: now(), forever: FOREVER, source });
  bus.feed({ agent: `coach:${alias}`, kind: "preference", person: alias, text: `Learned: dislikes ${intervention}${when ? ` when ${when}` : ""}` });
  return r;
}

/**
 * Privacy boundary. Compute this person's effective comfort ranges in their private graph,
 * remember the reasons privately (ConstraintExport -DERIVED_FROM-> Condition), and publish
 * only anonymous var/min/max Constraints into the shared home graph.
 */
export async function refreshConstraints(alias: string, room = DEFAULT_ROOM) {
  const t = now();
  const comfort = await bodyRead(alias, "effective_comfort", { alias, now: t });
  const exports = comfort
    .filter((c) => c.min != null || c.max != null)
    .map((c) => ({
      id: newId(`c-${alias}-${c.var}`),
      var: c.var,
      min: c.min,
      max: c.max,
      priority: c.priority ?? 0,
      reasons: [...new Set([...(c.conditions ?? []), ...(c.via ?? [])])].filter(Boolean),
    }));
  if (exports.length === 0) return [];
  await bodyWrite(alias, "record_export", { exports, now: t });
  await homeWrite("publish_constraints", {
    alias,
    room,
    now: t,
    until: t + 24 * HOUR,
    exports: exports.map(({ id, var: v, min, max, priority }) => ({ id, var: v, min, max, priority })),
  });
  bus.feed({
    agent: `guardian:${alias}`,
    kind: "constraints",
    person: alias,
    text: `Shared ${exports.length} anonymous comfort ranges with the home (reasons stay private)`,
    data: exports.map(({ var: v, min, max }) => ({ var: v, min, max })),
  });
  return exports;
}

export async function setPresence(alias: string, present: boolean, room = DEFAULT_ROOM) {
  await homeWrite("set_presence", { alias, room, present, now: now() });
  bus.feed({ agent: "home", kind: "presence", person: alias, text: `${alias} ${present ? "is in" : "left"} the ${room}` });
}

// ---------- episodes, decisions, actions, learning ----------

export async function openEpisode(
  alias: string,
  e: { kind: string; bands: BandSpec[]; summary: string; trigger: string; room?: string; start?: number; id?: string },
) {
  const id = e.id ?? newId(`ep-${alias}`);
  await bodyWrite(alias, "create_episode", {
    alias,
    id,
    kind: e.kind,
    start: e.start ?? now(),
    summary: e.summary,
    trigger: e.trigger,
    room: e.room ?? DEFAULT_ROOM,
    bands: e.bands,
  });
  await bodyWrite(alias, "link_bands", { habits: HABIT_VARS });
  await bodyWrite(alias, "classify_bands", { defaults: DEFAULT_LEVELS });
  void embed(`${e.kind}: ${e.summary}. Context: ${e.bands.map((b) => b.key).join(", ")}`).then((vec) =>
    vec ? bodyWrite(alias, "set_episode_embedding", { id, vec }).catch(() => undefined) : undefined,
  );
  return id;
}

export async function linkEvent(alias: string, episode: string, ev: { id: string; kind: string; at: number; source: string }) {
  await bodyWrite(alias, "link_event", {
    episode,
    event_id: ev.id,
    kind: ev.kind,
    at: ev.at,
    source: ev.source,
    gap_min: Math.round((now() - ev.at) / 60000),
  });
}

export async function closeEpisode(alias: string, id: string, effect: number | null, quality: string | null = null) {
  await bodyWrite(alias, "close_episode", { id, now: now(), effect, quality });
}

export interface Considered {
  name: string;
  score: number;
  chosen: boolean;
  rejected_because: string | null;
}

export async function logDecision(
  alias: string,
  d: {
    id?: string;
    episode: string;
    by: string;
    rationale: string;
    considered: Considered[];
    arbitration?: string | null;
    respected?: { id: string; var: string; min: number | null; max: number | null; mine: boolean }[];
  },
) {
  const id = d.id ?? newId(`dec-${alias}`);
  await bodyWrite(alias, "log_decision", {
    id,
    episode: d.episode,
    by: d.by,
    rationale: d.rationale,
    considered: d.considered,
    arbitration: d.arbitration ?? null,
    now: now(),
  });
  if (d.respected?.length) await bodyWrite(alias, "log_respected", { decision: id, respected: d.respected });
  return id;
}

export async function logAction(alias: string, a: { decision: string; intervention: string; device: string | null; applied: unknown; evalDue: number }) {
  const id = newId(`act-${alias}`);
  await bodyWrite(alias, "log_action", {
    id,
    decision: a.decision,
    intervention: a.intervention,
    device: a.device,
    applied: JSON.stringify(a.applied ?? {}),
    eval_due: a.evalDue,
    now: now(),
  });
  return id;
}

export async function learn(
  alias: string,
  o: { action: string; effect: number; signal: string; d_hr?: number | null; d_rmssd?: number | null; recover_s?: number | null; method: string },
) {
  const [r] = await bodyWrite(alias, "learn", {
    action: o.action,
    effect: o.effect,
    signal: o.signal,
    d_hr: o.d_hr ?? null,
    d_rmssd: o.d_rmssd ?? null,
    recover_s: o.recover_s ?? null,
    method: o.method,
    now: now(),
  });
  return (r?.updated ?? []) as { band: string; n: number; mean: number }[];
}

export async function addFeedback(alias: string, rating: number, text: string, withinMin = 60) {
  const [r] = await bodyWrite(alias, "add_feedback", { rating, text, now: now(), since: now() - withinMin * 60000 });
  return r ?? null;
}

export async function setBaseline(alias: string, signal: string, mean: number, sd: number, n: number, source: string) {
  await bodyWrite(alias, "set_baseline", { alias, signal, mean, sd, n, source, now: now(), forever: FOREVER });
}

// ---------- conversation memory ----------

export async function addTurn(alias: string, t: { session: string; role: string; text: string; lang?: string; channel?: string; about?: string[] }) {
  const vec = await embed(t.text);
  await bodyWrite(alias, "add_turn", {
    session: t.session,
    role: t.role,
    text: t.text,
    lang: t.lang ?? "en-IN",
    channel: t.channel ?? "watch",
    about: t.about ?? [],
    vec,
    now: now(),
  });
}

// ---------- procedural memory / insights ----------

export async function publishPlaybook(alias: string, name: string, steps: string[], note: string) {
  const [r] = await bodyWrite(alias, "publish_playbook", { name, steps, note, now: now() });
  if (r) bus.feed({ agent: "scientist", kind: "playbook", person: alias, text: `Playbook ${name} → v${r.version}: ${note}` });
  return r;
}

export async function writeInsight(alias: string, algo: string, text: string, about: string | null, data: unknown) {
  await bodyAlgo(alias, "write_insight", { id: newId("ins"), algo, text, about, data: JSON.stringify(data), now: now() }, true);
  bus.feed({ agent: "scientist", kind: "insight", person: alias, text });
}

// ---------- coordination (home graph) ----------

export async function createTask(t: { agent: string; kind: string; payload: unknown; owner?: string; from?: string | null; dueAt?: number }) {
  const id = newId("task");
  await homeWrite("create_task", {
    id,
    agent: t.agent,
    kind: t.kind,
    payload: JSON.stringify(t.payload ?? {}),
    owner: t.owner ?? null,
    from: t.from ?? null,
    due_at: t.dueAt ?? now(),
    now: now(),
  });
  return id;
}

export async function claimTask(agent: string, worker: string, leaseMs = 30_000) {
  const [r] = await homeWrite("claim_task", { agent, worker, lease_ms: leaseMs, now: now() });
  if (!r) return null;
  return { id: r.id as string, kind: r.kind as string, owner: r.owner as string | null, payload: JSON.parse(r.payload ?? "{}") };
}

export async function completeTask(id: string, worker: string, result: unknown, status = "done") {
  await homeWrite("complete_task", { id, worker, status, result: JSON.stringify(result ?? {}), now: now() });
}

export async function reclaimTasks() {
  const [r] = await homeWrite("reclaim_tasks", { now: now() });
  return (r?.reclaimed as number) ?? 0;
}

export async function acquireDevice(device: string, agent: string, ttlMs = 15_000): Promise<boolean> {
  const rows = await homeWrite("acquire_device", { device, agent, ttl_ms: ttlMs, now: now() });
  return rows.length > 0;
}

export async function releaseDevice(device: string, agent: string) {
  await homeWrite("release_device", { device, agent });
}

export async function postIntent(i: {
  agent: string;
  room?: string;
  var: string | null;
  target: number | null;
  device: string | null;
  intervention: string;
  priority: number;
  episode: string;
  ttlMs?: number;
}) {
  const id = newId("intent");
  await homeWrite("post_intent", {
    id,
    agent: i.agent,
    room: i.room ?? DEFAULT_ROOM,
    var: i.var,
    target: i.target,
    device: i.device,
    intervention: i.intervention,
    priority: i.priority,
    episode: i.episode,
    now: now(),
    valid_to: now() + (i.ttlMs ?? 10 * 60_000),
  });
  return id;
}

export async function recordArbitration(a: {
  id?: string;
  room?: string;
  summary: string;
  intents: string[];
  constraints: string[];
  applied: { device: string; setting: string }[];
}) {
  const id = a.id ?? newId("arb");
  await homeWrite("record_arbitration", {
    id,
    room: a.room ?? DEFAULT_ROOM,
    summary: a.summary,
    intents: a.intents,
    constraints: a.constraints,
    applied: a.applied,
    now: now(),
  });
  return id;
}

export async function setDeviceState(device: string, state: Record<string, unknown>) {
  const [r] = await homeWrite("set_device_state", { device, state, now: now() });
  return r?.state;
}

export { bodyRead, homeRead };
