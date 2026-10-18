/**
 * Morning alarm, decided from the graph.
 * Reads the person's calendar events and their own sleep episodes, asks on the watch,
 * and only then writes a Decision plus the bedroom clock state.
 */
import { bodyRead, bodyWrite, homeWrite } from "../graph/graphs.js";
import { now } from "../graph/db.js";
import { bus, newId } from "../bus.js";
import { prompt, say } from "../channels.js";
import { setDeviceState } from "../writeapi/index.js";

const HORIZON = 36 * 3600_000;
const OFFSET_MIN = Number(process.env.HOMEOS_UTC_OFFSET_MIN ?? 330);
const DAY = 86_400_000;

export interface MorningPlan {
  alias: string;
  eventId: string;
  title: string;
  startsAt: number;
  source: string;
  badIds: string[];
  badNights: number;
  nights: number;
  alarm1: string;
  alarm2: string;
}

const pending = new Map<string, MorningPlan>();

function local(ms: number) {
  const t = new Date(ms + OFFSET_MIN * 60_000);
  return { h: t.getUTCHours(), m: t.getUTCMinutes() };
}

function clock(h: number, m: number) {
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

function spoken(h: number, m: number) {
  const am = h < 12;
  const hr = h % 12 || 12;
  return `${hr}:${String(m).padStart(2, "0")} ${am ? "AM" : "PM"}`;
}

function shift(ms: number, minutes: number) {
  const base = local(ms);
  const total = base.h * 60 + base.m - minutes;
  const wrapped = ((total % 1440) + 1440) % 1440;
  return clock(Math.floor(wrapped / 60), wrapped % 60);
}

function earlyEnough(ms: number) {
  return local(ms).h < 9;
}

export function isYes(text: string) {
  const t = text.toLowerCase();
  return ["yes", "yeah", "yep", "ok", "okay", "sure", "haan", "han", "हाँ", "हां", "ठीक"].some((w) => t.includes(w));
}

export function isNo(text: string) {
  const t = text.toLowerCase();
  if (isYes(text) && !["no", "nah", "नहीं", "nahi"].some((w) => t.includes(w))) return false;
  return ["no", "nah", "nope", "don't", "dont", "नहीं", "nahi"].some((w) => t.includes(w)) || t.trim() === "ना";
}

export async function recordCalendarEvent(alias: string, ev: { id?: string; title: string; at: number; source?: string }) {
  const id = ev.id ?? newId("cal");
  const [row] = await bodyWrite(alias, "upsert_calendar_event", {
    alias,
    id,
    title: ev.title,
    at: ev.at,
    source: ev.source ?? "google_calendar",
  });
  bus.feed({ agent: `coach:${alias}`, kind: "memory", person: alias, text: `Calendar: ${ev.title}` });
  return row;
}

async function loadCase(alias: string): Promise<MorningPlan | { empty: string } > {
  const rows = await bodyRead(alias, "morning_case", { alias, now: now(), horizon: HORIZON });
  const row = rows.find((r) => r.event_id && earlyEnough(Number(r.starts_at)));
  if (!row) return { empty: "I checked your calendar in the graph. Nothing early tomorrow." };
  const bad = Number(row.bad_nights ?? 0);
  const nights = Number(row.nights ?? 0);
  if (nights === 0) return { empty: "Your calendar has an early event, but the ring has not synced any nights yet." };
  if (bad < 3 && bad * 2 < nights) {
    const loc = local(Number(row.starts_at));
    return { empty: `Your next event is ${row.title} at ${spoken(loc.h, loc.m)}. ${bad} of your last ${nights} nights were bad, so I am not moving the alarm.` };
  }
  const startsAt = Number(row.starts_at);
  return {
    alias,
    eventId: String(row.event_id),
    title: String(row.title),
    startsAt,
    source: String(row.source ?? "google_calendar"),
    badIds: (row.bad_ids ?? []).filter(Boolean).map(String),
    badNights: bad,
    nights,
    alarm1: shift(startsAt, 120),
    alarm2: shift(startsAt, 90),
  };
}

export async function proposeMorning(alias: string) {
  const found = await loadCase(alias);
  if ("empty" in found) {
    await say(alias, found.empty);
    return found;
  }
  const when = spoken(local(found.startsAt).h, local(found.startsAt).m);
  const line = `${found.title} is at ${when} on your calendar. ${found.badNights} of your last ${found.nights} ring nights were bad. I can set the bedroom clock to ${found.alarm1} and ${found.alarm2}.`;
  await say(alias, line);
  const id = newId("morning");
  pending.set(id, found);
  prompt(alias, id, `Set ${found.alarm1} and ${found.alarm2}?`, ["yes", "no"]);
  return { proposed: id, ...found };
}

export async function tryAnswerMorning(alias: string, id: string, answer: string) {
  const plan = pending.get(id);
  if (!plan || plan.alias !== alias) return null;
  pending.delete(id);
  if (!isYes(answer) || isNo(answer)) {
    await say(alias, "Okay. I left the clock alone.");
    return { ignored: true };
  }
  return commitMorning(plan);
}

/** Spoken yes/no while a morning prompt is open. Returns null if this text is not that answer. */
export async function trySpokenMorning(alias: string, text: string) {
  const hit = [...pending.entries()].find(([, p]) => p.alias === alias);
  if (!hit) return null;
  if (!isYes(text) && !isNo(text)) return null;
  return tryAnswerMorning(alias, hit[0], text);
}

async function commitMorning(plan: MorningPlan) {
  await say(plan.alias, "Creating the task from the graph.");
  await homeWrite("ensure_clock", {});
  const state = {
    power: "on",
    alarm_primary: plan.alarm1,
    alarm_backup: plan.alarm2,
    set_for: plan.alias,
    event: plan.eventId,
  };
  await setDeviceState("clock", state);
  bus.emit("device", { device: "clock", state, hardware: false });
  const decision = newId("dec");
  const episode = newId("ep");
  const when = spoken(local(plan.startsAt).h, local(plan.startsAt).m);
  const rationale = `${plan.title} at ${when} (${plan.source}) plus ${plan.badNights}/${plan.nights} bad nights in the last 7 days. Clock set to ${plan.alarm1} and ${plan.alarm2}.`;
  await bodyWrite(plan.alias, "record_morning_decision", {
    alias: plan.alias,
    event: plan.eventId,
    episode,
    decision,
    now: now(),
    summary: rationale,
    rationale,
    sleep_ids: plan.badIds,
  });
  bus.emit("decision", { person: plan.alias, decision });
  bus.feed({ agent: `planner:${plan.alias}`, kind: "act", person: plan.alias, text: `Google Clock ${plan.alarm1} and ${plan.alarm2}` });
  await say(plan.alias, `Done. The bedroom clock is set for ${plan.alarm1} and ${plan.alarm2}, before ${plan.title} at ${when}.`);
  return { decision, alarms: [plan.alarm1, plan.alarm2] };
}

export const morningWindow = { horizon: HORIZON, day: DAY };
