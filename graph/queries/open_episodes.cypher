// [body graph] Episodes that have not been closed.
MATCH (p:Person {alias: $alias})-[:HAD]->(ep:Episode)
WHERE ep.status = 'open'
RETURN ep.id AS id, ep.kind AS kind, ep.start AS start, ep.summary AS summary
ORDER BY ep.start DESC
LIMIT $limit
