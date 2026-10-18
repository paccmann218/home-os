import { m } from "framer-motion";
import { DEVICES, ROOM_DEVICES } from "../content";
import { group, inView, item, lift } from "../motion";
import { BoardArt, RingArt, WatchArt } from "./art/DeviceArt";
import { SectionHead } from "./SectionHead";

const ART: Record<string, () => JSX.Element> = { watch: WatchArt, ring: RingArt, board: BoardArt };

export function Hardware() {
  return (
    <section className="section" id="hardware" aria-labelledby="hw-title">
      <div className="wrap">
        <SectionHead
          eyebrow="Hardware"
          id="hw-title"
          title={
            <>
              Two things you wear.
              <br />
              One board on the shelf.
            </>
          }
          lede="No new gadgets to buy into. HomeOS listens to the watch and ring you already have, and to a small room node anyone can flash, all on your own network."
        />

        <m.div className="devices" variants={group} {...inView}>
          {DEVICES.map((d) => {
            const Art = ART[d.id];
            return (
              <m.article key={d.id} className={`plate device hue-${d.hue}`} variants={item} {...lift}>
                <div className="device-art">
                  <Art />
                </div>
                <div className="device-body">
                  <div className="device-name">
                    <h3>{d.name}</h3>
                    <span className={`chip hue-${d.hue}`}>{d.kind}</span>
                  </div>
                  <p className="device-line">{d.line}</p>
                </div>
                <div className="specs">
                  <div>
                    <p className="spec-title">Senses</p>
                    <ul className="spec-list">
                      {d.senses.map((s) => (
                        <li key={s}>{s}</li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <p className="spec-title">Does</p>
                    <ul className="spec-list">
                      {d.does.map((s) => (
                        <li key={s}>{s}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              </m.article>
            );
          })}
        </m.div>

        <m.div className="room-devices" variants={group} {...inView}>
          <m.p className="label" variants={item}>
            And the room
          </m.p>
          {ROOM_DEVICES.map((r) => (
            <m.div key={r.name} className="room-device" variants={item}>
              <b>{r.name}</b>
              <span>{r.text}</span>
            </m.div>
          ))}
        </m.div>
      </div>
    </section>
  );
}
