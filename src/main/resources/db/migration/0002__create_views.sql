create view if not exists ba_v_likes_sum_by_space_face_key_uid_username as
with tmp as (select bl.type     as space_type,
                    bl.value    as face_key,
                    bl.total    as face_count,
                    bp.mid      as mid,
                    bp.id       as pid,
                    bu.id       as uid,
                    bu.username as username
             from ba_likes bl
                      inner join ba_post bp on bp.id = bl.pid and bp.type = bl.type
                      inner join ba_topic bt on bp.type = bt.type and bp.mid = bt.id --  and bt.state != 1
                      inner join ba_user bu on bp.uid = bu.id)
select tmp.space_type, tmp.face_key, tmp.uid, tmp.username, sum(tmp.face_count) count_like
from tmp
group by tmp.space_type, tmp.face_key, tmp.uid, tmp.username
having count_like > 0;

create view if not exists ba_v_topic_username_rank_by_last_reply_and_dateline as
select bt.*,
       bp.dateline as                                                      last_update_time,
       bu.username as                                                      username,
       rank() over (partition by bt.type,bt.uid order by bp.dateline, bp.id desc) rank_last_reply,
       rank() over (partition by bt.type,bt.uid order by bp.dateline, bp.id desc) rank_dateline
from ba_topic bt
         inner join ba_post bp on bt.last_post_pid = bp.id and bt.type = bp.type
         inner join ba_user bu on bu.id = bt.uid;

create view if not exists ba_v_all_post_count_group_by_type_uid_state as
select bp.type, bp.uid, bu.username, bp.state, count(*) count_all_post
from ba_post bp
         inner join ba_user bu on bp.uid = bu.id
group by type, uid , state;

create view if not exists ba_v_all_topic_count_group_by_type_uid_state as
select bt.type, bt.uid, bu.username, bt.state, count(*) count_all_topic
from ba_topic bt
         inner join ba_user bu on bt.uid = bu.id
group by type, uid , state;

create view if not exists ba_v_post_count_group_by_type_space_uid_state as
select bp.type, bp.uid, bsnm.name, bsnm.display_name, bu.username, bp.state, count(bp.id) count_space_post
from ba_post bp
         inner join ba_topic bt on bt.type = bp.type and bt.id = bp.mid
         inner join ba_user bu on bp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on bt.type = bsnm.type and bt.sid = bsnm.sid
group by bp.type, bp.uid, bt.sid, bp.state;

create view if not exists ba_v_topic_count_group_by_type_space_uid_state as
select bt.type, bt.uid, bt.state, bsnm.name, bsnm.display_name, bu.username, count(*) count_space_topic
from ba_topic bt
         inner join ba_user bu on bt.uid = bu.id
         inner join ba_space_naming_mapping bsnm on bt.sid = bsnm.sid
group by bt.type, bt.uid,bt.sid, bt.state;


create index if not exists ba_post_dateline_index
    on ba_post (dateline desc);

create index if not exists ba_post_type_mid_index
    on ba_post (type, mid);

create index if not exists ba_post_type_uid_state_index
    on ba_post (type, uid, state);

create index if not exists ba_topic_type_uid_state_index
    on ba_topic (type, uid, state);


