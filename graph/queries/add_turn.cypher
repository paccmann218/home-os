// [body graph] Persistent conversation history: Session -HAS_TURN-> Turn -ABOUT-> entities.
MERGE (s:Session {id: $session}) ON CREATE SET s.started = $now, s.channel = $channel
WITH s
OPTIONAL MATCH (s)-[prev:HAS_TURN]->()
WITH s, count(prev) AS i
CREATE (s)-[:HAS_TURN {i: i}]->(t:Turn {role: $role, text: $text, lang: $lang, at: $now})
WITH t
UNWIND (CASE WHEN size($about) = 0 THEN [null] ELSE $about END) AS ref
OPTIONAL MATCH (x) WHERE ref IS NOT NULL AND ((x:Episode AND x.id = ref) OR (x:Intervention AND x.name = ref) OR (x:Condition AND x.kind = ref) OR (x:Decision AND x.id = ref))
FOREACH (xx IN CASE WHEN x IS NULL THEN [] ELSE [x] END | MERGE (t)-[:ABOUT]->(xx))
WITH DISTINCT t
FOREACH (_ IN CASE WHEN $vec IS NULL THEN [] ELSE [1] END | SET t.embedding = vecf32($vec))
RETURN t.at AS at
