// [body graph] This person's own nights, newest first.
MATCH (p:Person {alias: $alias})-[:HAD]->(ep:Episode {kind: 'sleep'})
RETURN ep.id AS id, ep.start AS start, ep.end AS end, ep.quality AS quality, ep.summary AS summary
ORDER BY ep.start DESC
LIMIT $limit
