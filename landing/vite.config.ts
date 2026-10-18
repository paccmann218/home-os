import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";

const worldDir = resolve(dirname(fileURLToPath(import.meta.url)), "../frontned 3d");
const worldPages: Record<string, { file: string; type: string }> = {
  "/world": { file: "index.html", type: "text/html; charset=utf-8" },
  "/world/": { file: "index.html", type: "text/html; charset=utf-8" },
  "/world/index.html": { file: "index.html", type: "text/html; charset=utf-8" },
  "/world/quest.js": { file: "quest.js", type: "text/javascript; charset=utf-8" },
};

/** Serves the walkable house, and its quest, at /world/. */
function homeosWorld(): Plugin {
  const send = (req: { url?: string }, res: { statusCode: number; setHeader: (k: string, v: string) => void; end: (b: Buffer) => void }, next: () => void) => {
    const path = req.url?.split("?")[0] ?? "";
    const page = worldPages[path];
    if (!page) return next();
    res.statusCode = 200;
    res.setHeader("Content-Type", page.type);
    res.end(readFileSync(resolve(worldDir, page.file)));
  };
  return {
    name: "homeos-world",
    configureServer(server) {
      server.middlewares.use(send);
    },
    configurePreviewServer(server) {
      server.middlewares.use(send);
    },
    generateBundle() {
      this.emitFile({
        type: "asset",
        fileName: "world/index.html",
        source: readFileSync(resolve(worldDir, "index.html")),
      });
      this.emitFile({
        type: "asset",
        fileName: "world/quest.js",
        source: readFileSync(resolve(worldDir, "quest.js")),
      });
    },
  };
}

// Static site: `npm run build` writes landing/dist, which any static host can serve.
export default defineConfig({
  plugins: [react(), homeosWorld()],
  build: { target: "es2020" },
});
