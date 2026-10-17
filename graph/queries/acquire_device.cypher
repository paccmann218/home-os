// [home graph, write API] Atomic device lease: succeeds only if nobody else holds a live lease.
// An empty result means another agent holds the device.
MATCH (d:Device {id: $device}), (a:Agent {id: $agent})
OPTIONAL MATCH (d)-[l:LEASED_BY]->(holder:Agent)
WITH d, a, l, holder
WHERE l IS NULL OR l.until < $now OR holder.id = $agent
FOREACH (x IN CASE WHEN l IS NULL THEN [] ELSE [l] END | DELETE x)
CREATE (d)-[:LEASED_BY {until: $now + $ttl_ms, at: $now}]->(a)
RETURN a.id AS holder
