/**
 * Graph tools the agents (and any MCP client) can call. Each one wraps parameterised Cypher in
 * graph/queries/*.cypher. Read tools run read-only under the caller's ACL user; the few write
 * tools go through the typed write API. Tools are scoped to ONE person (`alias`): a person's
 * agent physically cannot query another person's graph.
 */
import { z } from "zod";
import { bodyRead, bodyAlgo, homeRead } from "../graph/graphs.js";
import { now, run, agentUser } from "../graph/db.js";
import { bodyGraph } from "../config.js";
import { embed } from "../llm/azure.js";
import * as w from "../writeapi/index.js";
import { present, KEYSTONE_LABELS, KEYSTONE_RELS } from "../graph/schemaInfo.js";

export interface Tool {
  name: string;
  description: string;
  schema: z.ZodObject<any>;
  write?: boolean;
  run: (alias: string, args: any) => Promise<unknown>;
}

const HOUR = 3600_000;

async function latestEpisode(alias: string, kind?: string, quality?: string): Promise<string | null> {
  const where = [kind ? `ep.kind = $kind` : null, quality ? `ep.quality = $quality` : null].filter(Boolean).join(" AND ");
  const rows = await run(
    bodyGraph(alias),
    `MATCH (ep:Episode) ${where ? `WHERE ${where}` : ""} RETURN ep.id AS id ORDER BY ep.start DESC LIMIT 1`,
    { kind, quality },
    { as: agentUser(alias), readOnly: true },
  );
  return (rows[0]?.id as string) ?? null;
}

