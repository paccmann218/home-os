/**
 * Coach (one per person, LLM voice agent on the watch). Answers "why?", "what works for me?",
 * "what changed?" and takes spoken instructions ("I have a fever", "I hate the fan when I'm sick").
 * Every answer is grounded in graph tools chosen through MCP; every turn is stored as
 * Session -HAS_TURN-> Turn -ABOUT-> (episode | decision | condition | intervention) so it survives
 * restarts and can be recalled later (vector + full-text).
 */
import { config } from "../config.js";
import { bodyRead } from "../graph/graphs.js";
import * as w from "../writeapi/index.js";
import { runAgent } from "../llm/agent.js";
import { llmAvailable } from "../llm/azure.js";
import { bus } from "../bus.js";
import { say } from "../channels.js";
import { CHIPS, handleChip } from "./guardian.js";

const sessions = new Map<string, { id: string; last: number }>();

function sessionFor(alias: string): string {
  const s = sessions.get(alias);
  if (s && Date.now() - s.last < 30 * 60_000) {
    s.last = Date.now();
    return s.id;
  }
  const id = `ses-${alias}-${Date.now().toString(36)}`;
  sessions.set(alias, { id, last: Date.now() });
  return id;
}

const SYSTEM = (alias: string) => `You are HomeOS, the home's assistant, talking to ${alias} through their smartwatch.
You answer from the graph: ALWAYS call tools before answering about their body, home, past, or why something happened.
- "why ..." -> explain (latest decision unless they name one). Cite the evidence (n, mean) and constraints briefly.
- "what works / what helps" -> what_works and/or playbooks_for; "last time / remember" -> recall.
- "what changed / what do you know about me" -> belief_diff or get_context.
- "why did I sleep badly" -> why_bad_night (and drivers if useful).
- If they report a symptom or state (fever, sick, headache, stressed, too hot/cold, done working out), call set_condition with the right kind.
- If they state a lasting preference or dislike, call set_preference / set_dislike.
- For any other question about the shared home (devices, rooms, who is present, past arbitrations, tasks), use the official FalkorDB tools: call falkordb__get_graph_schema on graph "home:flat1" first, then falkordb__query_graph_readonly with Cypher that matches that schema.
Privacy: other people's limits appear as "someone present" - never guess who or why. Never reveal another person's data.
Wellness only, not medical advice. Your reply is SPOKEN on a watch: at most 2 short sentences and 30 words, no markdown, no lists, no numbers they didn't ask for. Match their language (English, Hindi or Hinglish).`;

function aboutRefs(trace: { tool: string; result: string }[]): string[] {
  const refs = new Set<string>();
  for (const t of trace) {
    for (const m of t.result.matchAll(/"(dec-[^"]+|ep-[^"]+)"/g)) refs.add(m[1]);
    for (const m of t.result.matchAll(/"intervention":\s*"([a-z_0-9]+)"/g)) refs.add(m[1]);
    for (const m of t.result.matchAll(/"kind":\s*"(sick|fever|cold_symptoms|headache|post_workout|sleeping|stressed|menstrual)"/g)) refs.add(m[1]);
  }
  return [...refs].slice(0, 12);
}

export async function ask(alias: string, text: string, opts: { lang?: string; channel?: string; speak?: boolean } = {}) {
  const session = sessionFor(alias);
  bus.feed({ agent: `coach:${alias}`, kind: "heard", person: alias, text: `“${text}”` });
  if (!config.graphEnabled) {
    const reply = "I don't have any memory of you or your home, so I can't say.";
    if (opts.speak !== false) await say(alias, reply);
    return { reply, tools: [] };
  }
  // Short chip-like utterances go straight to the guardian (fast path, no LLM).
  const chip = Object.keys(CHIPS).find((k) => text.trim().toLowerCase().replace(/[^a-z_ ]/g, "") === k.replace("_", " "));
  if (chip) {
    await handleChip(alias, chip, opts.channel ?? "voice");
    return { reply: "", tools: [] };
  }
  // Memory writes (with embeddings) run alongside the LLM instead of in front of it.
  const history = (await bodyRead(alias, "recent_turns", { limit: 6 })).reverse();
  const userTurn = w.addTurn(alias, { session, role: "user", text, lang: opts.lang, channel: opts.channel });
  let reply = "Sorry, I can't reach my reasoning service right now.";
  let trace: { tool: string; args: unknown; result: string }[] = [];
  if (llmAvailable()) {
    const r = await runAgent({
      alias,
      system: SYSTEM(alias),
      maxSteps: 6,
      messages: [
        ...history.map((h) => ({ role: (h.role === "user" ? "user" : "assistant") as "user" | "assistant", content: String(h.text) })),
        { role: "user", content: text },
      ],
      onTool: (t) => bus.feed({ agent: `coach:${alias}`, kind: "tool", person: alias, text: `MCP tool → ${t.tool}`, data: { args: t.args } }),
    });
    reply = r.text.trim() || "I couldn't find that in my memory yet.";
    trace = r.trace;
    // A condition set by voice should also act on the room, like a chip does.
    const cond = trace.find((t) => t.tool === "set_condition");
    if (cond) {
      const kind = (cond.args as any)?.kind as string;
      const chipKey = Object.entries(CHIPS).find(([, v]) => v.condition === kind)?.[0];
      const spec = chipKey ? CHIPS[chipKey] : null;
      if (spec?.episode) {
        const { raiseEpisode } = await import("./guardian.js");
        void raiseEpisode(alias, spec.episode, spec.summary, "voice:" + kind);
      }
    }
  }
  // Speak first; then persist the answer as conversation memory.
  if (opts.speak !== false) await say(alias, reply, { lang: opts.lang });
  await userTurn;
  await w.addTurn(alias, { session, role: "assistant", text: reply, lang: opts.lang, channel: opts.channel, about: aboutRefs(trace) });
  return { reply, tools: trace.map((t) => t.tool) };
}
