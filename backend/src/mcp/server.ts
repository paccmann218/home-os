/**
 * homeos-mcp: HomeOS graph tools over the Model Context Protocol (stdio).
 * One server instance is scoped to ONE person (HOMEOS_ALIAS) and connects to FalkorDB with that
 * person's ACL user, so an LLM using it can reach only that person's memory plus shared home state.
 *
 *   HOMEOS_ALIAS=harsh npx tsx src/mcp/server.ts
 *
 * Any MCP client (Claude Desktop, Cursor, the HomeOS agents) can attach to it.
 */
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { tools } from "../tools/index.js";

const alias = process.env.HOMEOS_ALIAS;
if (!alias) {
  console.error("Set HOMEOS_ALIAS to the person this MCP server acts for.");
  process.exit(1);
}

// stdout carries the MCP protocol: route stray logs to stderr.
console.log = (...a: unknown[]) => console.error(...a);

const server = new McpServer({ name: `homeos-mcp (${alias})`, version: "0.1.0" });

for (const t of tools) {
  server.registerTool(
    t.name,
    {
      description: t.description + (t.write ? " [writes via the typed HomeOS write API]" : " [read-only]"),
      inputSchema: t.schema.shape,
    },
    async (args: any) => {
      try {
        const out = await t.run(alias, args ?? {});
        return { content: [{ type: "text" as const, text: JSON.stringify(out, null, 1).slice(0, 12000) }] };
      } catch (e) {
        return { isError: true, content: [{ type: "text" as const, text: `error: ${(e as Error).message}` }] };
      }
    },
  );
}

await server.connect(new StdioServerTransport());
