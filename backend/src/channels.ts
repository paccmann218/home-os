/**
 * Outbound channels to a person's devices (watch app, phone app). Each connected socket registers
 * itself here; agents call say()/pacer()/prompt() without knowing which device is attached.
 */
import type { WebSocket } from "ws";
import { bus } from "./bus.js";
import { speak } from "./voice/sarvam.js";

type Kind = "watch" | "phone";
const sockets = new Map<string, Map<WebSocket, Kind>>();

export function register(alias: string, ws: WebSocket, kind: Kind): void {
  const m = sockets.get(alias) ?? new Map();
  m.set(ws, kind);
  sockets.set(alias, m);
  ws.on("close", () => m.delete(ws));
}

export function connected(alias: string): Kind[] {
  return [...(sockets.get(alias)?.values() ?? [])];
}

function send(alias: string, msg: unknown, only?: Kind): number {
  let n = 0;
  for (const [ws, kind] of sockets.get(alias) ?? []) {
    if (only && kind !== only) continue;
    if (ws.readyState === ws.OPEN) {
      ws.send(JSON.stringify(msg));
      n++;
    }
  }
  return n;
}

/** Speak to a person: text always; Sarvam audio URL when voice is configured (else the device's own TTS). */
export async function say(alias: string, text: string, opts: { lang?: string; agent?: string } = {}): Promise<void> {
  bus.feed({ agent: opts.agent ?? `coach:${alias}`, kind: "say", person: alias, text });
  const audio = await speak(text, opts.lang).catch(() => null);
  send(alias, { type: "say", text, audio_url: audio?.url ?? null, lang: audio?.lang ?? opts.lang ?? "en-IN" });
}

export function pacer(alias: string, bpm: number, seconds: number): boolean {
  bus.feed({ agent: `planner:${alias}`, kind: "pacer", person: alias, text: `Breathing pacer ${bpm}/min for ${seconds}s on the watch` });
  return send(alias, { type: "pacer", bpm, seconds }, "watch") > 0;
}

export function prompt(alias: string, id: string, text: string, options: string[]): void {
  bus.feed({ agent: `guardian:${alias}`, kind: "prompt", person: alias, text });
  send(alias, { type: "prompt", id, text, options });
}

export function state(alias: string, payload: unknown): void {
  send(alias, { type: "state", ...(payload as object) });
}
