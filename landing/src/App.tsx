import { LazyMotion, MotionConfig } from "framer-motion";
import { Agents } from "./components/Agents";
import { AppSection } from "./components/AppSection";
import { Footer } from "./components/Footer";
import { Hardware } from "./components/Hardware";
import { Hero } from "./components/Hero";
import { HowItWorks } from "./components/HowItWorks";
import { Learning } from "./components/Learning";
import { Nav } from "./components/Nav";
import { Privacy } from "./components/Privacy";

const loadMotionFeatures = () => import("./motion-features").then((mod) => mod.default);

export default function App() {
  return (
    // "user": Framer drops movement for visitors who prefer reduced motion. GSAP checks the same setting.
    // LazyMotion keeps the first download small; the animation features arrive right after.
    <LazyMotion features={loadMotionFeatures} strict>
    <MotionConfig reducedMotion="user">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <Nav />
      <main id="main">
        <Hero />
        <HowItWorks />
        <Hardware />
        <Agents />
        <Privacy />
        <Learning />
        <AppSection />
      </main>
      <Footer />
    </MotionConfig>
    </LazyMotion>
  );
}
