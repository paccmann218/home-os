import { m } from "framer-motion";
import { AGENTS, HANDOFF } from "../content";
import { group, inView, item, lift } from "../motion";
import { AGENT_ICONS } from "./AppIcons";
import { SectionHead } from "./SectionHead";

export function Agents() {
  return (
    <section className="section" id="agents" aria-labelledby="agents-title">
      <div className="wrap">
        <SectionHead
          eyebrow="Agents"
          id="agents-title"
          title="Six agents. They only talk through the graph."
          lede="No agent calls another. Work moves through the graph as tasks with atomic claims and handoffs, so every step leaves a trail."
        />

        <m.div className="handoff" aria-label="A typical handoff chain" variants={group} {...inView}>
          {HANDOFF.map((h, i) => (
            <m.span key={`${h}-${i}`} variants={item} style={{ display: "inline-flex", alignItems: "center", gap: "var(--space-2)" }}>
              {i > 0 && (
                <span className="arrow" aria-hidden="true">
                  →
                </span>
              )}
              <span className="hop">{h}</span>
            </m.span>
          ))}
          <m.span className="via" variants={item}>
            via Task nodes in FalkorDB
          </m.span>
        </m.div>

        <m.div className="agents" variants={group} {...inView}>
          {AGENTS.map((a) => {
            const Icon = AGENT_ICONS[a.name];
            return (
            <m.article key={a.name} className={`plate agent hue-${a.hue}`} variants={item} {...lift}>
              <div className="agent-top">
                <span className="agent-glyph" aria-hidden="true">
                  <Icon size={22} weight="duotone" />
                </span>
                <span className="chip">{a.kind}</span>
              </div>
              <h3>{a.name}</h3>
              <p>{a.text}</p>
              <dl>
                <dt>reads</dt>
                <dd>{a.reads}</dd>
                <dt>writes</dt>
                <dd>{a.writes}</dd>
              </dl>
            </m.article>
            );
          })}
        </m.div>
      </div>
    </section>
  );
}