export const tools: Tool[] = [
  {
    name: "get_context",
    description:
      "Everything HomeOS currently believes about this person and their room: active conditions (with IMPLIES expansion), effective comfort ranges and which conditions caused them, baselines, who is present, and device states.",
    schema: z.object({}),
    run: async (alias) => {
      const t = now();
      const [conditions, comfort, baselines, present, devices, events, sleep] = await Promise.all([
        bodyRead(alias, "active_conditions", { alias, now: t }),
        bodyRead(alias, "effective_comfort", { alias, now: t }),
        bodyRead(alias, "get_baselines", { alias, now: t }),
        homeRead("present_people", { room: w.DEFAULT_ROOM }),
        homeRead("devices", { now: t }),
        bodyRead(alias, "upcoming_events", { alias, now: t, limit: 5 }),
        bodyRead(alias, "recent_sleep", { alias, limit: 7 }),
      ]);
      return {
        person: alias,
        conditions,
        comfort,
        baselines,
        present: present.map((p) => p.alias),
        devices: devices.map((d) => ({ id: d.id, ...pick(d.state, ["kind", "power", "setpoint", "level", "brightness", "temperature", "humidity", "alarm_primary", "alarm_backup"]), leased_by: d.leased_by })),
        upcoming_events: events,
        recent_sleep: sleep,
      };
    },
  },
  {
    name: "feasible_settings",
    description:
      "Multi-hop arbitration view of the shared home graph: for each device and variable, the range every person present can accept (from their anonymous constraints), how many people bind it, and which body signals it touches.",
    schema: z.object({ room: z.string().default("bedroom") }),
    run: (_alias, a) => homeRead("feasible_settings", { room: a.room ?? "bedroom", now: now() }),
  },
  {
    name: "choose_intervention",
    description:
      "Rank interventions for an episode from this person's learned Evidence (matched on shared context bands, Thompson-sampled), minus things they dislike, boosted by active playbooks.",
    schema: z.object({ episode: z.string().optional().describe("episode id; defaults to the latest episode") }),
    run: async (alias, a) => {
      const episode = a.episode ?? (await latestEpisode(alias));
      if (!episode) return { error: "no episode yet" };
      return { episode, ranking: await bodyRead(alias, "choose_intervention", { episode, alias, now: now() }) };
    },
  },
  {
    name: "explain",
    description:
      "Explain a decision by walking its provenance in the graph: context bands, chosen intervention, evidence used (n, mean), rejected options and why, and the comfort constraints that bounded it. The owner sees their own private reasons; other people's constraints appear only as 'someone present'.",
    schema: z.object({ decision: z.string().optional().describe("decision id; defaults to the latest") }),
    run: async (alias, a) => {
      let decision = a.decision;
      if (!decision) {
        const r = await bodyRead(alias, "recent_decisions", { limit: 1 });
        decision = r[0]?.id;
      }
      if (!decision) return { error: "no decisions yet" };
      const [mine] = await bodyRead(alias, "explain", { decision });
      const shared = mine?.arbitration
        ? (await homeRead("explain_arbitration", { arbitration: mine.arbitration, viewer: alias }))[0] ?? null
        : null;
      return { decision, private: mine ?? null, shared };
    },
  },
  {
    name: "why_bad_night",
    description:
      "Most likely cause chains for a (bad) night using algo.SPpaths over -ln(p) weights: episode -> context band -> variable -> device or habit lever, with likelihoods.",
    schema: z.object({ episode: z.string().optional() }),
    run: async (alias, a) => {
      const episode = a.episode ?? (await latestEpisode(alias, "sleep", "bad")) ?? (await latestEpisode(alias, "sleep"));
      if (!episode) return { error: "no sleep episodes yet (sync the ring via Health Connect)" };
      return { episode, chains: await bodyAlgo(alias, "why_bad_night", { episode }) };
    },
  },
  {
    name: "drivers",
    description: "Top drivers of bad nights: PageRank over bands that co-occur on bad nights (Scientist output).",
    schema: z.object({ limit: z.number().int().default(5) }),
    run: (alias, a) => bodyAlgo(alias, "drivers", { limit: a.limit ?? 5 }),
  },
  {
    name: "keystone",
    description: "Keystone context: betweenness centrality of bands on the episode -> band -> lever graph.",
    schema: z.object({ limit: z.number().int().default(5) }),
    run: async (alias, a) => {
      const ks = await present(alias, KEYSTONE_LABELS, KEYSTONE_RELS);
      return ks.rels.length ? bodyAlgo(alias, "keystone", { limit: a.limit ?? 5, labels: ks.labels, rels: ks.rels }) : [];
    },
  },
  {
    name: "episode_types",
    description: "Types of episodes discovered by community detection (label propagation) over shared bands.",
    schema: z.object({}),
    write: true,
    run: (alias) => bodyAlgo(alias, "episode_types", {}, true),
  },
  {
    name: "similar_episodes",
    description: "Past episodes most like a given one: vector similarity on episode embeddings plus shared context bands.",
    schema: z.object({ episode: z.string().optional(), k: z.number().int().default(5) }),
    run: async (alias, a) => {
      const episode = a.episode ?? (await latestEpisode(alias));
      if (!episode) return [];
      return bodyRead(alias, "similar_episodes", { episode, k: (a.k ?? 5) + 1 });
    },
  },
  {
    name: "recall",
    description:
      "Search this person's long-term memory: past conversation turns (vector + full-text) and past episodes with what was done and how well it worked.",
    schema: z.object({ query: z.string(), k: z.number().int().default(5) }),
    run: async (alias, a) => {
      const k = a.k ?? 5;
      const vec = await embed(a.query);
      const words = String(a.query).replace(/[^\p{L}\p{N}\s]/gu, " ").split(/\s+/).filter((x) => x.length > 3).join(" | ");
      const [turns, episodes, keyword] = await Promise.all([
        vec ? bodyRead(alias, "recall_turns", { vec, k }) : Promise.resolve([]),
        vec ? bodyRead(alias, "recall_episodes", { vec, k }) : Promise.resolve([]),
        words ? bodyRead(alias, "recall_fulltext", { text: words, k }).catch(() => []) : Promise.resolve([]),
      ]);
      return { turns, episodes, keyword };
    },
  },
  {
    name: "what_works",
    description: "What has worked best for this person for a kind of episode (recovery, stress, too_hot, too_cold, unwell, sleep), pooled evidence with how much was learned from their own body.",
    schema: z.object({ kind: z.string(), limit: z.number().int().default(5) }),
    run: (alias, a) => bodyRead(alias, "what_works", { kind: a.kind, limit: a.limit ?? 5 }),
  },
  {
    name: "playbooks_for",
    description: "The active playbook (versioned procedural memory) for a condition, following IMPLIES (fever -> sick).",
    schema: z.object({ condition: z.string() }),
    run: (alias, a) => bodyRead(alias, "playbooks_for", { condition: a.condition }),
  },
  {
    name: "belief_diff",
    description: "What HomeOS learned or retired about this person in the last N hours (bi-temporal memory), e.g. 'what changed since last week?'",
    schema: z.object({ hours: z.number().default(168) }),
    run: (alias, a) => bodyRead(alias, "belief_diff", { alias, since: now() - (a.hours ?? 168) * HOUR, now: now() }),
  },
  {
    name: "impact",
    description: "BFS from a device: which variables and body signals a change reaches and who in the room would feel it.",
    schema: z.object({ device: z.string() }),
    run: (_alias, a) => homeRead("impact", { device: a.device }),
  },
  // ----- typed write tools (go through the write API; never free-form Cypher) -----
  {
    name: "set_condition",
    description:
      "Record that this person has a condition now (sick, fever, cold_symptoms, headache, post_workout, sleeping, stressed, menstrual, or a new kind). Stored privately; only anonymous comfort ranges are shared with the home.",
    schema: z.object({ kind: z.string(), hours: z.number().default(18), note: z.string().optional() }),
    write: true,
    run: (alias, a) => w.setCondition(alias, a.kind, { hours: a.hours, note: a.note, source: "voice" }),
  },
  {
    name: "clear_condition",
    description: "This person no longer has a condition.",
    schema: z.object({ kind: z.string() }),
    write: true,
    run: (alias, a) => w.clearCondition(alias, a.kind, "voice"),
  },
  {
    name: "set_preference",
    description: "Teach a lasting comfort preference, e.g. room_temp min 24 max 26 (vars: room_temp, airflow 0-3, humidity, lux 0-100, noise).",
    schema: z.object({ var: z.string(), min: z.number().nullable(), max: z.number().nullable() }),
    write: true,
    run: (alias, a) => w.setPreference(alias, a.var, a.min, a.max, "voice"),
  },
  {
    name: "set_dislike",
    description: "Teach a dislike of an intervention (ac_cooler, ac_warmer, fan_high, fan_low, fan_off, light_warm_dim, light_bright, pacer_5_0/5_5/6_0), optionally only while a condition holds.",
    schema: z.object({ intervention: z.string(), when_condition: z.string().nullable().default(null) }),
    write: true,
    run: (alias, a) => w.setDislike(alias, a.intervention, a.when_condition ?? null, "voice"),
  },
  {
    name: "give_feedback",
    description: "Record how the latest action felt: rating from -1 (worse) to 1 (better).",
    schema: z.object({ rating: z.number(), text: z.string() }),
    write: true,
    run: (alias, a) => w.addFeedback(alias, a.rating, a.text),
  },
  {
    name: "upcoming_events",
    description: "Calendar events still ahead for this person, soonest first. Empty means nothing is on the calendar in the graph.",
    schema: z.object({ limit: z.number().int().default(5) }),
    run: (alias, a) => bodyRead(alias, "upcoming_events", { alias, now: now(), limit: a.limit ?? 5 }),
  },
  {
    name: "recent_sleep",
    description: "This person's own sleep episodes, newest first, including quality.",
    schema: z.object({ limit: z.number().int().default(7) }),
    run: (alias, a) => bodyRead(alias, "recent_sleep", { alias, limit: a.limit ?? 7 }),
  },
  {
    name: "sleep_week",
    description: "How many nights in the last 7 days were bad, from this person's sleep episodes.",
    schema: z.object({}),
    run: (alias) => bodyRead(alias, "sleep_week", { alias, since: now() - 7 * 24 * HOUR }),
  },
  {
    name: "calendar_memory",
    description: "The calendar memory node and how many events are attached.",
    schema: z.object({}),
    run: (alias) => bodyRead(alias, "calendar_memory", { alias }),
  },
  {
    name: "goals",
    description: "Sleep and wake goals stored for this person.",
    schema: z.object({}),
    run: (alias) => bodyRead(alias, "goals", { alias }),
  },
  {
    name: "habits",
    description: "Habit levers this person tracks (bedtime, late caffeine, evening training).",
    schema: z.object({}),
    run: (alias) => bodyRead(alias, "habits", { alias }),
  },
  {
    name: "sources",
    description: "Where this person's facts come from: ring, watch, calendar.",
    schema: z.object({}),
    run: (alias) => bodyRead(alias, "sources", { alias }),
  },
  {
    name: "household_role",
    description: "This person's private household role. It is never copied to the home graph.",
    schema: z.object({}),
    run: (alias) => bodyRead(alias, "household_role", { alias }),
  },
  {
    name: "open_episodes",
    description: "Episodes that are still open.",
    schema: z.object({ limit: z.number().int().default(10) }),
    run: (alias, a) => bodyRead(alias, "open_episodes", { alias, limit: a.limit ?? 10 }),
  },
  {
    name: "condition_chain",
    description: "Active conditions and what they imply, up to three hops.",
    schema: z.object({}),
    run: (alias) => bodyRead(alias, "condition_chain", { alias, now: now() }),
  },
  {
    name: "intervention_catalog",
    description: "Every intervention in this person's graph and the variable it moves.",
    schema: z.object({}),
    run: (alias) => bodyRead(alias, "intervention_catalog", {}),
  },
  {
    name: "playbook_history",
    description: "Playbook versions and which version each one superseded.",
    schema: z.object({}),
    run: (alias) => bodyRead(alias, "playbook_history", {}),
  },
  {
    name: "body_counts",
    description: "Node and edge counts of this person's private graph.",
    schema: z.object({}),
    run: (alias) => bodyRead(alias, "body_counts", {}),
  },
  {
    name: "routines",
    description: "Time-of-day routines that apply to a room in the shared home graph.",
    schema: z.object({ room: z.string().default("bedroom") }),
    run: (_alias, a) => homeRead("routines", { room: a.room ?? "bedroom" }),
  },
  {
    name: "scenes",
    description: "Named scenes staged in a room, and the devices each scene uses.",
    schema: z.object({ room: z.string().default("bedroom") }),
    run: (_alias, a) => homeRead("scenes", { room: a.room ?? "bedroom" }),
  },
  {
    name: "policies",
    description: "House policies governing a room (quiet hours, warm floor).",
    schema: z.object({ room: z.string().default("bedroom") }),
    run: (_alias, a) => homeRead("policies", { room: a.room ?? "bedroom" }),
  },
  {
    name: "zones",
    description: "Zones and the rooms they contain.",
    schema: z.object({}),
    run: () => homeRead("zones", {}),
  },
  {
    name: "clock_state",
    description: "Bedroom clock power and the two alarms, if a morning decision has set them.",
    schema: z.object({}),
    run: () => homeRead("clock_state", {}),
  },
  {
    name: "handoff_chain",
    description: "Recent task handoffs between agents on the shared home graph.",
    schema: z.object({ hours: z.number().default(24), limit: z.number().int().default(20) }),
    run: (_alias, a) => homeRead("handoff_chain", { since: now() - (a.hours ?? 24) * HOUR, limit: a.limit ?? 20 }),
  },
  {
    name: "lease_holders",
    description: "Devices currently leased, and which agent holds each lease.",
    schema: z.object({}),
    run: () => homeRead("lease_holders", { now: now() }),
  },
  {
    name: "home_counts",
    description: "Node and edge counts of the shared home graph.",
    schema: z.object({}),
    run: () => homeRead("home_counts", {}),
  },
];

export const toolByName = new Map(tools.map((t) => [t.name, t]));

function pick(o: Record<string, unknown> | undefined, keys: string[]) {
  const out: Record<string, unknown> = {};
  for (const k of keys) if (o && o[k] !== undefined) out[k] = o[k];
  return out;
}
