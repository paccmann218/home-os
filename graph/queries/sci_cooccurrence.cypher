// [body graph, Scientist] Deviations that show up TOGETHER on bad nights (at least twice) get
// CO_OCCURS edges. PageRank over this graph finds the dominant drivers of this person's bad nights.
OPTIONAL MATCH ()-[old:CO_OCCURS]->()
DELETE old
WITH count(*) AS cleared
MATCH (ep:Episode)-[:IN_STATE]->(b1:Notable), (ep)-[:IN_STATE]->(b2:Notable)
WHERE ep.kind = $kind AND ep.quality = 'bad' AND b1.key <> b2.key
WITH b1, b2, count(ep) AS n
WHERE n >= 2
MERGE (b1)-[c:CO_OCCURS]->(b2)
SET c.n = n
RETURN count(c) AS edges
