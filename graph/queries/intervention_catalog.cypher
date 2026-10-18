// [body graph] Interventions and the variable each one moves.
MATCH (i:Intervention)
OPTIONAL MATCH (i)-[:AFFECTS]->(v:Var)
RETURN i.name AS name, i.label AS label, i.device AS device, i.var AS var
ORDER BY i.name
