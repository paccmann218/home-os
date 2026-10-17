// [body graph, owner's view] Track 1 explainable results: walk a Decision's provenance.
// Returns the episode context, what was chosen, the evidence behind it, what was rejected
// and why, and - for the owner only - the private reasons behind their own constraints.
MATCH (d:Decision {id: $decision})-[:FOR]->(ep:Episode)
OPTIONAL MATCH (ep)-[:IN_STATE]->(b:Band)
WITH d, ep, collect(DISTINCT b.key) AS context
OPTIONAL MATCH (d)-[ch:CHOSE]->(i:Intervention)
WITH d, ep, context, collect(DISTINCT {name: i.name, label: i.label, score: ch.score}) AS chosen
OPTIONAL MATCH (d)-[:USED]->(e:Evidence)
WITH d, ep, context, chosen, collect(DISTINCT {intervention: e.intervention, band: e.band, n: e.n, mean: e.mean, source: e.source}) AS evidence
OPTIONAL MATCH (d)-[rj:CONSIDERED]->(alt:Intervention) WHERE rj.rejected_because IS NOT NULL
WITH d, ep, context, chosen, evidence,
     collect(DISTINCT CASE WHEN rj.chosen THEN NULL ELSE {name: alt.name, because: rj.rejected_because} END) AS rejected,
     collect(DISTINCT CASE WHEN rj.chosen THEN {name: alt.name, because: rj.rejected_because} ELSE NULL END) AS limited
OPTIONAL MATCH (d)-[:RESPECTED]->(cr:ConstraintRef)
OPTIONAL MATCH (cr)-[:SAME_AS]->(:ConstraintExport)-[:DERIVED_FROM]->(c:Condition)
WITH d, ep, context, chosen, evidence, rejected, limited,
     collect(DISTINCT {var: cr.var, min: cr.min, max: cr.max, mine: cr.mine, reason: c.kind}) AS constraints
RETURN d.id AS decision, d.at AS at, d.rationale AS rationale, d.arbitration AS arbitration, ep.kind AS episode_kind, ep.summary AS episode,
       context, chosen, evidence, rejected, limited, constraints
