// [body graph] Active condition and what it implies, up to three hops.
MATCH (p:Person {alias: $alias})-[hc:HAS_CONDITION]->(c0:Condition)
WHERE hc.valid_from <= $now AND hc.valid_to > $now
OPTIONAL MATCH path = (c0)-[:IMPLIES*0..3]->(c:Condition)
RETURN c0.kind AS from, [n IN nodes(path) | n.kind] AS via, c.kind AS to
ORDER BY from, to
