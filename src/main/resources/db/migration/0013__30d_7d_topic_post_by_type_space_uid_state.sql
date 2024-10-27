drop view if exists ba_v_post_count_30d_group_by_type_space_uid_state;
CREATE VIEW ba_v_post_count_30d_group_by_type_space_uid_state as
select bp.type, bp.uid, bsnm.name, bsnm.display_name, bu.username, bp.state, count(1) count
from ba_user bu
         inner join ba_post bp on bp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on bp.type = bsnm.type and bp.sid = bsnm.sid
where dateline >= ((select unixepoch() - 86400*30))
group by bp.type, bp.uid, bp.state, bp.sid;

drop view if exists ba_v_post_count_7d_group_by_type_space_uid_state;
CREATE VIEW ba_v_post_count_7d_group_by_type_space_uid_state as
select bp.type, bp.uid, bsnm.name, bsnm.display_name, bu.username, bp.state, count(1) count
from ba_user bu
         inner join ba_post bp on bp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on bp.type = bsnm.type and bp.sid = bsnm.sid
where dateline >= ((select unixepoch() - 86400*7))
group by bp.type, bp.uid, bp.state, bp.sid;


drop view if exists ba_v_topic_count_30d_group_by_type_space_uid_state;
CREATE VIEW ba_v_topic_count_30d_group_by_type_space_uid_state as
select bt.type, bt.uid, bt.state, bsnm.name, bsnm.display_name, bu.username, count(*) count
from ba_user bu
         inner join ba_topic bt on bt.uid = bu.id
         inner join ba_space_naming_mapping bsnm on bt.type=bsnm.type and bt.sid = bsnm.sid
where dateline >= ((select unixepoch() - 86400*30))
group by bt.type, bt.uid, bt.sid, bt.state;

drop view if exists ba_v_topic_count_7d_group_by_type_space_uid_state;
CREATE VIEW ba_v_topic_count_7d_group_by_type_space_uid_state as
select bt.type, bt.uid, bt.state, bsnm.name, bsnm.display_name, bu.username, count(*) count
from ba_user bu
         inner join ba_topic bt on bt.uid = bu.id
         inner join ba_space_naming_mapping bsnm on bt.type=bsnm.type and bt.sid = bsnm.sid
where dateline >= ((select unixepoch() - 86400*7))
group by bt.type, bt.uid, bt.sid, bt.state;