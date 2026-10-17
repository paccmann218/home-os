// [body graph, Scientist] Procedural memory update: new playbook version SUPERSEDES the old one.
MATCH (old:Playbook {name: $name, status: 'active'})-[:FOR_CONDITION]->(c:Condition)
SET old.status = 'superseded', old.valid_to = $now
CREATE (new:Playbook {name: $name, v: old.v + 1, status: 'active', valid_from: $now, source: 'scientist', note: $note})
CREATE (new)-[:SUPERSEDES]->(old)
CREATE (new)-[:FOR_CONDITION]->(c)
WITH new
UNWIND range(0, size($steps) - 1) AS idx
MATCH (i:Intervention {name: $steps[idx]})
CREATE (new)-[:STEP {i: idx}]->(i)
RETURN new.name AS playbook, new.v AS version
