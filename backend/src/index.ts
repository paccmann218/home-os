/**
 * HomeOS backend: HTTP + WebSocket gateway for the watch app, phone app and dashboard, plus the
 * agent runtime (guardians, planners, arbiter, evaluator) that coordinates through FalkorDB.
 */
import express from "express";
import http from "node:http";
import path from "node:path";
import fs from "node:fs";
import { WebSocketServer, type WebSocket } from "ws";
import { config, REPO_ROOT, homeGraph, bodyGraph } from "./config.js";
import { now, run, agentUser, HOME_READER } from "./graph/db.js";
import { bodyRead, homeRead, bodyAlgo } from "./graph/graphs.js";
import { setupAll } from "./graph/setup.js";
import * as w from "./writeapi/index.js";
import { bus, type FeedEvent } from "./bus.js";
import { register, connected } from "./channels.js";
import { pushHr, setOnBody, snapshot } from "./ingest/buffer.js";
import { ingestHealth } from "./ingest/health.js";
import { cachedAudio, speak, transcribe, voiceEnabled } from "./voice/sarvam.js";
import { startRoomPolling, memoryState } from "./devices/index.js";
import { handleChip, answerPrompt, startGuardian } from "./agents/guardian.js";
import { startPlanner } from "./agents/planner.js";
import { startArbiter } from "./agents/arbiter.js";
import { startEvaluator } from "./agents/evaluator.js";
import { startReclaimer } from "./agents/worker.js";
import { runScientist } from "./agents/scientist.js";
import { ask } from "./agents/coach.js";
import { toolByName } from "./tools/index.js";
import { record } from "./replay/recorder.js";
import { toolbox } from "./llm/agent.js";

const app = express();
app.use(express.json({ limit: "25mb" }));
app.use(express.raw({ type: ["audio/*", "application/octet-stream"], limit: "25mb" }));
app.use((_, res, next) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  next();
});

const feed: FeedEvent[] = [];
bus.on("feed", (e: FeedEvent) => {
  feed.push(e);
  if (feed.length > 300) feed.shift();
  console.log(`[${new Date(e.at).toLocaleTimeString()}] ${e.agent}${e.person ? ` (${e.person})` : ""}: ${e.text}`);
});

/** Record a real input for replay mode; requests marked as tests (x-homeos-test) are never recorded. */
const rec = (req: express.Request, kind: string, body: unknown) => {
  // Tests and dataset imports (x-homeos-import; the converted dataset lives in replay/datasets) are not session recordings.
  if (!req.headers["x-homeos-test"] && !req.headers["x-homeos-import"]) record(kind, String(req.params.alias), body);
};

const person = (req: express.Request) => {
  const a = String(req.params.alias);
  if (!config.people.includes(a)) throw Object.assign(new Error(`unknown person ${a}`), { status: 404 });
  return a;
};
const wrap =
  (fn: (req: express.Request, res: express.Response) => Promise<unknown>) => async (req: express.Request, res: express.Response) => {
    try {
      const out = await fn(req, res);
      if (!res.headersSent) res.json(out ?? { ok: true });
    } catch (e: any) {
      res.status(e.status ?? 500).json({ error: e.message });
    }
  };

app.get("/api/ping", (_req, res) => res.json({ ok: true, graph: config.graphEnabled, voice: voiceEnabled() }));

app.get(
  "/api/state",
  wrap(async () => {
    const t = now();
    const [devices, present] = config.graphEnabled
      ? await Promise.all([homeRead("devices", { now: t }), homeRead("present_people", { room: w.DEFAULT_ROOM })])
      : [[...memoryState].map(([id, state]) => ({ id, state, leased_by: null })), config.people.map((alias) => ({ alias }))];
    return {
      graphEnabled: config.graphEnabled,
      voice: voiceEnabled(),
      people: config.people.map((p) => ({
        alias: p,
        devices: connected(p),
        present: present.some((x) => x.alias === p),
        dataset: config.datasetPeople.includes(p) ? "PMData (Thambawita et al., 2020), CC BY 4.0" : null,
      })),
      devices,
      live: snapshot(),
      feed: feed.slice(-80),
    };
  }),
);

