UNWIND $conditions AS c
UNWIND c.implies AS parent
MATCH (child:Condition {kind: c.kind}), (p:Condition {kind: parent})
MERGE (child)-[:IMPLIES]->(p)
