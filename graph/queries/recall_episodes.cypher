// [body graph] Semantic recall over episodes, with what was done and how well it worked.
CALL db.idx.vector.queryNodes('Episode', 'embedding', $k, vecf32($vec)) YIELD node, score
OPTIONAL MATCH (d:Decision)-[:FOR]->(node)
OPTIONAL MATCH (d)-[:CHOSE]->(i:Intervention)
OPTIONAL MATCH (a:Action)-[:PART_OF]->(d)
OPTIONAL MATCH (a)-[o:OUTCOME]->()
RETURN node.id AS id, node.kind AS kind, node.summary AS summary, node.start AS start, score AS distance,
       collect(DISTINCT i.name) AS did, avg(o.effect) AS effect
ORDER BY distance ASC
