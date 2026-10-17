/**
 * Agents coordinate ONLY through the graph: each one polls the Task board in the home graph,
 * claims work atomically, and hands follow-up work to the next agent as a new Task linked by
 * HANDOFF_FROM. A crashed worker's claim expires and the task is reclaimed.
 */
import { claimTask, completeTask, reclaimTasks } from "../writeapi/index.js";
import { bus } from "../bus.js";

export interface ClaimedTask {
  id: string;
  kind: string;
  owner: string | null;
  payload: any;
}

const stoppers: (() => void)[] = [];

export function startWorker(agentId: string, handler: (t: ClaimedTask) => Promise<unknown>, intervalMs = 400): void {
  const worker = `${agentId}#${process.pid}`;
  let busy = false;
  const h = setInterval(async () => {
    if (busy) return;
    busy = true;
    try {
      const t = await claimTask(agentId, worker);
      if (t) {
        try {
          const result = await handler(t);
          await completeTask(t.id, worker, result ?? {});
        } catch (e) {
          console.error(`[${agentId}] task ${t.kind} failed:`, e);
          bus.feed({ agent: agentId, kind: "error", text: `${t.kind} failed: ${(e as Error).message}` });
          await completeTask(t.id, worker, { error: (e as Error).message }, "failed");
        }
      }
    } catch (e) {
      console.error(`[${agentId}] poll failed:`, (e as Error).message);
    } finally {
      busy = false;
    }
  }, intervalMs);
  stoppers.push(() => clearInterval(h));
}

export function startReclaimer(intervalMs = 5000): void {
  const h = setInterval(() => {
    reclaimTasks()
      .then((n) => n && bus.feed({ agent: "home", kind: "reclaim", text: `Reclaimed ${n} task(s) from a stalled agent` }))
      .catch(() => undefined);
  }, intervalMs);
  stoppers.push(() => clearInterval(h));
}

export function stopWorkers(): void {
  while (stoppers.length) stoppers.pop()!();
}
