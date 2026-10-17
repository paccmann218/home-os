// [body graph] "I hate the fan when I'm sick" -> DISLIKES edge, optionally scoped to a condition.
MATCH (p:Person {alias: $alias}), (i:Intervention {name: $intervention})
CREATE (p)-[d:DISLIKES {valid_from: $now, valid_to: $forever, when_condition: $when, source: $source}]->(i)
RETURN i.name AS intervention, d.when_condition AS when
