import { useState } from "react";
import { AGENT_COLOR, fmtTime, type FeedEvent } from "../api";

export function Feed({ items }: { items: FeedEvent[] }) {
  return (
    <div className="scroll">
      {[...items].reverse().map((e, i) => (
        <div className="feed-item" key={`${e.at}-${i}`}>
          <div className="t mono">{fmtTime(e.at)}</div>
          <div>
            <div className="agent" style={{ ["--c" as any]: AGENT_COLOR(e.agent) }}>
              <i />
              {e.agent}
            </div>
            <div className="msg">{e.text}</div>
          </div>
        </div>
      ))}
    </div>
  );
}

export interface Device {
  id: string;
  state: Record<string, any>;
  leased_by: string | null;
}

export function Room({ devices, flash }: { devices: Device[]; flash: Set<string> }) {
  const by = Object.fromEntries(devices.map((d) => [d.id, d]));
  const ac = by.ac?.state ?? {};
  const fan = by.fan?.state ?? {};
  const light = by.light?.state ?? {};
  const env = by.env?.state ?? {};
  const tile = (id: string, label: string, value: string, sub: string, on: boolean) => (
    <div className={`device ${on ? "on" : ""} ${flash.has(id) ? "flash" : ""}`} key={id}>
      {by[id]?.leased_by && <span className="lease">⚿ {by[id].leased_by}</span>}
      <div className="label">{label}</div>
      <div className="value">{value}</div>
      <div className="sub">{sub}</div>
    </div>
  );
  return (
    <div className="devices">
      {tile("ac", "Air conditioner", ac.power === "on" ? `${ac.setpoint} °C` : "Off", ac.power === "on" ? "cooling to setpoint" : "standby", ac.power === "on")}
      {tile("fan", "Fan", fan.level ? `Level ${fan.level}` : "Off", "0–3", (fan.level ?? 0) > 0)}
      {tile("light", "Light (ESP32)", light.brightness ? `${light.brightness}%` : "Off", "dimmable LED", (light.brightness ?? 0) > 0)}
      {tile(
        "env",
        "Room sensor",
        env.temperature != null ? `${Number(env.temperature).toFixed(1)} °C` : "—",
        env.humidity != null ? `${Math.round(env.humidity)}% RH · ${env.bright ? "bright" : "dark"}` : "ESP32 offline",
        false,
      )}
    </div>
  );
}

export function Board({ tasks }: { tasks: any[] }) {
  return (
    <div className="scroll">
      {tasks.map((t) => (
        <div className="board-row" key={t.id}>
          <div>
            <span className="mono">{t.kind}</span> <span className="muted">→ {t.agent}</span>
            {t.from && <span className="muted"> · handoff</span>}
            {t.reclaimed ? <span className="status-claimed"> · reclaimed</span> : null}
          </div>
          <div className={`status-${t.status}`} style={{ textAlign: "right" }}>
            {t.status}
          </div>
        </div>
      ))}
      {!tasks.length && <div className="muted">No tasks in the last hour.</div>}
    </div>
  );
}

/** Evidence ranking for one episode kind: current mean effect (bar) vs. before the last outcome (thin ghost bar). */
export function EvidenceBars({ rows, before }: { rows: any[]; before?: any[] }) {
  const [tip, setTip] = useState<{ x: number; y: number; text: string } | null>(null);
  const max = Math.max(0.5, ...rows.map((r) => Math.abs(r.mean)), ...(before ?? []).map((r) => Math.abs(r.mean)));
  return (
    <div className="bars">
      {rows.map((r) => {
        const prev = before?.find((b) => b.intervention === r.intervention);
        return (
          <div
            className="row"
            key={r.intervention}
            onMouseMove={(e) =>
              setTip({
                x: e.clientX + 12,
                y: e.clientY + 12,
                text: `${r.label}: mean effect ${r.mean.toFixed(2)} over n=${r.n} (${r.learned_n} from your body)${prev ? `, was ${prev.mean.toFixed(2)}` : ""}`,
              })
            }
            onMouseLeave={() => setTip(null)}
          >
            <div className="name">{r.label}</div>
            <div className="track">
              <div className="bar" style={{ width: `${(Math.max(r.mean, 0) / max) * 100}%` }} />
              {prev && <div className="ghost" style={{ width: `${(Math.max(prev.mean, 0) / max) * 100}%` }} />}
            </div>
            <div className="val mono">
              {r.mean.toFixed(2)} <span className="muted">n{r.n}</span>
            </div>
          </div>
        );
      })}
      {tip && (
        <div className="tooltip" style={{ left: tip.x, top: tip.y }}>
          {tip.text}
        </div>
      )}
    </div>
  );
}

export function DecisionCard({ d, owner }: { d: any; owner: string }) {
  if (!d?.private) return <div className="muted">No decision yet. Tap a chip on the watch.</div>;
  const p = d.private;
  return (
    <div className="decision">
      <div className="muted" style={{ fontSize: 11 }}>
        {owner} · {p.episode_kind} · {fmtTime(p.at)}
      </div>
      <div className="chosen">{p.chosen.map((c: any) => c.label).join(" + ") || "No action"}</div>
      <div style={{ marginBottom: 6 }}>
        {p.context.map((k: string) => (
          <span className="tag" key={k}>
            {k}
          </span>
        ))}
      </div>
      {p.constraints.filter((c: any) => c.var).map((c: any, i: number) => (
        <span className="tag bound" key={i}>
          {c.var} {c.min ?? "…"}–{c.max ?? "…"} · {c.mine ? `yours${c.reason ? ` (${c.reason})` : ""}` : "someone present"}
        </span>
      ))}
      {(p.limited ?? []).filter((r: any) => r?.name).map((r: any) => (
        <div className="rej" key={`l-${r.name}`}>
          ⤓ <b>{r.name}</b> — {r.because}
        </div>
      ))}
      {p.rejected.filter((r: any) => r?.name).map((r: any) => (
        <div className="rej" key={r.name}>
          ✕ <b>{r.name}</b> — {r.because}
        </div>
      ))}
      {p.rationale && <div className="secondary" style={{ marginTop: 6, fontStyle: "italic" }}>“{p.rationale}”</div>}
      <div className="privacy">Private view of {owner}'s graph. Other people's reasons are never stored here.</div>
    </div>
  );
}