app.post("/api/chip/:alias", wrap(async (req) => (rec(req, "chip", req.body), handleChip(person(req), String(req.body.chip), String(req.body.source ?? "app")))));
app.post("/api/ask/:alias", wrap(async (req) => (rec(req, "ask", req.body), ask(person(req), String(req.body.text), { lang: req.body.lang, channel: req.body.channel ?? "app", speak: req.body.speak !== false }))));
app.post("/api/prompt/:alias", wrap(async (req) => (rec(req, "prompt", req.body), answerPrompt(person(req), String(req.body.id), String(req.body.answer)))));
app.post("/api/presence/:alias", wrap(async (req) => w.setPresence(person(req), Boolean(req.body.present))));
app.post("/api/hr/:alias", wrap(async (req) => (rec(req, "hr", req.body), pushHr(person(req), { t: req.body.t ?? now(), hr: Number(req.body.hr), ibi: req.body.ibi }))));
app.post("/api/health/:alias", wrap(async (req) => (rec(req, "health", req.body), ingestHealth(person(req), req.body))));
app.post("/api/scientist/:alias", wrap(async (req) => runScientist(person(req))));
app.post(
  "/api/audio/:alias",
  wrap(async (req) => {
    const alias = person(req);
    const buf = Buffer.isBuffer(req.body) ? req.body : Buffer.from(String(req.body.wav_base64 ?? ""), "base64");
    const stt = await transcribe(buf, req.headers["content-type"]?.startsWith("audio/") ? req.headers["content-type"] : "audio/wav");
    if (!stt.text) return { heard: "", reply: "" };
    rec(req, "ask", { text: stt.text, lang: stt.lang, channel: "voice" });
    const r = await ask(alias, stt.text, { lang: stt.lang, channel: "watch-voice" });
    return { heard: stt.text, ...r };
  }),
);

app.get("/api/tts/:file", (req, res) => {
  const audio = cachedAudio(String(req.params.file).replace(/\.wav$/, ""));
  if (!audio) return res.status(404).end();
  res.setHeader("Content-Type", "audio/wav");
  res.end(audio);
});

app.get(
  "/api/tool/:alias/:tool",
  wrap(async (req) => {
    const t = toolByName.get(String(req.params.tool));
    if (!t || t.write) throw Object.assign(new Error("unknown read tool"), { status: 404 });
    const args: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(req.query)) args[k] = /^\d+(\.\d+)?$/.test(String(v)) ? Number(v) : v;
    return t.run(person(req), t.schema.parse(args));
  }),
);

app.get(
  "/api/decisions/:alias",
  wrap(async (req) => bodyRead(person(req), "recent_decisions", { limit: Number(req.query.limit ?? 10) })),
);

app.get(
  "/api/provenance/:alias/:decision",
  wrap(async (req) => {
    const rows = await bodyRead(person(req), "explain_paths", { decision: String(req.params.decision) });
    return toGraph(rows.map((r) => r.p));
  }),
);

app.get(
  "/api/board",
  wrap(async (req) => homeRead("board", { since: now() - Number(req.query.minutes ?? 60) * 60_000, limit: 40 })),
);

/** Graph snapshot for the dashboard: a person's private graph (as them) or the shared home graph. */
app.get(
  "/api/graph/:which",
  wrap(async (req) => {
    const which = String(req.params.which);
    const limit = Number(req.query.limit ?? 400);
    const query = `MATCH p=(a)-[r]->(b) WHERE NOT a:Evidence AND NOT b:Evidence AND NOT type(r) IN ['CLAIMED_BY'] RETURN p ORDER BY coalesce(a.start, a.at, a.created, 0) DESC LIMIT ${limit}`;
    if (which === "home") return toGraph((await run(homeGraph(), query, {}, { as: HOME_READER, readOnly: true })).map((r) => r.p));
    if (!config.people.includes(which)) throw Object.assign(new Error("unknown graph"), { status: 404 });
    return toGraph((await run(bodyGraph(which), query, {}, { as: agentUser(which), readOnly: true })).map((r) => r.p));
  }),
);

app.get(
  "/api/evidence/:alias/:kind",
  wrap(async (req) => bodyRead(person(req), "what_works", { kind: String(req.params.kind), limit: 8 })),
);

