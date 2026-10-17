import { config as loadEnv } from "dotenv";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
export const REPO_ROOT = path.resolve(here, "..", "..");
loadEnv({ path: path.join(REPO_ROOT, ".env"), quiet: true });

function env(name: string, fallback = ""): string {
  return process.env[name] ?? fallback;
}

export const config = {
  falkor: {
    host: env("FALKORDB_HOST", "127.0.0.1"),
    port: Number(env("FALKORDB_PORT", "6379")),
    // Password every per-agent ACL user is created with (local demo secret).
    agentPassword: env("HOMEOS_AGENT_PASSWORD", "homeos-agent"),
  },
  homeId: env("HOMEOS_HOME", "flat1"),
  // People in the household. Each gets a private body:<alias> graph.
  people: [
    ...env("HOMEOS_PEOPLE", "harsh,shashwath").split(",").map((s) => s.trim()).filter(Boolean),
    ...env("HOMEOS_DATASET_PEOPLE", "").split(",").map((s) => s.trim()).filter(Boolean),
  ].filter((p, i, a) => a.indexOf(p) === i),
  // People whose memory comes from an imported public dataset (e.g. PMData). They have a private
  // graph like everyone else but are never present in the home and never control devices.
  datasetPeople: env("HOMEOS_DATASET_PEOPLE", "").split(",").map((s) => s.trim()).filter(Boolean),
  // Ablation switch: HOMEOS_GRAPH=off runs the fixed-rule fallback with no FalkorDB.
  graphEnabled: env("HOMEOS_GRAPH", "on") !== "off",
  azure: {
    baseURL: env("AZURE_OPENAI_BASE_URL"),
    apiKey: env("AZURE_OPENAI_API_KEY"),
    chatModel: env("AZURE_OPENAI_CHAT_DEPLOYMENT", "gpt-5.4-mini"),
    embedModel: env("AZURE_OPENAI_EMBED_DEPLOYMENT", "text-embedding-3-small"),
    embedDim: Number(env("HOMEOS_EMBED_DIM", "1536")),
  },
  sarvam: {
    apiKey: env("SARVAM_API_KEY"),
    sttModel: env("SARVAM_STT_MODEL", "saaras:v3"),
    ttsModel: env("SARVAM_TTS_MODEL", "bulbul:v3"),
    ttsSpeaker: env("SARVAM_TTS_SPEAKER", "priya"),
    language: env("SARVAM_LANGUAGE", "en-IN"),
  },
  server: {
    port: Number(env("PORT", "3001")),
    publicBaseUrl: env("PUBLIC_BASE_URL", ""),
  },
  devices: {
    esp32Host: env("ESP32_HOST", ""),
    wizHost: env("WIZ_HOST", ""),
  },
  // Seconds the evaluator waits after an action before measuring the body's response.
  evalWindowSec: Number(env("HOMEOS_EVAL_WINDOW_SEC", "180")),
};

export const homeGraph = () => `home:${config.homeId}`;
export const bodyGraph = (alias: string) => `body:${alias}`;
