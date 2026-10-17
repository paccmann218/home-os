// Seed: what each device controls and what each variable does to the body (literature priors).
UNWIND $vars AS v MERGE (:Var {name: v})
WITH 1 AS _
UNWIND $signals AS s MERGE (:Signal {name: s})
WITH 1 AS _
UNWIND $controls AS c
MATCH (d:Device {id: c.device}), (v:Var {name: c.var})
MERGE (d)-[k:CONTROLS]->(v) SET k.strength = c.strength
WITH 1 AS _
UNWIND $affects AS a
MATCH (v:Var {name: a.var}), (s:Signal {name: a.signal})
MERGE (v)-[e:AFFECTS]->(s) SET e.sign = a.sign, e.prior = a.prior, e.source_url = a.source_url
