// Seed weak evidence priors (n = 1) so the planner never faces an empty policy.
// Each Evidence node is "how well intervention I worked for episodes of kind K in band B".
UNWIND $priors AS e
MERGE (b:Band {key: 'kind:' + e.kind}) SET b.var = 'kind', b.level = e.kind
WITH e, b
MATCH (i:Intervention {name: e.intervention})
MERGE (ev:Evidence {kind: e.kind, intervention: e.intervention, band: b.key})
ON CREATE SET ev.n = 1, ev.mean = e.mean, ev.m2 = 0.0, ev.source = 'literature_prior'
MERGE (ev)-[:FOR]->(i)
MERGE (ev)-[:WHEN]->(b)
