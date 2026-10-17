// [body graph] Keyword recall over conversation turns (full-text index).
CALL db.idx.fulltext.queryNodes('Turn', $text) YIELD node, score
MATCH (s:Session)-[:HAS_TURN]->(node)
RETURN node.text AS text, node.role AS role, node.at AS at, s.id AS session, score
ORDER BY score DESC
LIMIT $k
