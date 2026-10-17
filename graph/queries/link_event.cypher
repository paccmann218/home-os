// [body graph] An Episode happened AFTER an Event (workout, voice report, presence change).
MATCH (ep:Episode {id: $episode})
MERGE (e:Event {id: $event_id}) ON CREATE SET e.kind = $kind, e.at = $at, e.source = $source
CREATE (ep)-[:AFTER {gap_min: $gap_min, neglogp: 1.0}]->(e)
RETURN e.id AS event
