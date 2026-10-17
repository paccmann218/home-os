/** Azure OpenAI (v1 endpoint) for agent reasoning and embeddings. */
import OpenAI from "openai";
import { config } from "../config.js";

let client: OpenAI | null = null;

export function llm(): OpenAI {
  if (!client) {
    if (!config.azure.baseURL || !config.azure.apiKey) throw new Error("AZURE_OPENAI_BASE_URL / AZURE_OPENAI_API_KEY missing in .env");
    client = new OpenAI({
      baseURL: config.azure.baseURL,
      apiKey: config.azure.apiKey,
      defaultHeaders: { "api-key": config.azure.apiKey },
    });
  }
  return client;
}

export const llmAvailable = () => Boolean(config.azure.baseURL && config.azure.apiKey);

const embedCache = new Map<string, number[]>();

export async function embed(text: string): Promise<number[] | null> {
  if (!llmAvailable() || !text.trim()) return null;
  const key = text.trim().slice(0, 2000);
  const hit = embedCache.get(key);
  if (hit) return hit;
  try {
    const res = await llm().embeddings.create({ model: config.azure.embedModel, input: key });
    const vec = res.data[0]?.embedding ?? null;
    if (vec) embedCache.set(key, vec);
    return vec;
  } catch (e) {
    console.warn("[embed] failed:", (e as Error).message);
    return null;
  }
}