app.get(
  "/api/stats",
  wrap(async () => {
    const graphs: Record<string, unknown> = {};
    for (const g of [homeGraph(), ...config.people.map(bodyGraph)]) {
      const [n] = await run(g, "MATCH (n) RETURN count(n) AS nodes");
      const [e] = await run(g, "MATCH ()-[r]->() RETURN count(r) AS edges");
      graphs[g] = { nodes: n?.nodes ?? 0, edges: e?.edges ?? 0 };
    }
    return graphs;
  }),
);

app.post(
  "/api/reset",
  wrap(async () => {
    await setupAll({ reset: true });
    for (const p of config.people) await w.setPresence(p, !config.datasetPeople.includes(p));
    await loadDatasetPeople();
    return { reset: true };
  }),
);

function toGraph(paths: any[]) {
  const nodes = new Map<number, any>();
  const edges = new Map<number, any>();
  for (const p of paths) {
    if (!p) continue;
    for (const n of p.nodes ?? []) {
      const props = { ...n.properties };
      delete props.embedding;
      nodes.set(n.id, { id: n.id, label: n.labels?.[0], props });
    }
    for (const e of p.edges ?? []) edges.set(e.id, { id: e.id, source: e.sourceId, target: e.destinationId, type: e.relationshipType, props: e.properties });
  }
  return { nodes: [...nodes.values()], edges: [...edges.values()] };
}

// ---------- dashboard (static) ----------
const uiDist = path.join(REPO_ROOT, "ui", "dist");
if (fs.existsSync(uiDist)) app.use(express.static(uiDist));

// ---------- WebSockets ----------
const server = http.createServer(app);
const deviceWss = new WebSocketServer({ noServer: true });
const uiWss = new WebSocketServer({ noServer: true });

server.on("upgrade", (req, socket, head) => {
  const url = new URL(req.url ?? "/", "http://x");
  if (url.pathname === "/ws/device") deviceWss.handleUpgrade(req, socket, head, (ws) => deviceWss.emit("connection", ws, req));
  else if (url.pathname === "/ws/ui") uiWss.handleUpgrade(req, socket, head, (ws) => uiWss.emit("connection", ws, req));
  else socket.destroy();
});

deviceWss.on("connection", (ws: WebSocket, req: http.IncomingMessage) => {
  const url = new URL(req.url ?? "/", "http://x");
  const alias = url.searchParams.get("alias") ?? "";
  const kind = (url.searchParams.get("kind") ?? "watch") as "watch" | "phone";
  if (!config.people.includes(alias)) return ws.close(4004, "unknown person");
  register(alias, ws, kind);
  bus.feed({ agent: "home", kind: "connect", person: alias, text: `${alias}'s ${kind} connected` });
  ws.send(JSON.stringify({ type: "hello", alias, voice: voiceEnabled(), graph: config.graphEnabled }));
  ws.on("message", async (raw) => {
    let m: any;
    try {
      m = JSON.parse(String(raw));
    } catch {
      return;
    }
    try {
      const clip = m.type === "audio" || m.type === "demo_hear";
      record(clip ? "audio_ws" : m.type, alias, clip ? { bytes: String(m.wav_base64 ?? "").length } : m);
      if (m.type === "hr") pushHr(alias, { t: m.t ?? now(), hr: Number(m.hr), ibi: m.ibi });
      else if (m.type === "onbody") {
        setOnBody(alias, Boolean(m.on));
        await w.setPresence(alias, Boolean(m.on));
      } else if (m.type === "chip") await handleChip(alias, String(m.chip), kind);
      else if (m.type === "ask") await ask(alias, String(m.text), { lang: m.lang, channel: kind });
      else if (m.type === "prompt") await answerPrompt(alias, String(m.id), String(m.answer));
      else if (m.type === "audio") {
        const stt = await transcribe(Buffer.from(String(m.wav_base64), "base64"));
        ws.send(JSON.stringify({ type: "heard", text: stt.text }));
        if (stt.text) record("ask", alias, { text: stt.text, lang: stt.lang, channel: "voice" });
        if (stt.text) await ask(alias, stt.text, { lang: stt.lang, channel: `${kind}-voice` });
      } else if (m.type === "demo_say") {
        // Hardcoded watch demo: Sarvam TTS only. Does not enter the agent loop.
        const id = String(m.id ?? "");
        const text = String(m.text ?? "").slice(0, 1400);
        const lang = String(m.lang || "hi-IN");
        bus.feed({ agent: `coach:${alias}`, kind: "say", person: alias, text });
        const audio = await speak(text, lang).catch((err) => {
          console.warn("[demo_say]", (err as Error).message);
          return null;
        });
        ws.send(JSON.stringify({ type: "demo_audio", id, text, audio_url: audio?.url ?? null, lang: audio?.lang ?? lang }));
      } else if (m.type === "demo_hear") {
        // Hardcoded watch demo: Sarvam STT only. The watch decides what "हाँ" means.
        const id = String(m.id ?? "");
        try {
          const sttDemo = await transcribe(Buffer.from(String(m.wav_base64), "base64"));
          ws.send(JSON.stringify({ type: "demo_heard", id, text: sttDemo.text, lang: sttDemo.lang }));
        } catch (err) {
          console.warn("[demo_hear]", (err as Error).message);
          ws.send(JSON.stringify({ type: "demo_heard", id, text: "" }));
        }
      }
    } catch (e) {
      ws.send(JSON.stringify({ type: "error", error: (e as Error).message }));
    }
  });
  ws.on("close", () => bus.feed({ agent: "home", kind: "disconnect", person: alias, text: `${alias}'s ${kind} disconnected` }));
});

