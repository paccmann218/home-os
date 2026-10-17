export interface FeedEvent {
  at: number;
  agent: string;
  kind: string;
  person?: string;
  text: string;
  data?: any;
}

export interface GNode {
  id: number;
  label: string;
  props: Record<string, any>;
}
export interface GEdge {
  id: number;
  source: number;
  target: number;
  type: string;
  props: Record<string, any>;
}
export interface Graph {
  nodes: GNode[];
  edges: GEdge[];
}

export async function get<T = any>(path: string): Promise<T> {
  const r = await fetch(path);
  if (!r.ok) throw new Error(`${path}: ${r.status}`);
  return r.json();
}

export async function post<T = any>(path: string, body: unknown = {}): Promise<T> {
  const r = await fetch(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  return r.json();
}

/** Live stream from the backend bus (feed, signals, devices, decisions, learning). */
export function subscribe(onMsg: (type: string, data: any) => void): () => void {
  let ws: WebSocket | null = null;
  let closed = false;
  const open = () => {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    ws = new WebSocket(`${proto}://${location.host}/ws/ui`);
    ws.onmessage = (e) => {
      const m = JSON.parse(e.data);
      onMsg(m.type, m.data);
    };
    ws.onclose = () => {
      if (!closed) setTimeout(open, 1500);
    };
  };
  open();
  return () => {
    closed = true;
    ws?.close();
  };
}

/** Person colours follow the entity (categorical slots 1, 2, 3), never their rank. */
export const PERSON_COLORS = ["var(--series-1)", "var(--series-2)", "var(--series-3)"];

export const AGENT_COLOR = (agent: string): string => {
  if (agent.startsWith("guardian")) return "var(--series-3)";
  if (agent.startsWith("planner")) return "var(--series-1)";
  if (agent === "arbiter") return "var(--series-4)";
  if (agent === "evaluator") return "var(--series-5)";
  if (agent.startsWith("coach")) return "var(--series-7)";
  if (agent === "scientist") return "var(--series-2)";
  return "var(--text-muted)";
};

export const fmtTime = (t: number) => new Date(t).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
