// [home graph, write API] Shared provenance of a room decision: which intents it resolved,
// which (anonymous) constraints bounded it, and what it applied to which device.
CREATE (a:Arbitration {id: $id, at: $now, summary: $summary, room: $room})
WITH a
UNWIND $intents AS iid
MATCH (i:Intent {id: iid})
SET i.status = 'resolved'
CREATE (a)-[:RESOLVED]->(i)
WITH DISTINCT a
UNWIND (CASE WHEN size($constraints) = 0 THEN [null] ELSE $constraints END) AS cid
OPTIONAL MATCH (c:Constraint {id: cid})
FOREACH (x IN CASE WHEN c IS NULL THEN [] ELSE [c] END | MERGE (a)-[:BOUNDED_BY]->(x))
WITH DISTINCT a
UNWIND $applied AS ap
MATCH (d:Device {id: ap.device})
CREATE (a)-[:APPLIED {setting: ap.setting, at: $now}]->(d)
RETURN a.id AS id
