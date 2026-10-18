// [body graph] The calendar memory node and how many events hang off the person.
MATCH (p:Person {alias: $alias})-[:HAS_MEMORY]->(c:Calendar)
OPTIONAL MATCH (p)-[:HAS_EVENT]->(e:Event)
RETURN c.id AS calendar, c.source AS source, count(e) AS events
