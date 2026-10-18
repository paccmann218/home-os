// [body graph] Calendar events still ahead of now, soonest first.
MATCH (p:Person {alias: $alias})-[:HAS_EVENT]->(e:Event)
WHERE e.at >= $now
RETURN e.id AS id, e.title AS title, e.at AS at, e.source AS source
ORDER BY e.at ASC
LIMIT $limit
