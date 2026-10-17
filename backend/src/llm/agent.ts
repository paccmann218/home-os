/**
 * LLM agent loop with tool selection through MCP.
 * Each person gets an MCP client attached to:
 *   - homeos-mcp (our typed graph tools, scoped to that person's ACL user), and
 *   - the official FalkorDB MCP server (read-only Cypher over the shared home graph),
 *     when HOMEOS_FALKORDB_MCP=on.
 * The model (Azure OpenAI) sees the union of tools and decides which to call.
 */
import path from "node:path";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import type OpenAI from "openai";
import { llm } from "./azure.js";
import { config, REPO_ROOT } from "../config.js";
import { HOME_READER } from "../graph/db.js";

interface Box {
  clients: { prefix: string; client: Client }[];
  tools: OpenAI.Chat.Completions.ChatCompletionTool[];
  route: Map<string, { client: Client; name: string }>;
}

const boxes = new Map<string, Promise<Box>>();
const TSX_CLI = path.join(REPO_ROOT, "backend", "node_modules", "tsx", "dist", "cli.mjs");

function childEnv(extra: Record<string, string>): Record<string, string> {
  const env: Record<string, string> = {};
  for (const [k, v] of Object.entries(process.env)) if (v !== undefined) env[k] = v;
  return { ...env, ...extra };
}

async function connectHomeosMcp(alias: string): Promise<Client> {
  const client = new Client({ name: `homeos-agent-${alias}`, version: "0.1.0" });
  await client.connect(
    new StdioClientTransport({
      command: process.execPath,
      args: [TSX_CLI, path.join(REPO_ROOT, "backend", "src", "mcp", "server.ts")],
      env: childEnv({ HOMEOS_ALIAS: alias }),
      stderr: "ignore",
    }),
  );
  return client;
}

async function connectFalkorMcp(): Promise<Client | null> {
  if (process.env.HOMEOS_FALKORDB_MCP !== "on") return null;
  const client = new Client({ name: "homeos-falkordb-mcp", version: "0.1.0" });
  // Official server, installed as a dependency (@falkordb/mcpserver) so it starts instantly.
  await client.connect(
    new StdioClientTransport({
      command: process.execPath,
      args: [path.join(REPO_ROOT, "backend", "node_modules", "@falkordb", "mcpserver", "dist", "index.js")],
      env: childEnv({
        FALKORDB_HOST: config.falkor.host,
        FALKORDB_PORT: String(config.falkor.port),
        FALKORDB_USERNAME: HOME_READER,
        FALKORDB_PASSWORD: config.falkor.agentPassword,
        FALKORDB_DEFAULT_READONLY: "true",
        MCP_TRANSPORT: "stdio",
      }),
      stderr: "ignore",
    }),
  );
  return client;
}

async function buildBox(alias: string): Promise<Box> {
  const clients: Box["clients"] = [{ prefix: "", client: await connectHomeosMcp(alias) }];
  try {
    const f = await connectFalkorMcp();
    if (f) clients.push({ prefix: "falkordb__", client: f });
  } catch (e) {
    console.warn("[agent] FalkorDB MCP unavailable:", (e as Error).message);
  }
  const tools: Box["tools"] = [];
  const route: Box["route"] = new Map();
  for (const { prefix, client } of clients) {
    const listed = await client.listTools();
    for (const t of listed.tools) {
      // Only read-only Cypher from the official server; writes always go through our typed API.
      if (prefix && !/readonly|schema|list_graphs/i.test(t.name)) continue;
      const name = `${prefix}${t.name}`.slice(0, 64);
      route.set(name, { client, name: t.name });
      tools.push({
        type: "function",
        function: {
          name,
          description: (prefix ? "[official FalkorDB MCP, read-only, shared home graph 'home:" + config.homeId + "'] " : "") + (t.description ?? ""),
          parameters: (t.inputSchema as any) ?? { type: "object", properties: {} },
        },
      });
    }
  }
  return { clients, tools, route };
}

export function toolbox(alias: string): Promise<Box> {
  let b = boxes.get(alias);
  if (!b) {
    b = buildBox(alias);
    boxes.set(alias, b);
    b.catch(() => boxes.delete(alias));
  }
  return b;
}

export interface ToolTrace {
  tool: string;
  args: unknown;
  result: string;
}

export async function runAgent(opts: {
  alias: string;
  system: string;
  messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[];
  allow?: (toolName: string) => boolean;
  maxSteps?: number;
  json?: boolean;
  onTool?: (t: ToolTrace) => void;
}): Promise<{ text: string; trace: ToolTrace[] }> {
  const box = await toolbox(opts.alias);
  const tools = box.tools.filter((t) => t.type === "function" && (opts.allow ? opts.allow(t.function.name) : true));
  const messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[] = [{ role: "system", content: opts.system }, ...opts.messages];
  const trace: ToolTrace[] = [];
  for (let step = 0; step < (opts.maxSteps ?? 6); step++) {
    const res = await llm().chat.completions.create({
      model: config.azure.chatModel,
      messages,
      tools: tools.length ? tools : undefined,
      max_completion_tokens: 1200,
    });
    const msg = res.choices[0].message;
    messages.push(msg as any);
    if (!msg.tool_calls?.length) return { text: msg.content ?? "", trace };
    for (const call of msg.tool_calls) {
      if (call.type !== "function") continue;
      const r = box.route.get(call.function.name);
      let result = "";
      let args: unknown = {};
      try {
        args = JSON.parse(call.function.arguments || "{}");
        if (!r) throw new Error(`unknown tool ${call.function.name}`);
        const out = await r.client.callTool({ name: r.name, arguments: args as Record<string, unknown> });
        result = (out.content as any[]).map((c) => (c.type === "text" ? c.text : "")).join("\n");
      } catch (e) {
        result = `error: ${(e as Error).message}`;
      }
      const t = { tool: call.function.name, args, result: result.slice(0, 6000) };
      trace.push(t);
      opts.onTool?.(t);
      messages.push({ role: "tool", tool_call_id: call.id, content: t.result });
    }
  }
  return { text: "", trace };
}

export async function closeToolboxes(): Promise<void> {
  for (const b of boxes.values()) {
    try {
      for (const c of (await b).clients) await c.client.close();
    } catch {
      /* ignore */
    }
  }
  boxes.clear();
}
