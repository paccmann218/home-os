// [home graph] The bedroom clock, including alarms written by a confirmed morning decision.
MATCH (d:Device {id: 'clock'})
OPTIONAL MATCH (rt:Routine)-[:TRIGGERS]->(d)
RETURN d.id AS id, d.name AS name, d.power AS power, d.alarm_primary AS alarm_primary, d.alarm_backup AS alarm_backup, d.set_for AS set_for, collect(rt.id) AS routines
