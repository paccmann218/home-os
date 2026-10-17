// [body graph, Scientist] Re-learn every band's weight from history: Laplace-smoothed
// P(bad night | band) -> neglogp on the band and its IN_STATE edges.
MATCH (ep:Episode)-[:IN_STATE]->(b:Band)
WHERE ep.kind = $kind AND ep.quality IS NOT NULL
WITH b, count(ep) AS total, sum(CASE WHEN ep.quality = 'bad' THEN 1 ELSE 0 END) AS bad
WITH b, total, bad, (toFloat(bad) + 1.0) / (toFloat(total) + 2.0) AS p
SET b.p_bad = p, b.n = total, b.neglogp = -log(p)
WITH b
MATCH (:Episode)-[r:IN_STATE]->(b)
SET r.neglogp = b.neglogp
RETURN count(DISTINCT b) AS bands
