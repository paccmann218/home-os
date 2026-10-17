// [home graph, write API] Atomic claim. FalkorDB runs writes to one graph one at a time and
// each query is atomic, so two workers can never claim the same task.
MATCH (t:Task {status: 'open'})-[:ASSIGNED_TO]->(a:Agent {id: $agent})
WHERE t.due_at <= $now
WITH t, a ORDER BY t.created ASC LIMIT 1
SET t.status = 'claimed', t.claimed_at = $now, t.lease_until = $now + $lease_ms, t.worker = $worker
CREATE (t)-[:CLAIMED_BY {at: $now, worker: $worker}]->(a)
RETURN t.id AS id, t.kind AS kind, t.payload_json AS payload, t.owner AS owner
