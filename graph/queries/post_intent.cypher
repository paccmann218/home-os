// [home graph, write API] A personal planner states what it wants for its person - the arbiter
// resolves all intents for a room against everyone's constraints.
MATCH (a:Agent {id: $agent}), (r:Room {id: $room})
CREATE (a)-[:POSTED]->(i:Intent {id: $id, var: $var, target: $target, device: $device, intervention: $intervention,
                                 priority: $priority, created: $now, valid_to: $valid_to, status: 'open', episode: $episode})-[:ON]->(r)
RETURN i.id AS id
