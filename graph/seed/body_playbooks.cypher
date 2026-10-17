// Seed version-1 playbooks (procedural memory). The Scientist writes later versions.
UNWIND $playbooks AS pb
MATCH (c:Condition {kind: pb.condition})
MERGE (p:Playbook {name: pb.name, v: 1})
ON CREATE SET p.status = 'active', p.valid_from = $now, p.source = 'default'
MERGE (p)-[:FOR_CONDITION]->(c)
WITH p, pb
UNWIND range(0, size(pb.steps) - 1) AS idx
MATCH (i:Intervention {name: pb.steps[idx]})
MERGE (p)-[s:STEP {i: idx}]->(i)
