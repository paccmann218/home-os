import { m } from "framer-motion";
import { STEPS } from "../content";
import { group, inView, item } from "../motion";
import { STEP_ICONS } from "./AppIcons";
import { SectionHead } from "./SectionHead";

/** A connected stepper: icon nodes on one rail, the step under each. Vertical on narrow screens. */
export function HowItWorks() {
  return (
    <section className="section" id="how" aria-labelledby="how-title">
      <div className="wrap">
        <SectionHead
          eyebrow="How it works"
          id="how-title"
          title="It reads your body, not a thermostat schedule."
          lede="Smart homes run fixed rules. HomeOS starts from how you are right now, then checks whether what it did actually helped."
        />
        <m.ol className="stepper" variants={group} {...inView}>
          {STEPS.map((s) => {
            const Icon = STEP_ICONS[s.title];
            return (
              <m.li key={s.n} className={`stepper-item hue-${s.hue}`} variants={item}>
                <div className="stepper-track" aria-hidden="true">
                  <span className="stepper-node">
                    <Icon size={22} weight="duotone" />
                  </span>
                  <span className="stepper-rail" />
                </div>
                <div className="stepper-body">
                  <p className="stepper-index">Step {s.n}</p>
                  <h3>{s.title}</h3>
                  <p>{s.text}</p>
                </div>
              </m.li>
            );
          })}
        </m.ol>
      </div>
    </section>
  );
}
