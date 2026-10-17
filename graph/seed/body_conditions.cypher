// Seed default condition knowledge: which conditions imply others and how each shifts comfort.
// Users change this by voice ("I hate the fan when I'm sick"), so it is data, not code.
UNWIND $conditions AS c
MERGE (cond:Condition {kind: c.kind}) SET cond.label = c.label, cond.priority = c.priority
WITH c, cond
UNWIND c.shifts AS s
MATCH (v:Var {name: s.var})
MERGE (cond)-[sh:SHIFTS]->(v) SET sh.min = s.min, sh.max = s.max
