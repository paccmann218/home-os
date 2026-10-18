// [body graph] How many of the last 7 days of nights were bad.
MATCH (p:Person {alias: $alias})-[:HAD]->(ep:Episode {kind: 'sleep'})
WHERE ep.start >= $since
RETURN count(ep) AS nights,
       size([e IN collect(ep) WHERE e.quality = 'bad']) AS bad_nights
