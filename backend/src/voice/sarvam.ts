/**
 * Sarvam voice: Saaras speech-to-text (code-mixed Indic + English) and Bulbul text-to-speech.
 * Ported from the team's Rakshak project (NaomiiAP/codexhack backend/src/services/sarvam.ts).
 * Synthesised audio is cached in memory and fetched by the watch over HTTP (keeps WS frames small).
 */
import { config } from "../config.js";

const BASE = "https://api.sarvam.ai";
const audioCache = new Map<string, Buffer>();
let seq = 0;

export const voiceEnabled = () => Boolean(config.sarvam.apiKey);

export async function transcribe(wav: Buffer, mime = "audio/wav"): Promise<{ text: string; lang: string }> {
  if (!voiceEnabled()) throw new Error("SARVAM_API_KEY not set");
  const form = new FormData();
  const clean = mime.split(";")[0].trim() || "audio/wav";
  form.append("file", new Blob([new Uint8Array(wav)], { type: clean }), clean.includes("webm") ? "audio.webm" : "audio.wav");
  form.append("model", config.sarvam.sttModel);
  form.append("language_code", "unknown");
  form.append("mode", "codemix");
  const res = await fetch(`${BASE}/speech-to-text`, {
    method: "POST",
    headers: { "api-subscription-key": config.sarvam.apiKey },
    body: form,
    signal: AbortSignal.timeout(30_000),
  });
  if (!res.ok) throw new Error(`Sarvam STT ${res.status}: ${await res.text()}`);
  const data = (await res.json()) as { transcript?: string; language_code?: string };
  return { text: (data.transcript ?? "").trim(), lang: data.language_code ?? config.sarvam.language };
}

function ttsLang(lang: string | undefined, text: string): string {
  if (lang && /^[a-z]{2}-IN$/.test(lang)) return lang;
  if (/[ऀ-ॿ]/.test(text)) return "hi-IN";
  if (/[ಀ-೿]/.test(text)) return "kn-IN";
  return config.sarvam.language;
}

export async function speak(text: string, lang?: string): Promise<{ url: string; lang: string } | null> {
  if (!voiceEnabled() || !text.trim()) return null;
  const target = ttsLang(lang, text);
  const res = await fetch(`${BASE}/text-to-speech`, {
    method: "POST",
    headers: { "api-subscription-key": config.sarvam.apiKey, "Content-Type": "application/json" },
    body: JSON.stringify({
      text: text.slice(0, 1400),
      target_language_code: target,
      speaker: config.sarvam.ttsSpeaker,
      model: config.sarvam.ttsModel,
      pace: 1.0,
      speech_sample_rate: 24000,
      output_audio_codec: "wav",
    }),
    signal: AbortSignal.timeout(25_000),
  });
  if (!res.ok) throw new Error(`Sarvam TTS ${res.status}: ${await res.text()}`);
  const data = (await res.json()) as { audios?: string[] };
  const b64 = data.audios?.[0];
  if (!b64) return null;
  const id = `${Date.now().toString(36)}${(seq++).toString(36)}`;
  audioCache.set(id, Buffer.from(b64, "base64"));
  if (audioCache.size > 50) audioCache.delete(audioCache.keys().next().value!);
  return { url: `/api/tts/${id}.wav`, lang: target };
}

export const cachedAudio = (id: string) => audioCache.get(id) ?? null;
