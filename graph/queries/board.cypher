// [home graph] The coordination board for the dashboard: recent tasks with their handoff chain.
MATCH (t:Task)-[:ASSIGNED_TO]->(a:Agent)
WHERE t.created >= $since
OPTIONAL MATCH (t)-[:HANDOFF_FROM]->(prev:Task)
RETURN t.id AS id, t.kind AS kind, t.status AS status, a.id AS agent, t.owner AS owner, t.created AS created,
       prev.id AS from, t.reclaimed AS reclaimed
ORDER BY t.created DESC
LIMIT $limit
