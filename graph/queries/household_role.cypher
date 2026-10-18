// [body graph] Role stored privately.
MATCH (p:Person {alias: $alias})-[:HAS_ROLE]->(r:Role)
RETURN r.id AS id, r.name AS name
