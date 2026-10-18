// Seed: time-of-day routines the home can follow. Each one applies to a room.
MERGE (night:Routine {id: 'night'})
SET night.name = 'Night', night.tod = 'night', night.note = 'Dim, quiet, warmer floor'
MERGE (morning:Routine {id: 'morning'})
SET morning.name = 'Morning', morning.tod = 'morning', morning.note = 'Wake window before the first calendar event'
MERGE (evening:Routine {id: 'wind_down'})
SET evening.name = 'Wind down', evening.tod = 'evening', evening.note = 'Lower light before bed'
WITH night, morning, evening
MATCH (room:Room {id: 'bedroom'})
MERGE (night)-[:APPLIES_TO]->(room)
MERGE (morning)-[:APPLIES_TO]->(room)
MERGE (evening)-[:APPLIES_TO]->(room)
