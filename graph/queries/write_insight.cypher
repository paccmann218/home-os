// [body graph, Scientist] Store an insight produced by a graph algorithm, linked to its subject.
CREATE (n:Insight {id: $id, at: $now, algo: $algo, text: $text, data_json: $data})
WITH n
OPTIONAL MATCH (b:Band {key: $about})
FOREACH (x IN CASE WHEN b IS NULL THEN [] ELSE [b] END | CREATE (n)-[:ABOUT]->(x))
RETURN n.id AS id
