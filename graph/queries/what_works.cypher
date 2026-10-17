// [body graph] Procedural + episodic memory: what has worked best for this person for a kind of
// episode, pooled across bands, with the active playbook for it.
MATCH (e:Evidence)-[:FOR]->(i:Intervention) WHERE e.kind = $kind
WITH i, sum(e.n) AS n, sum(e.n * e.mean) / sum(e.n) AS mean,
     sum(CASE WHEN e.source = 'learned' THEN e.n ELSE 0 END) AS learned_n
RETURN i.name AS intervention, i.label AS label, n, learned_n, mean
ORDER BY mean DESC
LIMIT $limit
