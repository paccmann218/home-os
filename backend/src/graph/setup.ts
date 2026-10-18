/**
 * Builds the HomeOS graphs from scratch (idempotent):
 *  - loads the HomeOS JS UDF library
 *  - creates per-agent ACL users (tenant isolation enforced by FalkorDB)
 *  - creates range / full-text / vector indexes and unique constraints
 *  - seeds home:<id> and one body:<alias> graph per person from graph/seed/catalog.json
 *
 * Run: npm run setup-graph            (keeps existing memory)
 *      npm run setup-graph -- --reset (wipes all HomeOS graphs first)
 */
import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { adminDb, rawCommand, run, closeAll, agentUser, HOME_READER, now } from "./db.js";
import { config, REPO_ROOT, homeGraph, bodyGraph } from "../config.js";

const SEED_DIR = path.join(REPO_ROOT, "graph", "seed");
import { FOREVER } from "./constants.js";
export { FOREVER };

export const catalog = JSON.parse(fs.readFileSync(path.join(SEED_DIR, "catalog.json"), "utf8"));

function seed(name: string): string {
  return fs
    .readFileSync(path.join(SEED_DIR, `${name}.cypher`), "utf8")
    .split("\n")
    .filter((l) => !l.trim().startsWith("//"))
    .join("\n");
}

async function tryRun(graph: string, query: string): Promise<void> {
  try {
    await run(graph, query);
  } catch (e: any) {
    if (!/already (indexed|exists)|exists/i.test(String(e?.message))) throw e;
  }
}

async function rangeIndex(graph: string, label: string, prop: string) {
  await tryRun(graph, `CREATE INDEX FOR (n:${label}) ON (n.${prop})`);
}

async function unique(graph: string, label: string, prop: string) {
  await rangeIndex(graph, label, prop);
  try {
    await rawCommand(["GRAPH.CONSTRAINT", "CREATE", graph, "UNIQUE", "NODE", label, "PROPERTIES", "1", prop]);
  } catch (e: any) {
    if (!/already exists/i.test(String(e?.message))) throw e;
  }
}

export async function loadUdfs(): Promise<void> {
  const db = await adminDb();
  const script = fs.readFileSync(path.join(REPO_ROOT, "graph", "udf", "homeos.js"), "utf8");
  await db.udfLoad("HomeOS", script, true);
}

export async function createAclUsers(): Promise<void> {
  const pw = config.falkor.agentPassword;
  const conn = ["+@connection", "+ping", "+info", "+client"];
  for (const alias of config.people) {
    // A person's agents may read/write ONLY that person's private graph.
    await rawCommand([
      "ACL", "SETUSER", agentUser(alias), "reset", "on", `>${pw}`,
      `~${bodyGraph(alias)}`,
      "+GRAPH.QUERY", "+GRAPH.RO_QUERY", "+GRAPH.EXPLAIN", "+GRAPH.PROFILE", ...conn,
    ]);
  }
  // Every agent can read (never write) the shared home graph through this user.
  await rawCommand([
    "ACL", "SETUSER", HOME_READER, "reset", "on", `>${pw}`,
    "~home:*", "+GRAPH.RO_QUERY", "+GRAPH.EXPLAIN", ...conn,
  ]);
  try {
    await rawCommand(["ACL", "SAVE"]);
  } catch {
    /* no aclfile configured: users still work until restart */
  }
}

async function homeSchema(g: string) {
  await unique(g, "Device", "id");
  await unique(g, "Person", "alias");
  await unique(g, "Agent", "id");
  await rangeIndex(g, "Room", "id");
  await rangeIndex(g, "Var", "name");
  await rangeIndex(g, "Signal", "name");
  await rangeIndex(g, "Task", "status");
  await rangeIndex(g, "Task", "id");
  await rangeIndex(g, "Intent", "id");
  await rangeIndex(g, "Constraint", "id");
  await rangeIndex(g, "Arbitration", "id");
}

