/**
 * Phosphor icons (MIT, phosphoricons.com), one per step and per agent.
 * Duotone keeps them distinctive without needing a second colour.
 */
import {
  BellRingingIcon,
  ChartLineUpIcon,
  FlaskIcon,
  HeartbeatIcon,
  LightbulbIcon,
  LightningIcon,
  MicrophoneIcon,
  PathIcon,
  ScalesIcon,
  WaveformIcon,
  type Icon,
} from "@phosphor-icons/react";

export const STEP_ICONS: Record<string, Icon> = {
  Sense: WaveformIcon,
  Notice: BellRingingIcon,
  Decide: PathIcon,
  Act: LightningIcon,
  "Learn & explain": LightbulbIcon,
};

export const AGENT_ICONS: Record<string, Icon> = {
  Guardian: HeartbeatIcon,
  Planner: PathIcon,
  Arbiter: ScalesIcon,
  Evaluator: ChartLineUpIcon,
  Coach: MicrophoneIcon,
  Scientist: FlaskIcon,
};
