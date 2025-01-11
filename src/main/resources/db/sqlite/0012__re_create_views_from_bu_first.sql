drop view if exists ba_v_all_post_count_group_by_type_uid_state;
CREATE VIEW ba_v_all_post_count_group_by_type_uid_state as
select bp.type, bp.uid, bu.username, bp.state, count(*) count
from ba_user bu
         inner join ba_post bp on bp.uid = bu.id
group by type, uid, state;

drop view if exists ba_v_all_topic_count_group_by_type_uid_state;
CREATE VIEW ba_v_all_topic_count_group_by_type_uid_state as
select bt.type, bt.uid, bu.username, bt.state, count(*) count
from ba_user bu
         inner join ba_topic bt on bt.uid = bu.id
group by type, uid, state;

drop view if exists ba_v_likes_sum_by_space_face_key_uid_username;
CREATE VIEW ba_v_likes_sum_by_space_face_key_uid_username as
with tmp as (select bl.type     as type,
                    bl.value    as face_key,
                    bl.total    as face_count,
                    bp.mid      as mid,
                    bp.id       as pid,
                    bu.id       as uid,
                    bu.username as username
             from ba_user bu
                      inner join ba_post bp on bp.uid = bu.id
                      inner join ba_likes bl on bp.id = bl.pid and bp.type = bl.type)
select tmp.type, tmp.face_key, tmp.uid, tmp.username, sum(tmp.face_count) count
from tmp
group by tmp.type, tmp.face_key, tmp.uid, tmp.username
having count > 0;

drop view if exists ba_v_post_count_group_by_type_space_uid_state;
CREATE VIEW ba_v_post_count_group_by_type_space_uid_state as
select bp.type, bp.uid, bsnm.name, bsnm.display_name, bu.username, bp.state, count(1) count
from ba_user bu
         inner join ba_post bp on bp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on bp.type = bsnm.type and bp.sid = bsnm.sid
group by bp.type, bp.uid, bp.state, bp.sid;

drop view if exists ba_v_post_username_rank_by_reply_asc;
CREATE VIEW ba_v_post_username_rank_by_reply_asc as
select bp.*,
       bt.title,
       bu.username as                                                                    username,
       rank() over (partition by bp.type,bp.mid, bp.uid order by bp.dateline desc,bp.id desc) rank_reply_asc
from ba_user bu
         inner join ba_post bp on bu.id = bp.uid
         inner join ba_topic bt on bp.type = bt.type and bt.id = bp.mid;

drop view if exists ba_v_topic_count_group_by_type_space_uid_state;
CREATE VIEW ba_v_topic_count_group_by_type_space_uid_state as
select bt.type, bt.uid, bt.state, bsnm.name, bsnm.display_name, bu.username, count(*) count
from ba_user bu
         inner join ba_topic bt on bt.uid = bu.id
         inner join ba_space_naming_mapping bsnm on bt.type=bsnm.type and bt.sid = bsnm.sid
group by bt.type, bt.uid, bt.sid, bt.state;

drop view if exists ba_v_topic_username_rank_by_last_reply_and_dateline;
CREATE VIEW ba_v_topic_username_rank_by_last_reply_and_dateline as
select bt.*,
       bp.dateline as                                                             last_update_time,
       bu.username as                                                             username,
       rank() over (partition by bt.type,bt.uid order by bp.dateline desc, bp.id desc) rank_last_reply,
       rank() over (partition by bt.type,bt.uid order by bp.dateline desc, bp.id desc) rank_dateline
from ba_user bu
         inner join ba_topic bt on bu.id = bt.uid
         inner join ba_post bp on bt.last_post_pid = bp.id and bt.type = bp.type;

