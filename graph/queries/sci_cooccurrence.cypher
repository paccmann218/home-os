// [body graph, Scientist] Bands that show up together on bad nights get CO_OCCURS edges,
// weighted by how often. PageRank over this graph finds the dominant drivers.
MATCH (ep:Episode {quality: 'bad'})-[:IN_STATE]->(b1:Band), (ep)-[:IN_STATE]->(b2:Band)
WHERE ep.kind = $kind AND b1.key <> b2.key AND NOT b1.var = 'kind' AND NOT b2.var = 'kind'
WITH b1, b2, count(ep) AS n
MERGE (b1)-[c:CO_OCCURS]->(b2)
SET c.n = n
RETURN count(c) AS edges
