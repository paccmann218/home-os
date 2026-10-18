import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";

const worldFile = resolve(dirname(fileURLToPath(import.meta.url)), "../frontned 3d/index.html");

/** Serves the walkable house at /world/ in dev, preview, and the production build. */
function homeosWorld(): Plugin {
  const send = (req: { url?: string }, res: { statusCode: number; setHeader: (k: string, v: string) => void; end: (b: Buffer) => void }, next: () => void) => {
    const path = req.url?.split("?")[0] ?? "";
    if (path !== "/world" && path !== "/world/" && path !== "/world/index.html") return next();
    res.statusCode = 200;
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.end(readFileSync(worldFile));
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
      this.emitFile({ type: "asset", fileName: "world/index.html", source: readFileSync(worldFile) });
    },
  };
}

// Static site: `npm run build` writes landing/dist, which any static host can serve.
export default defineConfig({
  plugins: [react(), homeosWorld()],
  build: { target: "es2020" },
});
