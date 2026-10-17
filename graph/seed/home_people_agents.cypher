// Seed: household members (alias only - nothing private lives here) and the agents.
UNWIND $people AS alias
MERGE (p:Person {alias: alias})
MERGE (a:Agent {id: 'planner:' + alias}) SET a.kind = 'planner', a.owner = alias
MERGE (g:Agent {id: 'guardian:' + alias}) SET g.kind = 'guardian', g.owner = alias
MERGE (c:Agent {id: 'coach:' + alias}) SET c.kind = 'coach', c.owner = alias
WITH 1 AS _
UNWIND ['arbiter', 'evaluator', 'scientist'] AS k
MERGE (a:Agent {id: k}) SET a.kind = k, a.owner = 'home'
