// [body graph] Next calendar event, plus this person's own sleep episodes from the last 7 days.
// The morning agent decides only from these rows.
MATCH (p:Person {alias: $alias})-[:HAS_EVENT]->(ev:Event)
WHERE ev.kind = 'calendar' AND ev.at >= $now AND ev.at <= $now + $horizon
WITH ev ORDER BY ev.at ASC LIMIT 1
OPTIONAL MATCH (p:Person {alias: $alias})-[:HAD]->(s:Episode {kind: 'sleep'})
WHERE s.start >= $now - 7 * 86400000
WITH ev, [n IN collect(s) WHERE n IS NOT NULL] AS nights
RETURN ev.id AS event_id, ev.title AS title, ev.at AS starts_at, ev.source AS source,
       [n IN nights WHERE n.quality = 'bad' | n.id] AS bad_ids,
       size([n IN nights WHERE n.quality = 'bad']) AS bad_nights,
       size(nights) AS nights
