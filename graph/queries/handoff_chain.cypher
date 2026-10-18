// [home graph] Recent task handoffs between agents.
MATCH (t:Task)-[:HANDOFF_FROM]->(prev:Task)
WHERE t.created >= $since OR prev.created >= $since
RETURN t.id AS task, t.kind AS kind, t.status AS status, prev.id AS from_task, prev.kind AS from_kind
ORDER BY t.created DESC
LIMIT $limit