async function bodySchema(g: string) {
  await unique(g, "Episode", "id");
  await unique(g, "Band", "key");
  await unique(g, "Intervention", "name");
  await unique(g, "Condition", "kind");
  await rangeIndex(g, "Episode", "start");
  await rangeIndex(g, "Episode", "kind");
  await rangeIndex(g, "Decision", "id");
  await rangeIndex(g, "Action", "id");
  await rangeIndex(g, "Evidence", "kind");
  await rangeIndex(g, "Session", "id");
  await rangeIndex(g, "Turn", "at");
  await rangeIndex(g, "Event", "at");
  await rangeIndex(g, "Var", "name");
  await rangeIndex(g, "Signal", "name");
  await rangeIndex(g, "Playbook", "name");
  await rangeIndex(g, "ConstraintExport", "id");
  const dim = config.azure.embedDim;
  await tryRun(g, `CREATE VECTOR INDEX FOR (e:Episode) ON (e.embedding) OPTIONS {dimension:${dim}, similarityFunction:'cosine'}`);
  await tryRun(g, `CREATE VECTOR INDEX FOR (t:Turn) ON (t.embedding) OPTIONS {dimension:${dim}, similarityFunction:'cosine'}`);
  await tryRun(g, `CALL db.idx.fulltext.createNodeIndex('Turn', 'text')`);
  await tryRun(g, `CALL db.idx.fulltext.createNodeIndex('Episode', 'summary')`);
}

export async function seedHome(): Promise<void> {
  const g = homeGraph();
  await homeSchema(g);
  await run(g, seed("home_rooms_devices"), { home: config.homeId, rooms: catalog.rooms, devices: catalog.devices });
  await run(g, seed("home_knowledge"), {
    vars: catalog.vars,
    signals: catalog.signals,
    controls: catalog.controls,
    affects: catalog.affects,
  });
  await run(g, seed("home_people_agents"), { people: config.people });
  await run(g, seed("home_routines"));
  await run(g, seed("home_scenes"));
  await run(g, seed("home_policies"));
  await run(g, seed("home_zones"));
  await run(g, seed("home_clock"));
}

export async function seedBody(alias: string): Promise<void> {
  const g = bodyGraph(alias);
  await bodySchema(g);
  const interventions = catalog.interventions.map((i: any) => ({ ...i, params_json: JSON.stringify(i.params) }));
  const controls = catalog.controls;
  await run(g, seed("body_catalog"), {
    alias,
    vars: catalog.vars,
    signals: catalog.signals,
    controls,
    interventions,
  });
  await run(g, seed("body_conditions"), { conditions: catalog.conditions });
  await run(g, seed("body_implies"), { conditions: catalog.conditions.map((c: any) => ({ kind: c.kind, implies: c.implies ?? [] })) });
  await run(g, seed("body_preferences"), { alias, preferences: catalog.preferences, now: now(), forever: FOREVER });
  await run(g, seed("body_evidence"), { priors: catalog.evidence_priors });
  await run(g, seed("body_playbooks"), { playbooks: catalog.playbooks, now: now() });
  await run(g, seed("body_calendar"), { alias });
  await run(g, seed("body_goals"), { alias });
  await run(g, seed("body_habits"), { alias });
  await run(g, seed("body_sources"), { alias });
  await run(g, seed("body_roles"), { alias });
}

export async function resetAll(): Promise<void> {
  const db = await adminDb();
  // Only this household's graphs (tests and the ablation run use their own home/people).
  const mine = new Set([homeGraph(), ...config.people.map(bodyGraph)]);
  for (const name of await db.list()) {
    if (mine.has(name)) await db.selectGraph(name).delete();
  }
}

export async function setupAll(opts: { reset?: boolean } = {}): Promise<void> {
  if (opts.reset) await resetAll();
  await loadUdfs();
  await createAclUsers();
  await seedHome();
  for (const alias of config.people) await seedBody(alias);
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  const reset = process.argv.includes("--reset");
  setupAll({ reset })
    .then(async () => {
      console.log(`HomeOS graphs ready: ${homeGraph()} + ${config.people.map(bodyGraph).join(", ")}${reset ? " (reset)" : ""}`);
      await closeAll();
    })
    .catch(async (e) => {
      console.error(e);
      await closeAll();
      process.exit(1);
    });
}