uiWss.on("connection", (ws: WebSocket) => {
  const send = (type: string) => (data: unknown) => ws.readyState === ws.OPEN && ws.send(JSON.stringify({ type, data }));
  const handlers: [string, (d: unknown) => void][] = [
    ["feed", send("feed")],
    ["signal", send("signal")],
    ["room", send("room")],
    ["device", send("device")],
    ["decision", send("decision")],
    ["learned", send("learned")],
  ];
  for (const [ev, h] of handlers) bus.on(ev, h);
  ws.on("close", () => handlers.forEach(([ev, h]) => bus.off(ev, h)));
});

/** Dataset people (e.g. PMData p01) get their committed history on first start, so a fresh
 *  `docker compose up` already has months of real nights for the Scientist. */
async function loadDatasetPeople() {
  for (const alias of config.datasetPeople) {
    const file = path.join(REPO_ROOT, "replay", "datasets", `pmdata-${alias}.json`);
    if (!fs.existsSync(file)) continue;
    const [row] = await run(bodyGraph(alias), "MATCH (e:Episode) RETURN count(e) AS n", {}, { as: agentUser(alias), readOnly: true });
    if ((row?.n ?? 0) > 0) continue;
    const r = await ingestHealth(alias, JSON.parse(fs.readFileSync(file, "utf8")));
    console.log(`[dataset] loaded ${alias}: ${r.newNights} nights from ${path.basename(file)}`);
    await runScientist(alias).catch((e) => console.warn(`[dataset] scientist ${alias}:`, (e as Error).message));
  }
}

// ---------- start ----------
process.on("unhandledRejection", (e) => console.warn("[unhandled]", (e as Error)?.message ?? e));
process.on("uncaughtException", (e) => console.warn("[uncaught]", e.message));
async function main() {
  if (config.graphEnabled) {
    await setupAll({ reset: process.argv.includes("--reset") });
    for (const p of config.people) await w.setPresence(p, !config.datasetPeople.includes(p));
    await loadDatasetPeople();
    startReclaimer();
    startArbiter();
    startEvaluator();
    for (const p of config.people) {
      startPlanner(p);
      startGuardian(p);
    }
  }
  startRoomPolling();
  // Start each person's MCP toolbox now so the first question isn't slowed by process start-up.
  if (config.graphEnabled) for (const p of config.people) void toolbox(p).catch((e) => console.warn(`[mcp:${p}]`, (e as Error).message));
  server.listen(config.server.port, "0.0.0.0", () => {
    console.log(`HomeOS backend on http://0.0.0.0:${config.server.port}  graph=${config.graphEnabled ? "on" : "OFF (ablation)"}  voice=${voiceEnabled() ? "sarvam" : "device TTS"}`);
    console.log(`  watch/phone WS: ws://<laptop-ip>:${config.server.port}/ws/device?alias=<${config.people.join("|")}>&kind=watch`);
  });
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
