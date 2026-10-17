// [body graph] Learn, atomically (Evaluator). For every band of the episode, fold the measured
// effect into Evidence(kind, intervention, band) with the HomeOS.welford UDF, then record
// the OUTCOME edge. One query = one atomic write: concurrent learners can't lose updates.
MATCH (a:Action {id: $action})-[:PART_OF]->(:Decision)-[:FOR]->(ep:Episode)-[:IN_STATE]->(b:Band),
      (a)-[:INSTANCE_OF]->(i:Intervention)
MERGE (e:Evidence {kind: ep.kind, intervention: i.name, band: b.key})
ON CREATE SET e.n = 0, e.mean = 0.0, e.m2 = 0.0, e.source = 'learned'
MERGE (e)-[:FOR]->(i)
MERGE (e)-[:WHEN]->(b)
WITH a, e, HomeOS.welford(e.n, e.mean, e.m2, $effect) AS s
SET e.n = s.n, e.mean = s.mean, e.m2 = s.m2, e.updated = $now
WITH a, collect({band: e.band, n: e.n, mean: e.mean}) AS updated
MATCH (sig:Signal {name: $signal})
CREATE (a)-[:OUTCOME {effect: $effect, d_hr: $d_hr, d_rmssd: $d_rmssd, recover_s: $recover_s, at: $now, method: $method}]->(sig)
SET a.evaluated = true
RETURN updated
