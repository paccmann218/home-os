// [body graph] Active playbook(s) for a condition, following IMPLIES (fever -> sick -> sick_day).
MATCH (c0:Condition {kind: $condition})-[:IMPLIES*0..3]->(c:Condition)<-[:FOR_CONDITION]-(pb:Playbook)
WHERE pb.status = 'active'
MATCH (pb)-[s:STEP]->(i:Intervention)
WITH pb, c, s, i ORDER BY s.i
OPTIONAL MATCH (pb)-[:SUPERSEDES*1..5]->(older:Playbook)
RETURN pb.name AS playbook, pb.v AS version, c.kind AS condition, collect(DISTINCT i.label) AS steps,
       count(DISTINCT older) AS earlier_versions, pb.valid_from AS since, pb.note AS note
