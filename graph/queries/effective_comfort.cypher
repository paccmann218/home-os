// [body graph] Track 1 multi-hop: Person -> active conditions -> IMPLIES* -> SHIFTS -> Var,
// combined with the person's own (bi-temporal) preferences. Conditions outrank preferences
// (HomeOS.resolveRanges). Returns the comfort range this person needs right now for every
// variable, which conditions caused it, and the priority of the strongest one.
MATCH (p:Person {alias: $alias}), (v:Var)
OPTIONAL MATCH (p)-[pr:PREFERS]->(v)
WHERE pr.valid_from <= $now AND pr.valid_to > $now
WITH p, v, collect(CASE WHEN pr IS NULL THEN NULL ELSE {min: pr.min, max: pr.max, priority: 0, id: 'preference'} END) AS prefs
OPTIONAL MATCH (p)-[hc:HAS_CONDITION]->(c0:Condition)-[:IMPLIES*0..3]->(c:Condition)-[s:SHIFTS]->(v)
WHERE hc.valid_from <= $now AND hc.valid_to > $now
WITH v, prefs,
     collect(CASE WHEN s IS NULL THEN NULL ELSE {min: s.min, max: s.max, priority: coalesce(c.priority, c0.priority, 1) + 1, id: c.kind, via: c0.kind} END) AS shifts
WITH v, prefs, shifts, HomeOS.resolveRanges(prefs + shifts) AS eff
WHERE size(prefs) + size(shifts) > 0
RETURN v.name AS var, eff.min AS min, eff.max AS max, eff.feasible AS feasible,
       [x IN shifts | x.via] AS conditions, [x IN shifts | x.id] AS via,
       reduce(m = 0, x IN shifts | CASE WHEN x.priority - 1 > m THEN x.priority - 1 ELSE m END) AS priority
