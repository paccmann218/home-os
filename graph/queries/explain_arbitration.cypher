// [home graph] Explanation from the shared side. A viewer sees their own constraint ids,
// but other people's constraints only as anonymous ranges - never who or why.
MATCH (a:Arbitration {id: $arbitration})
OPTIONAL MATCH (a)-[ap:APPLIED]->(d:Device)
WITH a, collect(DISTINCT {device: d.id, setting: ap.setting}) AS applied
OPTIONAL MATCH (a)-[:BOUNDED_BY]->(c:Constraint)<-[:HAS_CONSTRAINT]-(p:Person)
WITH a, applied, collect(DISTINCT {var: c.var, min: c.min, max: c.max, id: c.id, mine: p.alias = $viewer}) AS bounds
OPTIONAL MATCH (a)-[:RESOLVED]->(i:Intent)<-[:POSTED]-(ag:Agent)
RETURN a.id AS arbitration, a.at AS at, a.summary AS summary, applied,
       [b IN bounds | CASE WHEN b.mine THEN b ELSE {var: b.var, min: b.min, max: b.max, id: 'someone-present', mine: false} END] AS bounds,
       collect(DISTINCT {var: i.var, target: i.target, mine: ag.owner = $viewer}) AS intents
