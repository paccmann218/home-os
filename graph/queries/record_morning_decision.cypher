// [body graph] The decision is a node. It points at the calendar event and the bad nights it used.
MATCH (p:Person {alias: $alias})
MATCH (ev:Event {id: $event})
CREATE (ep:Episode {id: $episode, kind: 'sleep_plan', start: $now, status: 'closed', end: $now, summary: $summary, trigger: 'morning', quality: 'plan'})
CREATE (p)-[:HAD]->(ep)
CREATE (d:Decision {id: $decision, at: $now, by: 'morning', rationale: $rationale})
CREATE (d)-[:FOR]->(ep)
CREATE (d)-[:DERIVED_FROM]->(ev)
WITH d
UNWIND $sleep_ids AS sid
MATCH (s:Episode {id: sid})
CREATE (d)-[:DERIVED_FROM]->(s)
RETURN d.id AS id
