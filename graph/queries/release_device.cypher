MATCH (d:Device {id: $device})-[l:LEASED_BY]->(a:Agent {id: $agent}) DELETE l RETURN count(*) AS released
