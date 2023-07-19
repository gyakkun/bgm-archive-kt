drop view if exists ba_v_topic_count_group_by_type_space_uid_state;

CREATE VIEW ba_v_topic_count_group_by_type_space_uid_state as
select bt.type, bt.uid, bt.state, bsnm.name, bsnm.display_name, bu.username, count(*) count
from ba_topic bt
         inner join ba_user bu on bt.uid = bu.id
         inner join ba_space_naming_mapping bsnm on bt.type=bsnm.type and bt.sid = bsnm.sid
group by bt.type, bt.uid, bt.sid, bt.state

