MATCH (s:Session)-[h:HAS_TURN]->(t:Turn)
RETURN t.role AS role, t.text AS text, t.at AS at, s.id AS session
ORDER BY t.at DESC
LIMIT $limit
