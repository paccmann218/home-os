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
  const clock = by.clock?.state ?? {};
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
        "clock",
        "Google Clock",
        clock.alarm_primary ? `${clock.alarm_primary} · ${clock.alarm_backup}` : "No alarm",
        clock.set_for ? `set for ${clock.set_for}` : "bedroom",
        Boolean(clock.alarm_primary),
      )}
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

/** Ask HomeOS as any person (same Coach + MCP tools as the watch; reply shown here, not spoken). */
export function AskBox({ people, onAsk }: { people: string[]; onAsk: (alias: string, text: string) => Promise<{ reply: string; tools: string[] }> }) {
  const [who, setWho] = useState(people[0] ?? "");
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [answer, setAnswer] = useState<{ q: string; reply: string; tools: string[] } | null>(null);
  const alias = people.includes(who) ? who : people[0] ?? "";
  const send = async () => {
    if (!text.trim() || !alias) return;
    setBusy(true);
    const q = text;
    setText("");
    try {
      const r = await onAsk(alias, q);
      setAnswer({ q, reply: r.reply, tools: r.tools ?? [] });
    } finally {
      setBusy(false);
    }
  };
  return (
    <div>
      <input
        className="input"
        style={{ width: "100%" }}
        placeholder="Why did I sleep badly? What helps me when I'm sick?"
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && send()}
      />
      <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
        <select value={alias} onChange={(e) => setWho(e.target.value)} className="input" style={{ flex: 1, minWidth: 0 }}>
          {people.map((p) => (
            <option key={p}>{p}</option>
          ))}
        </select>
        <button className="primary" disabled={busy} onClick={send}>
          {busy ? "Thinking…" : "Ask"}
        </button>
      </div>
      {answer && (
        <div style={{ marginTop: 8 }}>
          <div className="muted" style={{ fontSize: 11 }}>
            {alias}: “{answer.q}”
          </div>
          <div style={{ marginTop: 4, lineHeight: 1.4 }}>{answer.reply}</div>
          {answer.tools.length > 0 && (
            <div style={{ marginTop: 4 }}>
              {answer.tools.map((t, i) => (
                <span className="tag" key={i}>
                  {t}
                </span>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/** The Scientist's latest report for a person: algorithm insights + most likely cause chains. */
export function ScientistReport({ r, chains, onClose }: { r: any; chains: any[]; onClose: () => void }) {
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
        <span className="muted" style={{ fontSize: 11 }}>
          {r.alias} · {r.ms} ms over the full history
        </span>
        <span className="spacer" />
        <button style={{ padding: "2px 8px", fontSize: 10 }} onClick={onClose}>
          close
        </button>
      </div>
      {r.insights.map((t: string, i: number) => (
        <div key={i} style={{ margin: "6px 0", lineHeight: 1.35 }}>
          <span className="tag">{["pageRank", "betweenness", "labelPropagation"][i] ?? "insight"}</span> {t}
        </div>
      ))}
      {chains.length > 0 && (
        <div style={{ marginTop: 8 }}>
          <div className="muted" style={{ fontSize: 11, marginBottom: 4 }}>
            Why the last bad night? algo.SPpaths over −ln P(bad | deviation)
          </div>
          {chains.map((c: any, i: number) => (
            <div key={i} className="rej">
              <b>{c.chain.slice(1).join(" → ")}</b> — likelihood {Number(c.likelihood).toFixed(2)}
            </div>
          ))}
        </div>
      )}
      {r.playbookChanges?.length > 0 && <div className="privacy">Playbooks updated: {r.playbookChanges.join(", ")}</div>}
    </div>
  );
}
