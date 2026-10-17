/** In-process event bus: agents publish what they do; the dashboard and watch sockets subscribe. */
import { EventEmitter } from "node:events";

export interface FeedEvent {
  at: number;
  agent: string;
  kind: string;
  person?: string;
  text: string;
  data?: unknown;
}

class Bus extends EventEmitter {
  feed(e: Omit<FeedEvent, "at">): void {
    const ev = { at: Date.now(), ...e };
    this.emit("feed", ev);
  }
}

export const bus = new Bus();
bus.setMaxListeners(100);

let seq = 0;
export const newId = (prefix: string) => `${prefix}-${Date.now().toString(36)}-${(seq++).toString(36)}`;
