MATCH (p:Person {alias: $alias})-[b:HAS_BASELINE]->(s:Signal)
WHERE b.valid_from <= $now AND b.valid_to > $now
RETURN s.name AS signal, b.mean AS mean, b.sd AS sd, b.n AS n
