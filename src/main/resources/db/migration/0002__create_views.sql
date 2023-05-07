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
                      inner join ba_topic bt on bp.type = bt.type and bp.mid = bt.id and bt.state != 1
                      inner join ba_user bu on bp.uid = bu.id)
select tmp.space_type, tmp.face_key, tmp.uid, tmp.username, sum(tmp.face_count) count
from tmp
group by tmp.space_type, tmp.face_key, tmp.uid, tmp.username
having count > 0;

create view if not exists ba_v_topic_username_rank_by_last_reply as
select bt.*,
       bp.dateline as                                                      last_update_time,
       bu.username as                                                      username,
       rank() over (partition by bt.type,bt.uid order by bp.dateline desc) tr
from ba_topic bt
         inner join ba_post bp on bt.last_post_pid = bp.id and bt.type = bp.type
         inner join ba_user bu on bu.id = bt.uid;

create index if not exists ba_post_dateline_index
    on ba_post (dateline desc);

create index if not exists ba_post_type_mid_index
    on ba_post (type, mid)

