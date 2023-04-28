CREATE VIEW ba_v_likes_sum_by_space_face_key_uid_username as
with tmp as (select bl.type     as space_type,
                    bl.value    as face_key,
                    bl.total    as face_count,
                    bp.mid      as mid,
                    bp.id       as pid,
                    bu.id       as uid,
                    bu.username as username
             from ba_likes bl
                      inner join ba_post bp on bp.id = bl.pid and bp.type = bl.type
                      inner join ba_topic bt on bp.type = bt.type and bp.mid = bt.id
                      inner join ba_user bu on bp.uid = bu.id)
select tmp.space_type, tmp.face_key, tmp.uid,tmp.username, sum(tmp.face_count) count
from tmp
group by tmp.space_type, tmp.face_key, tmp.uid, tmp.username;

create index ba_post_dateline_index
    on ba_post (dateline desc);

CREATE INDEX ba_post_type_mid_index
    on ba_post (type, mid)

