// [home graph] Devices currently leased, and by whom.
MATCH (d:Device)-[l:LEASED_BY]->(a:Agent)
WHERE l.until > $now
RETURN d.id AS device, a.id AS agent, l.until AS until
ORDER BY d.id
