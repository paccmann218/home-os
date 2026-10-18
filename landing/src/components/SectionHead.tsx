import { m } from "framer-motion";
import type { ReactNode } from "react";
import { group, inView, item } from "../motion";

export function SectionHead({ eyebrow, title, id, lede }: { eyebrow: string; title: ReactNode; id: string; lede: ReactNode }) {
  return (
    <m.header className="section-head" variants={group} {...inView}>
      <m.p className="eyebrow" variants={item}>
        {eyebrow}
      </m.p>
      <m.h2 id={id} variants={item}>
        {title}
      </m.h2>
      <m.p className="lede" variants={item}>
        {lede}
      </m.p>
    </m.header>
  );
}
