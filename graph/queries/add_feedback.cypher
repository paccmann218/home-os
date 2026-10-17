// [body graph] Explicit feedback ("better", "too cold") on the latest action.
MATCH (a:Action) WHERE a.at >= $since
WITH a ORDER BY a.at DESC LIMIT 1
CREATE (f:Feedback {at: $now, rating: $rating, text: $text})-[:ON]->(a)
RETURN a.id AS action, f.rating AS rating
