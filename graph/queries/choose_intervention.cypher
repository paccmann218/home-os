// [body graph] Track 1 "actions based on graph results": rank interventions for an episode.
// Evidence that matches MORE of the episode's context bands counts; each candidate gets a
// Thompson-sampling draw (HomeOS.thompson UDF) so uncertain options still get explored.
// The person's DISLIKES (optionally scoped to a condition) and active playbooks feed in.
MATCH (ep:Episode {id: $episode})-[:IN_STATE]->(b:Band)<-[:WHEN]-(e:Evidence)-[:FOR]->(i:Intervention)
WHERE e.kind = ep.kind
WITH ep, i, count(b) AS matched, sum(e.n) AS n, sum(e.n * e.mean) / sum(e.n) AS mean, sum(e.m2) AS m2, collect(b.key) AS bands
MATCH (p:Person {alias: $alias})
OPTIONAL MATCH (p)-[dl:DISLIKES]->(i) WHERE dl.valid_from <= $now AND dl.valid_to > $now
OPTIONAL MATCH (p)-[hw:HAS_CONDITION]->(cw:Condition) WHERE hw.valid_to > $now AND cw.kind = dl.when_condition
WITH ep, p, i, matched, n, mean, m2, bands,
     sum(CASE WHEN dl IS NOT NULL AND (dl.when_condition IS NULL OR cw IS NOT NULL) THEN 1 ELSE 0 END) AS dislikes
OPTIONAL MATCH (p)-[hc:HAS_CONDITION]->(:Condition)-[:IMPLIES*0..3]->(:Condition)<-[:FOR_CONDITION]-(pb:Playbook {status: 'active'})-[:STEP]->(i)
WHERE hc.valid_from <= $now AND hc.valid_to > $now
WITH i, matched, n, mean, m2, bands, dislikes, collect(DISTINCT pb.name + ' v' + toString(pb.v)) AS playbooks
RETURN i.name AS intervention, i.label AS label, i.device AS device, i.var AS var, i.params_json AS params,
       matched, n, mean, HomeOS.thompson(n, mean, m2) + 0.15 * size(playbooks) AS draw,
       dislikes > 0 AS disliked, playbooks, bands
ORDER BY disliked ASC, draw DESC
