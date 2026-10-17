// [body graph] Provenance: a Decision records what was considered, what was chosen, why
// others were rejected, and which shared constraints bounded it. This is what "why?" walks.
MATCH (ep:Episode {id: $episode})
CREATE (d:Decision {id: $id, at: $now, by: $by, rationale: $rationale, arbitration: $arbitration})-[:FOR]->(ep)
WITH d
UNWIND $considered AS c
MATCH (i:Intervention {name: c.name})
CREATE (d)-[:CONSIDERED {score: c.score, chosen: c.chosen, rejected_because: c.rejected_because}]->(i)
FOREACH (_ IN CASE WHEN c.chosen THEN [1] ELSE [] END | CREATE (d)-[:CHOSE {score: c.score}]->(i))
WITH DISTINCT d
MATCH (d)-[:FOR]->(ep:Episode)-[:IN_STATE]->(b:Band)<-[:WHEN]-(e:Evidence)-[:FOR]->(i:Intervention)<-[:CHOSE]-(d)
WHERE e.kind = ep.kind
MERGE (d)-[:USED]->(e)
RETURN d.id AS id, count(e) AS evidence_used
