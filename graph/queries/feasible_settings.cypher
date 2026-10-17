// [home graph] Track 1 multi-hop arbitration input:
// Room -> Device -CONTROLS-> Var -AFFECTS-> Signal, and Room <-IN- Person -HAS_CONSTRAINT-> Constraint.
// For every device/variable it returns the band every present person can live with, resolved by
// priority when ranges conflict (HomeOS.resolveRanges), who binds it, whose range was overridden,
// and which body signals a change would touch.
MATCH (r:Room {id: $room})-[:HAS]->(d:Device)-[k:CONTROLS]->(v:Var)
OPTIONAL MATCH (r)<-[:IN]-(p:Person)-[:HAS_CONSTRAINT]->(c:Constraint {var: v.name})-[:FOR_ROOM]->(r)
WHERE c.valid_from <= $now AND c.valid_to > $now
WITH r, d, k, v, collect(CASE WHEN c IS NULL THEN NULL ELSE {min: c.min, max: c.max, id: c.id, who: p.alias, priority: coalesce(c.priority, 0)} END) AS cs
OPTIONAL MATCH (v)-[a:AFFECTS]->(s:Signal)
WITH r, d, k, v, cs, collect(CASE WHEN s IS NULL THEN NULL ELSE s.name END) AS touches
WITH d, k, v, cs, touches,
     HomeOS.resolveRanges(cs + CASE WHEN k.strength >= 1.0 THEN [{min: d.min, max: d.max, priority: 99, id: null}] ELSE [] END) AS eff
RETURN d.id AS device, d.kind AS kind, d.driver AS driver, v.name AS var, k.strength AS strength,
       eff.min AS min, eff.max AS max, eff.feasible AS feasible,
       [x IN cs WHERE x.id IN eff.kept | x.id] AS constraint_ids, [x IN cs WHERE x.id IN eff.kept | x.who] AS bound_by,
       [x IN cs WHERE x.id IN eff.overridden | x.who] AS overridden_for, eff.overridden AS overridden_ids, touches,
       d.power AS power, d.setpoint AS setpoint, d.level AS level, d.brightness AS brightness
ORDER BY device, strength DESC
