/**
 * FalkorDB connections. Every role gets its own Redis ACL user, so isolation
 * between people is enforced by the database, not by application code:
 *
 *   default       -> the write API (the only component that writes the shared home graph)
 *   agent_<alias> -> read/write on body:<alias> only (a person's private memory)
 *   home_reader   -> GRAPH.RO_QUERY on home:* only (agents read shared state)
 */
import { FalkorDB } from "falkordb";
import fs from "node:fs";
import path from "node:path";
import { config, REPO_ROOT } from "../config.js";

export type Params = Record<string, unknown>;
export type Row = Record<string, any>;

const clients = new Map<string, Promise<FalkorDB>>();

function connect(username?: string): Promise<FalkorDB> {
  const key = username ?? "default";
  let c = clients.get(key);
  if (!c) {
    c = FalkorDB.connect({
      socket: { host: config.falkor.host, port: config.falkor.port },
      ...(username ? { username, password: config.falkor.agentPassword } : {}),
    }).then((db) => {
      // A FalkorDB restart must not kill the process: log, and let the client reconnect.
      db.on("error", (e: Error) => console.warn(`[falkordb:${key}] ${e.message}`));
      return db;
    });
    c.catch(() => clients.delete(key));
    clients.set(key, c);
  }
  return c;
}

export const adminDb = () => connect();
export const agentUser = (alias: string) => `agent_${alias}`;
export const HOME_READER = "home_reader";

/** Query text lives in graph/queries/*.cypher so judges can read every query the agents use. */
const QUERY_DIR = path.join(REPO_ROOT, "graph", "queries");
const queryCache = new Map<string, string>();

export function cypher(name: string): string {
  let q = queryCache.get(name);
  if (!q) {
    const raw = fs.readFileSync(path.join(QUERY_DIR, `${name}.cypher`), "utf8");
    q = raw
      .split("\n")
      .filter((l) => !l.trim().startsWith("//"))
      .join("\n")
      .trim();
    queryCache.set(name, q);
  }
  return q;
}

export interface RunOpts {
  readOnly?: boolean;
  timeoutMs?: number;
  /** ACL user to run as; omitted = write API (default user). */
  as?: string;
}

/** Run Cypher text against a graph. Values come back as plain objects keyed by column. */
export async function run(graph: string, query: string, params: Params = {}, opts: RunOpts = {}): Promise<Row[]> {
  const db = await connect(opts.as);
  const g = db.selectGraph(graph);
  const options = { params: params as any, TIMEOUT: opts.timeoutMs ?? 5000 };
  const reply = opts.readOnly ? await g.roQuery<Row>(query, options) : await g.query<Row>(query, options);
  return (reply.data ?? []) as Row[];
}

/** Run a named query file. */
export function q(graph: string, name: string, params: Params = {}, opts: RunOpts = {}): Promise<Row[]> {
  return run(graph, cypher(name), params, opts);
}

export async function rawCommand(args: string[]): Promise<unknown> {
  const db = await adminDb();
  const conn: any = await db.connection;
  return conn.sendCommand(args);
}

export async function closeAll(): Promise<void> {
  const pending = [...clients.values()];
  clients.clear();
  // A client whose socket died (e.g. FalkorDB restarted) must not hang shutdown.
  await Promise.all(
    pending.map((c) =>
      Promise.race([c.then((db) => db.close()).catch(() => undefined), new Promise((r) => setTimeout(r, 2000))]),
    ),
  );
}

export const now = () => Date.now();
