/**
 * Device drivers. The graph says WHAT to set (via arbitration); drivers only carry it out.
 *   virtual -> state lives in the home graph + dashboard (AC and fan until an IR blaster is added)
 *   esp32   -> ESPHome REST API on the room node (LED light, DHT11, light sensor)
 *   wiz     -> Philips WiZ bulb over local UDP (port 38899)
 */
import dgram from "node:dgram";
import { config } from "../config.js";
import { setDeviceState } from "../writeapi/index.js";
import { pushRoom } from "../ingest/buffer.js";
import { bus } from "../bus.js";

/** Ablation mode (HOMEOS_GRAPH=off): no FalkorDB at all, device state lives in process memory. */
export const memoryState = new Map<string, Record<string, unknown>>();

export interface Setting {
  power?: "on" | "off";
  setpoint?: number;
  level?: number;
  brightness?: number;
}

function entityPaths(domain: string, name: string): string[] {
  const objectId = name.toLowerCase().replace(/[^a-z0-9]/g, "_");
  return [`/${domain}/${encodeURIComponent(name)}`, `/${domain}/${objectId}`];
}

async function esp(domain: string, name: string, action = "", params: Record<string, string | number> = {}, method = "GET") {
  const host = config.devices.esp32Host;
  if (!host) throw new Error("ESP32_HOST not set");
  let lastErr: unknown = null;
  for (const p of entityPaths(domain, name)) {
    const qs = new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)])).toString();
    const url = `http://${host}${p}${action ? `/${action}` : ""}${qs ? `?${qs}` : ""}`;
    try {
      const res = await fetch(url, { method, signal: AbortSignal.timeout(4000) });
      if (res.status === 404) continue;
      const body = await res.text();
      return body ? JSON.parse(body) : {};
    } catch (e) {
      lastErr = e;
    }
  }
  throw lastErr ?? new Error(`ESP32 entity ${name} not found`);
}

function wiz(params: Record<string, unknown>): Promise<void> {
  const host = config.devices.wizHost;
  if (!host) return Promise.reject(new Error("WIZ_HOST not set"));
  return new Promise((resolve, reject) => {
    const sock = dgram.createSocket("udp4");
    const msg = Buffer.from(JSON.stringify({ method: "setPilot", params }));
    sock.send(msg, 38899, host, (err) => {
      sock.close();
      err ? reject(err) : resolve();
    });
  });
}

/** Apply a setting to a device; returns what was actually applied (and whether hardware confirmed). */
export async function apply(device: string, driver: string, s: Setting): Promise<{ applied: Setting; hardware: boolean; error?: string }> {
  let hardware = false;
  let error: string | undefined;
  try {
    if (driver === "esp32" && device === "light") {
      if (s.power === "off" || s.brightness === 0) await esp("light", "Demo Light", "turn_off", {}, "POST");
      else await esp("light", "Demo Light", "turn_on", { brightness: Math.round(((s.brightness ?? 60) / 100) * 255) }, "POST");
      hardware = true;
    } else if (driver === "wiz") {
      if (s.power === "off" || s.brightness === 0) await wiz({ state: false });
      else await wiz({ state: true, dimming: Math.max(10, Math.round(s.brightness ?? 60)), temp: (s.brightness ?? 60) < 40 ? 2700 : 4500 });
      hardware = true;
    }
  } catch (e) {
    error = (e as Error).message;
  }
  const state: Record<string, unknown> = { ...s };
  if (s.level !== undefined) state.power = s.level > 0 ? "on" : "off";
  if (s.brightness !== undefined) state.power = s.brightness > 0 ? "on" : "off";
  if (s.setpoint !== undefined) state.power = "on";
  if (config.graphEnabled) await setDeviceState(device, state);
  else memoryState.set(device, { ...(memoryState.get(device) ?? {}), ...state });
  bus.emit("device", { device, state, hardware, error });
  return { applied: s, hardware, error };
}

/** Poll the ESP32 room sensors into the raw buffer. */
export function startRoomPolling(intervalMs = 10_000): () => void {
  if (!config.devices.esp32Host) return () => undefined;
  const tick = async () => {
    try {
      const [t, h, b] = await Promise.all([
        esp("sensor", "Room Temperature").catch(() => null),
        esp("sensor", "Room Humidity").catch(() => null),
        esp("binary_sensor", "Room Bright").catch(() => null),
      ]);
      const reading = {
        t: Date.now(),
        temperature: typeof t?.value === "number" ? t.value : null,
        humidity: typeof h?.value === "number" ? h.value : null,
        bright: typeof b?.value === "boolean" ? b.value : null,
      };
      pushRoom(reading);
      if (reading.temperature != null) await setDeviceState("env", { temperature: reading.temperature, humidity: reading.humidity, bright: reading.bright });
    } catch {
      /* node offline: keep last reading */
    }
  };
  void tick();
  const h = setInterval(tick, intervalMs);
  return () => clearInterval(h);
}
