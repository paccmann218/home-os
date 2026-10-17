// [body graph] Hybrid recall: vector similarity on episode embeddings + shared Band nodes.
MATCH (ep:Episode {id: $episode})
CALL db.idx.vector.queryNodes('Episode', 'embedding', $k, ep.embedding) YIELD node, score
WHERE node.id <> ep.id
OPTIONAL MATCH (ep)-[:IN_STATE]->(b:Band)<-[:IN_STATE]-(node)
RETURN node.id AS id, node.kind AS kind, node.summary AS summary, node.effect AS effect,
       score AS distance, count(b) AS shared_bands, collect(b.key) AS shared
ORDER BY shared_bands DESC, distance ASC
