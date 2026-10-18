import { useLayoutEffect, useRef, useState } from "react";
import { m } from "framer-motion";
import { HouseIcon, LockSimpleIcon, ProhibitIcon } from "@phosphor-icons/react";
import { PRIVACY } from "../content";
import { gsap, MOTION_OK, useGSAP } from "../gsap";
import { group, inView, item, STATIC } from "../motion";
import { CheckIcon } from "./Icons";
import { SectionHead } from "./SectionHead";

type Row = { id?: string; k: string; v: string; note?: string; blocked?: boolean; out?: boolean; key?: boolean };

const SHASHWATH: Row[] = [
  { k: "Condition", v: "fever" },
  { k: "implies", v: "sick", blocked: true },
  { id: "shift", k: "Shifts", v: "room_temp ≥ 26 °C", out: true },
];
const HARSH: Row[] = [
  { k: "Episode", v: "post-workout" },
  { id: "plan", k: "Plan", v: "fan · level 1", out: true },
];
const HOME: Row[] = [
  { k: "Room", v: "bedroom" },
  { id: "constraint", k: "Constraint", v: "room_temp 26–27 °C", note: "priority 3", key: true },
  { id: "intent", k: "Intent", v: "fan · level 1", key: true },
  { k: "Device", v: "ac · 26 °C" },
];
const WIRES = [
  { from: "shift", to: "constraint", label: "range + priority" },
  { from: "plan", to: "intent", label: "intent" },
];

type Refs = React.MutableRefObject<Record<string, HTMLElement | null>>;

function Panel({ name, tag, rows, home, refs }: { name: string; tag: string; rows: Row[]; home?: boolean; refs: Refs }) {
  return (
    <div className={`pd-panel${home ? " is-home" : ""}`}>
      <div className="pd-head">
        {home ? <HouseIcon size={16} weight="bold" aria-hidden="true" /> : <LockSimpleIcon size={16} weight="bold" aria-hidden="true" />}
        <span className="pd-name">{name}</span>
        <span className={`pd-tag ${home ? "hue-amber" : "hue-violet"}`}>{tag}</span>
      </div>
      <ul>
        {rows.map((r) => (
          <li
            key={r.k + r.v}
            className={`pd-row${r.key ? " is-key" : ""}`}
            ref={
              r.id
                ? (el) => {
                    refs.current[r.id!] = el;
                  }
                : undefined
            }
          >
            <span className="pd-k">{r.k}</span>
            <span className="pd-v">
              {r.v}
              {r.note && <span className="pd-note">{r.note}</span>}
            </span>
            {r.blocked && (
              <span className="pd-blocked">
                <ProhibitIcon size={12} weight="bold" aria-hidden="true" />
                never shared
              </span>
            )}
            {r.out && <span className="pd-out">→ home</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}

type Wire = { d: string; mx: number; my: number; label: string };

function PrivacyDiagram() {
  const root = useRef<HTMLDivElement>(null);
  const refs = useRef<Record<string, HTMLElement | null>>({});
  const [wires, setWires] = useState<Wire[]>([]);

  // Wires are drawn from the real row positions, so they follow the layout at any width.
  useLayoutEffect(() => {
    const el = root.current;
    if (!el) return;
    const measure = () => {
      const box = el.getBoundingClientRect();
      const next: Wire[] = [];
      for (const w of WIRES) {
        const a = refs.current[w.from]?.getBoundingClientRect();
        const b = refs.current[w.to]?.getBoundingClientRect();
        if (!a || !b || b.left < a.right) continue; // stacked layout: the "→ home" tags take over
        const x1 = a.right - box.left;
        const y1 = a.top + a.height / 2 - box.top;
        const x2 = b.left - box.left - 6;
        const y2 = b.top + b.height / 2 - box.top;
        const dx = (x2 - x1) * 0.5;
        next.push({
          d: `M${x1} ${y1} C${x1 + dx} ${y1} ${x2 - dx} ${y2} ${x2} ${y2}`,
          mx: (x1 + x2) / 2,
          my: (y1 + y2) / 2,
          label: w.label,
        });
      }
      setWires(next);
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // Built once as it scrolls in: the panels and rows settle, then what crosses into the home appears.
  useGSAP(
    () => {
      if (STATIC || !wires.length) return;
      const mm = gsap.matchMedia();
      mm.add(MOTION_OK, () => {
        gsap
          .timeline({ scrollTrigger: { trigger: root.current, start: "top 78%", once: true }, defaults: { ease: "power2.out" } })
          .from(".pd-panel", { autoAlpha: 0, y: 12, duration: 0.5, stagger: 0.1 })
          .from(".pd-row", { autoAlpha: 0, x: -6, duration: 0.35, stagger: 0.035 }, "-=0.25")
          .from(".pd-wire, .pd-label", { autoAlpha: 0, duration: 0.45, stagger: 0.12 }, "-=0.05")
          .from(".pd-blocked", { autoAlpha: 0, scale: 0.85, duration: 0.3, ease: "back.out(2)" }, "-=0.1");
      });
    },
    { scope: root, dependencies: [wires.length > 0] },
  );

  return (
    <div
      className="pd"
      ref={root}
      role="img"
      aria-label="Shashwath's private graph holds fever and sick, which are never shared; only a temperature range with priority 3 reaches the shared home graph. Harsh's plan reaches it as an intent."
    >
      <svg className="pd-wires" aria-hidden="true">
        <defs>
          <marker id="pd-arrow" viewBox="0 0 10 10" refX="7" refY="5" markerWidth="7" markerHeight="7" orient="auto">
            <path d="M0 0 L10 5 L0 10 z" fill="var(--green-fill)" />
          </marker>
        </defs>
        {wires.map((w) => (
          <path key={w.label} className="pd-wire flow" d={w.d} markerEnd="url(#pd-arrow)" />
        ))}
      </svg>
      {wires.map((w) => (
        <span key={w.label} className="pd-label" style={{ left: w.mx, top: w.my }}>
          {w.label}
        </span>
      ))}
      <div className="pd-people">
        <Panel name="body:shashwath" tag="private" rows={SHASHWATH} refs={refs} />
        <Panel name="body:harsh" tag="private" rows={HARSH} refs={refs} />
      </div>
      <div className="pd-home">
        <Panel name="home:flat1" tag="shared · read-only" rows={HOME} home refs={refs} />
        <p className="pd-foot">No names. No reasons.</p>
      </div>
    </div>
  );
}

export function Privacy() {
  return (
    <section className="section" id="privacy" aria-labelledby="privacy-title">
      <div className="wrap">
        <SectionHead
          eyebrow="Private by design"
          id="privacy-title"
          title="The room learns what you need. Not why."
          lede="Each person has a private graph in FalkorDB. The shared home graph only ever sees ranges and priorities."
        />
        <m.div className="plate privacy-art" variants={item} {...inView}>
          <PrivacyDiagram />
        </m.div>
        <m.ul className="privacy-points" variants={group} {...inView}>
          {PRIVACY.map((p) => (
            <m.li key={p.title} variants={item}>
              <span className="tick" aria-hidden="true">
                <CheckIcon />
              </span>
              <div>
                <h3>{p.title}</h3>
                <p>{p.text}</p>
              </div>
            </m.li>
          ))}
        </m.ul>
      </div>
    </section>
  );
}
