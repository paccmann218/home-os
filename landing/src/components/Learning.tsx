import { m } from "framer-motion";
import { ABLATION, FINDINGS, STATS } from "../content";
import { group, inView, item } from "../motion";
import { CountUp } from "./CountUp";
import { SectionHead } from "./SectionHead";

export function Learning() {
  return (
    <section className="section" id="learning" aria-labelledby="learn-title">
      <div className="wrap">
        <SectionHead
          eyebrow="Learns what helps you"
          id="learn-title"
          title="Evidence about your body, not people in general."
          lede="Every action becomes an experiment. Each night a Scientist agent runs graph algorithms over your history, and updates your playbook when your results disagree with the textbook."
        />

        <m.div className="learn-grid" variants={group} {...inView}>
          <m.div className="plate findings" variants={item}>
            <div className="findings-head">
              <h3>What ruins p01's nights</h3>
              <span className="chip hue-violet">150 real nights</span>
            </div>
            {FINDINGS.map((f) => (
              <div key={f.algo} className="finding">
                <span className="chip hue-green mono">{f.algo}</span>
                <p>{f.text}</p>
              </div>
            ))}
            <p className="dataset-note">
              p01 is one participant of the public PMData dataset (Fitbit, CC BY 4.0), loaded as a clearly labelled
              household member who is never in the home. Nothing is synthesised.
            </p>
          </m.div>

          <m.figure className="plate shot" style={{ margin: 0 }} variants={item}>
            <div className="shot-bar" aria-hidden="true">
              <i />
              <i />
              <i />
              <span>localhost:3001 · HomeOS dashboard</span>
            </div>
            <img
              src="/img/dashboard.webp"
              alt="The HomeOS dashboard: people and their private graphs on the left, p01's graph of nights and context bands in the middle, and the Scientist's PageRank, betweenness, label-propagation and shortest-path findings on the right."
              width={1920}
              height={1080}
            />
            <figcaption>The live dashboard. Every dot is a node in FalkorDB.</figcaption>
          </m.figure>
        </m.div>

        <m.div className="stats" variants={group} {...inView}>
          {STATS.map((s) => (
            <m.div key={s.label} className="stat" variants={item}>
              <span className="v">
                <CountUp value={s.value} />
                {s.unit && <span className="u">{s.unit}</span>}
              </span>
              <span className="l">{s.label}</span>
            </m.div>
          ))}
        </m.div>

        <m.div className="plate ablation" variants={item} {...inView}>
          <h3>Take the graph away, and it forgets everyone.</h3>
          <p>The same two-person evening, run with FalkorDB and with fixed rules instead.</p>
          <table className="ablation-table">
            <thead>
              <tr>
                <th scope="col">Moment</th>
                <th scope="col">With FalkorDB</th>
                <th scope="col">Without</th>
              </tr>
            </thead>
            <tbody>
              {ABLATION.map((r) => (
                <tr key={r.step}>
                  <td>{r.step}</td>
                  <td className="on">{r.on}</td>
                  <td className="off">{r.off}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </m.div>
      </div>
    </section>
  );
}
