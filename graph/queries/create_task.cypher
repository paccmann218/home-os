// [home graph, write API] Track 2 shared state + handoffs: agents give each other work as Task
// nodes. HANDOFF_FROM links each task to the one that spawned it, so the chain is inspectable.
MATCH (a:Agent {id: $agent})
CREATE (t:Task {id: $id, kind: $kind, status: 'open', created: $now, due_at: $due_at, payload_json: $payload, owner: $owner})
CREATE (t)-[:ASSIGNED_TO]->(a)
WITH t
OPTIONAL MATCH (prev:Task {id: $from})
FOREACH (p IN CASE WHEN prev IS NULL THEN [] ELSE [prev] END | CREATE (t)-[:HANDOFF_FROM]->(p))
RETURN t.id AS id
