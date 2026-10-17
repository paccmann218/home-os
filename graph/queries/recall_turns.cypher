// [body graph] Track 2 persistent conversation history: semantic recall of past turns,
// expanded to what each turn was ABOUT (episodes, interventions, conditions).
CALL db.idx.vector.queryNodes('Turn', 'embedding', $k, vecf32($vec)) YIELD node, score
MATCH (s:Session)-[:HAS_TURN]->(node)
OPTIONAL MATCH (node)-[:ABOUT]->(x)
RETURN node.text AS text, node.role AS role, node.at AS at, s.id AS session, score AS distance,
       collect(DISTINCT labels(x)[0] + ':' + coalesce(x.kind, x.name, x.id, '')) AS about
ORDER BY distance ASC
