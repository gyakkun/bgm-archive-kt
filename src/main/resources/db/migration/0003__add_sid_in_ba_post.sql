alter table ba_post
    add column sid integer;

update ba_post
set sid = (select sid from ba_topic where ba_post.type = ba_topic.type and ba_post.mid = ba_topic.id);

create index if not exists ba_post_type_uid_state_sid_index
    on ba_post (type, uid, state, sid);

drop view if exists ba_v_post_count_group_by_type_space_uid_state;

create view if not exists ba_v_post_count_group_by_type_space_uid_state as
select bp.type, bp.uid, bsnm.name, bsnm.display_name, bu.username, bp.state, count(1) count
from ba_post bp
         inner join ba_user bu on bp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on bp.type = bsnm.type and bp.sid = bsnm.sid
group by bp.type, bp.uid, bp.state, bp.sid;