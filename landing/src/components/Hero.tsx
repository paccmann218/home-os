import { useEffect, useRef, useState } from "react";
import { AnimatePresence, m } from "framer-motion";
import { APK_URL, EVENING, WORLD_URL, type Agent } from "../content";
import { usePrefersReducedMotion } from "../hooks";
import { EASE, STATIC } from "../motion";
import { gsap, MOTION_OK, useGSAP } from "../gsap";
import { CubeIcon, DownloadIcon } from "./Icons";

const AGENT_HUE: Record<Agent, string> = {
  watch: "hue-teal",
  guardian: "hue-red",
  planner: "hue-violet",
  arbiter: "hue-amber",
  coach: "hue-teal",
  evaluator: "hue-green",
};

const HEADLINE = [
  { text: "A home with a", em: false },
  { text: "nervous system.", em: true },
];

/** One beat of an ECG, repeated: a gentle baseline with a single sharp complex. */
function ecg(width: number, period = 80, mid = 30) {
  let d = `M0 ${mid}`;
  for (let x = 0; x < width; x += period) {
    d += ` L${x + 18} ${mid} L${x + 24} ${mid - 4} L${x + 28} ${mid} L${x + 33} ${mid + 5} L${x + 37} ${mid - 22} L${x + 41} ${mid + 12} L${x + 45} ${mid} L${x + 56} ${mid - 6} L${x + 64} ${mid} L${x + period} ${mid}`;
  }
  return d;
}

/** Text that cross-fades when it changes. */
function Swap({ className, children }: { className: string; children: string }) {
  return (
    <AnimatePresence mode="wait" initial={false}>
      <m.span
        key={children}
        className={className}
        initial={{ opacity: 0, y: 7 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -7 }}
        transition={{ duration: 0.26, ease: EASE }}
      >
        {children}
      </m.span>
    </AnimatePresence>
  );
}

function RoomCard() {
  const reduced = usePrefersReducedMotion();
  const [shown, setShown] = useState(reduced ? EVENING.length : 1);

  useEffect(() => {
    if (reduced) {
      setShown(EVENING.length);
      return;
    }
    const id = window.setInterval(() => setShown((n) => (n >= EVENING.length + 1 ? 1 : n + 1)), 2800);
    return () => window.clearInterval(id);
  }, [reduced]);

  const visible = EVENING.slice(0, Math.min(shown, EVENING.length)).slice(-3);
  const done = shown >= 4;

  return (
    <div className="plate room-card" aria-label="Example: one evening in a HomeOS home">
      <div className="room-head">
        <span className="room-title">Bedroom</span>
        <span className="live">live</span>
      </div>

      <div className="readings">
        <div className="reading">
          <span className="k">Air con</span>
          <span className="v">26 °C</span>
          <span className="s">floor, priority 3</span>
        </div>
        <div className="reading">
          <span className="k">Fan</span>
          <Swap className="v">{done ? "Level 1" : "Off"}</Swap>
          <Swap className="s">{done ? "for Harsh" : "standby"}</Swap>
        </div>
        <div className="reading">
          <span className="k">Pacer</span>
          <Swap className="v">{done ? "5.5/min" : "—"}</Swap>
          <Swap className="s">{done ? "on his watch" : "idle"}</Swap>
        </div>
      </div>

      <div className="pulse-plot">
        <div className="row">
          <span>heart rate · harsh</span>
          <span>1 Hz</span>
        </div>
        <svg viewBox="0 0 320 54" preserveAspectRatio="none" aria-hidden="true">
          <path className="pulse-trace" d={ecg(480)} />
        </svg>
      </div>

      <ol className="events" aria-live="polite">
        <AnimatePresence initial={false} mode="popLayout">
          {visible.map((e) => (
            <m.li
              layout
              key={e.at + e.text}
              className="event"
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10, transition: { duration: 0.22 } }}
              transition={{ duration: 0.45, ease: EASE }}
            >
              <span className="at tnum">{e.at}</span>
              <span className="what">
                <span className={`chip who ${AGENT_HUE[e.agent]}`}>{e.agent}</span>
                {e.text}
              </span>
              <span className="note">{e.note}</span>
            </m.li>
          ))}
        </AnimatePresence>
      </ol>
      <p className="room-caption">A replay of the demo evening in our README.</p>
    </div>
  );
}

export function Hero() {
  const scope = useRef<HTMLElement>(null);

  // On load: the headline rises word by word out of its own line, then the copy and the card settle in.
  useGSAP(
    () => {
      if (STATIC) return;
      const mm = gsap.matchMedia();
      mm.add(MOTION_OK, () => {
        gsap
          .timeline({ defaults: { ease: "power3.out" } })
          .from(".word-inner", { yPercent: 115, duration: 0.9, stagger: 0.06 })
          .from([".eyebrow", ".hero-sub", ".hero-ctas"], { autoAlpha: 0, y: 14, duration: 0.6, stagger: 0.08 }, 0.3)
          .from(".room-card", { autoAlpha: 0, y: 30, duration: 0.95 }, 0.2);
      });
    },
    { scope },
  );

  return (
    <section className="hero" id="top" ref={scope}>
      <div className="wrap hero-grid">
        <div>
          <p className="eyebrow">Graph Hacks 2026 · Built on FalkorDB</p>
          <h1 className="hero-title" style={{ marginTop: "1rem" }}>
            {HEADLINE.map((seg) =>
              seg.text.split(" ").map((w, i) => (
                <span key={seg.text + i}>
                  <span className="word">
                    <span className="word-inner">{seg.em ? <em>{w}</em> : w}</span>
                  </span>{" "}
                </span>
              )),
            )}
          </h1>
          <p className="hero-sub">
            Your watch and ring notice when your body drifts from its own normal. HomeOS adjusts the room, learns
            what actually helps you, and tells you why, while your health stays in a private memory only you can read.
          </p>
          <div className="hero-ctas">
            <a className="btn btn-primary" href={APK_URL}>
              <DownloadIcon />
              Download the Android app
            </a>
            <a className="btn btn-ghost" href={WORLD_URL}>
              <CubeIcon />
              Explore the 3D world
            </a>
          </div>
        </div>
        <RoomCard />
      </div>
    </section>
  );
}
