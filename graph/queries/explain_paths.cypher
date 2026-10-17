// [body graph] The provenance subgraph of a decision as paths (the dashboard highlights these).
MATCH p = (d:Decision {id: $decision})-[:FOR|IN_STATE|CHOSE|USED|WHEN|RESPECTED|SAME_AS|DERIVED_FROM*1..4]->(x)
RETURN p
LIMIT 200
