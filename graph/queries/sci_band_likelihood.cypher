// [body graph, Scientist] Re-learn every band from this person's history: Laplace-smoothed
// P(bad | band), lift over their base rate of bad episodes, and neglogp = -ln P(bad | band) on the
// band's IN_STATE edges (usual bands get +5 so cause chains run through deviations, not routine).
MATCH (all:Episode) WHERE all.kind = $kind AND all.quality IS NOT NULL
WITH toFloat(sum(CASE WHEN all.quality = 'bad' THEN 1 ELSE 0 END)) / count(all) AS base
MATCH (ep:Episode)-[:IN_STATE]->(b:Band)
WHERE ep.kind = $kind AND ep.quality IS NOT NULL AND b.var <> 'kind'
WITH base, b, count(ep) AS total, sum(CASE WHEN ep.quality = 'bad' THEN 1 ELSE 0 END) AS bad
WITH base, b, total, bad, (toFloat(bad) + 1.0) / (toFloat(total) + 2.0) AS p
SET b.p_bad = p, b.n = total, b.n_bad = bad, b.base_rate = base, b.lift = p / base,
    b.neglogp = CASE WHEN b.notable THEN -log(p) ELSE 5.0 - log(p) END
WITH b
MATCH (:Episode)-[r:IN_STATE]->(b)
SET r.neglogp = b.neglogp
RETURN count(DISTINCT b) AS bands
