import { m } from "framer-motion";
import { APK_URL, APP_FEATURES, WORLD_URL } from "../content";
import { EASE, group, inView, item, STATIC } from "../motion";
import { CubeIcon, DownloadIcon } from "./Icons";

const CHIPS = ["Sick", "Fever", "Headache", "Stressed", "Period", "Workout"];

/** The phone, drawn in the app's own visual language. Copy follows the app's real screens. */
function Phone() {
  return (
    <div className="phone" role="img" aria-label="The HomeOS Bridge app: a check-in card, the room's devices, and an answer from the app">
      <div className="phone-screen">
        <span className="phone-notch" aria-hidden="true" />
        <div className="phone-top">
          <span className="phone-word">HomeOS</span>
          <span className="phone-avatar">shashwath</span>
        </div>
        <div className="p-block">
          <h4>How are you?</h4>
          <div className="p-chips">
            {CHIPS.map((c) => (
              <span key={c} className={`p-chip${c === "Sick" ? " on" : ""}`}>
                {c}
              </span>
            ))}
          </div>
        </div>
        <div className="p-block">
          <h4>Bedroom</h4>
          <div className="p-room">
            <div>
              <b>26 °C</b>air con
            </div>
            <div>
              <b>Off</b>fan
            </div>
            <div>
              <b>30%</b>warm light
            </div>
          </div>
        </div>
        <div className="p-block p-why">
          <p className="q">What helps me when I'm sick?</p>
          <p className="a">AC 2 °C warmer, fan off, and warm dim light help when you're sick.</p>
          <div className="tags">
            <span>playbook: sick</span>
            <span>from your graph</span>
          </div>
        </div>
        <div className="p-tabs" aria-hidden="true">
          <span>Home</span>
          <span className="on">Check in</span>
          <span>Ask</span>
          <span>Memory</span>
        </div>
      </div>
    </div>
  );
}

export function AppSection() {
  return (
    <section className="section" id="app" aria-labelledby="app-title">
      <div className="wrap app-grid">
        <m.div
          {...(STATIC
            ? {}
            : {
                initial: { opacity: 0, y: 32, rotate: -3 },
                whileInView: { opacity: 1, y: 0, rotate: 0 },
                viewport: { once: true, margin: "0px 0px -12% 0px" },
                transition: { duration: 0.9, ease: EASE },
              })}
        >
          <Phone />
        </m.div>
        <m.div variants={group} {...inView}>
          <m.p className="eyebrow" variants={item}>
            The phone app
          </m.p>
          <m.h2 id="app-title" style={{ fontSize: "var(--step-3)", marginTop: "1rem" }} variants={item}>
            HomeOS Bridge: the remote, the memory and the “why”.
          </m.h2>
          <m.div className="app-features" variants={group}>
            {APP_FEATURES.map((f) => (
              <m.div key={f.title} variants={item}>
                <h3>{f.title}</h3>
                <p>{f.text}</p>
              </m.div>
            ))}
          </m.div>
          <m.div className="plate download" variants={item}>
            <div className="download-text">
              <b>HomeOS Bridge for Android</b>
              <span>APK · Android 12 or newer · uses Health Connect</span>
            </div>
            <div className="download-actions">
              <a className="btn btn-primary" href={APK_URL}>
                <DownloadIcon />
                Download APK
              </a>
              <a className="btn btn-ghost" href={WORLD_URL}>
                <CubeIcon />
                Open the 3D world
              </a>
            </div>
          </m.div>
        </m.div>
      </div>
    </section>
  );
}
