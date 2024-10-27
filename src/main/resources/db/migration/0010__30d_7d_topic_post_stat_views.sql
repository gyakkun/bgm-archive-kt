drop view if exists ba_v_all_post_count_30d_group_by_type_uid_state;
CREATE VIEW ba_v_all_post_count_30d_group_by_type_uid_state as
select bp.type, bp.uid, bu.username, bp.state, count(*) count
from ba_post bp
         inner join ba_user bu on bp.uid = bu.id
where dateline >= ((select unixepoch() - 86400*30))
group by type, uid, state;


drop view if exists ba_v_all_post_count_7d_group_by_type_uid_state;
CREATE VIEW ba_v_all_post_count_7d_group_by_type_uid_state as
select bp.type, bp.uid, bu.username, bp.state, count(*) count
from ba_post bp
         inner join ba_user bu on bp.uid = bu.id
where dateline >= ((select unixepoch() - 86400*7))
group by type, uid, state;

drop view if exists ba_v_all_topic_count_30d_group_by_type_uid_state;
CREATE VIEW ba_v_all_topic_count_30d_group_by_type_uid_state as
select bt.type, bt.uid, bu.username, bt.state, count(*) count
from ba_topic bt
         inner join ba_user bu on bt.uid = bu.id
where dateline >= ((select unixepoch() - 86400*30))
group by type, uid, state;

drop view if exists ba_v_all_topic_count_7d_group_by_type_uid_state;
CREATE VIEW ba_v_all_topic_count_7d_group_by_type_uid_state as
select bt.type, bt.uid, bu.username, bt.state, count(*) count
from ba_topic bt
         inner join ba_user bu on bt.uid = bu.id
where dateline >= ((select unixepoch() - 86400*7))
group by type, uid, state;

