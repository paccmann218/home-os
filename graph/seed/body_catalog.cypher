// Seed one person's private graph: themselves, the variables/signals they reason about,
// the device stubs explanations end at, and the intervention catalogue.
MERGE (p:Person {alias: $alias})
MERGE (:DeviceRef:Lever {id: 'watch'})
WITH DISTINCT p
UNWIND $vars AS v
MERGE (var:Var {name: v})
WITH DISTINCT p
UNWIND $signals AS s
MERGE (:Signal {name: s})
WITH DISTINCT p
UNWIND $controls AS c
MATCH (v:Var {name: c.var})
MERGE (d:DeviceRef:Lever {id: c.device})
MERGE (v)-[r:CONTROLLED_BY]->(d) SET r.neglogp = 0.0
WITH DISTINCT p
UNWIND $interventions AS i
MERGE (n:Intervention {name: i.name})
SET n.device = i.device, n.var = i.var, n.label = i.label, n.params_json = i.params_json
