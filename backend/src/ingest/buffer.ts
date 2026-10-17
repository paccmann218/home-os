/**
 * Raw sample buffer. 1 Hz heart rate, inter-beat intervals and room readings live HERE, in memory,
 * never in the graph. The graph only stores what agents reason over (episodes, actions, outcomes).
 */
import { bus } from "../bus.js";

export interface HrSample {
  t: number;
  hr: number;
  ibi?: number[];
}

export interface RoomReading {
  t: number;
  temperature?: number | null;
  humidity?: number | null;
  bright?: boolean | null;
}

const WINDOW_MS = 30 * 60_000;
const hr = new Map<string, HrSample[]>();
const onBody = new Map<string, { on: boolean; t: number }>();
let room: RoomReading[] = [];

export function pushHr(alias: string, s: HrSample): void {
  const arr = hr.get(alias) ?? [];
  arr.push(s);
  const cutoff = s.t - WINDOW_MS;
  while (arr.length && arr[0].t < cutoff) arr.shift();
  hr.set(alias, arr);
  bus.emit("signal", { person: alias, t: s.t, hr: s.hr, rmssd: rmssd(alias, 60_000) });
}

export function pushRoom(r: RoomReading): void {
  room.push(r);
  const cutoff = r.t - WINDOW_MS;
  room = room.filter((x) => x.t >= cutoff);
  bus.emit("room", r);
}

export function setOnBody(alias: string, on: boolean): void {
  onBody.set(alias, { on, t: Date.now() });
}

export const isOnBody = (alias: string) => onBody.get(alias)?.on ?? false;

export function hrSamples(alias: string, sinceMs: number, untilMs = Date.now()): HrSample[] {
  return (hr.get(alias) ?? []).filter((s) => s.t >= sinceMs && s.t <= untilMs);
}

export function latestHr(alias: string): HrSample | null {
  const arr = hr.get(alias);
  if (!arr?.length) return null;
  const last = arr[arr.length - 1];
  return Date.now() - last.t < 20_000 ? last : null;
}

export function meanHr(alias: string, windowMs: number, endMs = Date.now()): number | null {
  const s = hrSamples(alias, endMs - windowMs, endMs);
  if (!s.length) return null;
  return s.reduce((a, b) => a + b.hr, 0) / s.length;
}

/** RMSSD (ms) over the IBIs received in a window; null without IBI (e.g. no Samsung SDK). */
export function rmssd(alias: string, windowMs: number, endMs = Date.now()): number | null {
  const ibis = hrSamples(alias, endMs - windowMs, endMs)
    .flatMap((s) => s.ibi ?? [])
    .filter((x) => x > 300 && x < 2000);
  if (ibis.length < 10) return null;
  let sum = 0;
  for (let i = 1; i < ibis.length; i++) sum += (ibis[i] - ibis[i - 1]) ** 2;
  return Math.sqrt(sum / (ibis.length - 1));
}

export function latestRoom(): RoomReading | null {
  return room.length ? room[room.length - 1] : null;
}

export function snapshot() {
  const people: Record<string, unknown> = {};
  for (const [alias] of hr) {
    people[alias] = { hr: latestHr(alias)?.hr ?? null, rmssd: rmssd(alias, 120_000), onBody: isOnBody(alias) };
  }
  return { people, room: latestRoom() };
}
