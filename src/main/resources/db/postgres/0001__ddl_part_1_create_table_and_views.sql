CREATE TABLE IF NOT EXISTS meta_data
(
    k text not null,
    v text
);
CREATE TABLE IF NOT EXISTS ba_user
(
    id       bigint not null,
    username text   not null,
    nickname text
);
CREATE TABLE IF NOT EXISTS ba_topic
(
    type          smallint not null,
    id            integer  not null,
    uid           bigint,
    sid           bigint,
    dateline      bigint,
    state         integer,
    top_post_pid  bigint,
    last_post_pid bigint,
    title         text
);
CREATE TABLE IF NOT EXISTS ba_post
(
    type     smallint not null,
    id       integer  not null,
    mid      integer,
    uid      bigint,
    dateline bigint,
    state    integer,
    sid      bigint
);
CREATE TABLE IF NOT EXISTS ba_likes
(
    type  smallint,
    mid   integer,
    pid   integer,
    value smallint,
    total integer
);
CREATE TABLE IF NOT EXISTS ba_space_naming_mapping -- Should be for group/subject only
(
    type         smallint,
    sid          bigint,
    name         TEXT,
    display_name TEXT
);
CREATE TABLE IF NOT EXISTS ba_blog_subject_id_mapping -- Should be for blog only
(
    blog_topic_id integer,
    subject_id    integer
);
CREATE TABLE IF NOT EXISTS ba_blog_tag_mapping
(
    blog_topic_id integer,
    tag           text
);
CREATE TABLE IF NOT EXISTS ba_likes_rev
(
    type  smallint,
    mid   integer,
    pid   integer,
    value smallint,
    uid   bigint,
    total integer
);
CREATE TABLE IF NOT EXISTS "ba_cache_repo_commit"
(
    id        bigserial,
    repo_id   bigint,
    commit_id text
);

CREATE TABLE IF NOT EXISTS "ba_cache_file_relative_path"
(
    id                 bigserial,
    file_relative_path text
);
CREATE TABLE IF NOT EXISTS "ba_cache_file_commit"
(
    fid bigint not null,
    cid bigint not null
);

CREATE OR REPLACE VIEW ba_v_all_post_count_30d_group_by_type_uid_state as
    with tmp as (select bp.type, bp.uid, bp.state, count(*) count
    from ba_post bp
    inner join ba_user bu on bp.uid = bu.id
    where dateline >= (((select extract(epoch from now()))::bigint - 86400 * 30))
    group by type, uid, state)
select tmp.type, tmp.uid, bu.username, tmp.state, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
/* ba_v_all_post_count_30d_group_by_type_uid_state(type,uid,username,state,count) */;


CREATE OR REPLACE VIEW ba_v_all_post_count_7d_group_by_type_uid_state as
    with tmp as (select bp.type, bp.uid, bp.state, count(*) count
    from ba_post bp
    inner join ba_user bu on bp.uid = bu.id
    where dateline >= (((select extract(epoch from now()))::bigint - 86400 * 7))
    group by type, uid, state)
select tmp.type, tmp.uid, bu.username, tmp.state, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
/* ba_v_all_post_count_7d_group_by_type_uid_state(type,uid,username,state,count) */;


CREATE OR REPLACE VIEW ba_v_all_topic_count_30d_group_by_type_uid_state as
    with tmp as (select bt.type, bt.uid, bt.state, count(*) count
    from ba_topic bt
    inner join ba_user bu on bt.uid = bu.id
    where dateline >= (((select extract(epoch from now()))::bigint - 86400 * 30))
    group by type, uid, state)
select tmp.type, tmp.uid, bu.username, tmp.state, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id;
/* ba_v_all_topic_count_30d_group_by_type_uid_state(type,uid,username,state,count) */;


CREATE OR REPLACE VIEW ba_v_all_topic_count_7d_group_by_type_uid_state as
    with tmp as (select bt.type, bt.uid, bt.state, count(*) count
    from ba_topic bt
    inner join ba_user bu on bt.uid = bu.id
    where dateline >= (((select extract(epoch from now()))::bigint - 86400 * 7))
    group by type, uid, state)
select tmp.type, tmp.uid, bu.username, tmp.state, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
/* ba_v_all_topic_count_7d_group_by_type_uid_state(type,uid,username,state,count) */;


CREATE OR REPLACE VIEW ba_v_all_post_count_group_by_type_uid_state as
    with tmp as (select bp.type, bp.uid, bp.state, count(*) count
    from ba_user bu
    inner join ba_post bp on bp.uid = bu.id
    group by type, uid, state)
select tmp.type, tmp.uid, bu.username, tmp.state, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
/* ba_v_all_post_count_group_by_type_uid_state(type,uid,username,state,count) */;


CREATE OR REPLACE VIEW ba_v_all_topic_count_group_by_type_uid_state as
    with tmp as (select bt.type, bt.uid, bt.state, count(*) count
    from ba_user bu
    inner join ba_topic bt on bt.uid = bu.id
    group by type, uid, state)
select tmp.type, tmp.uid, bu.username, tmp.state, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
/* ba_v_all_topic_count_group_by_type_uid_state(type,uid,username,state,count) */;


CREATE OR REPLACE VIEW ba_v_likes_sum_by_space_face_key_uid_username as
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
having sum(tmp.face_count) > 0
/* ba_v_likes_sum_by_space_face_key_uid_username(type,face_key,uid,username,count) */;


CREATE OR REPLACE VIEW ba_v_post_count_group_by_type_space_uid_state as
    with tmp as (select bp.type, bp.uid, bp.sid, bp.state, count(*) count
    from ba_post bp
    group by bp.type, bp.uid, bp.state, bp.sid)
select tmp.type, tmp.uid, bsnm.name, bsnm.display_name, bu.username, tmp.state, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on tmp.type = bsnm.type and tmp.sid = bsnm.sid
/* ba_v_post_count_group_by_type_space_uid_state(type,uid,name,display_name,username,state,count) */;


CREATE OR REPLACE VIEW ba_v_post_username_rank_by_reply_asc as
select bp.*,
       bt.title,
       bu.username as                                                                         username,
       rank() over (partition by bp.type,bp.mid, bp.uid order by bp.dateline desc,bp.id desc) rank_reply_asc
from ba_user bu
         inner join ba_post bp on bu.id = bp.uid
         inner join ba_topic bt on bp.type = bt.type and bt.id = bp.mid
/* ba_v_post_username_rank_by_reply_asc(type,id,mid,uid,dateline,state,sid,title,username,rank_reply_asc) */;


CREATE OR REPLACE VIEW ba_v_topic_count_group_by_type_space_uid_state as
    with tmp as (select bt.type, bt.uid, bt.sid, bt.state, count(*) count
    from ba_topic bt
    group by bt.type, bt.uid, bt.sid, bt.state)
select tmp.type, tmp.uid, tmp.state, bsnm.name, bsnm.display_name, bu.username, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on tmp.type = bsnm.type and tmp.sid = bsnm.sid
/* ba_v_topic_count_group_by_type_space_uid_state(type,uid,state,name,display_name,username,count) */;


CREATE OR REPLACE VIEW ba_v_topic_username_rank_by_last_reply_and_dateline as
select bt.*,
       bp.dateline as                                                                  last_update_time,
       bu.username as                                                                  username,
       rank() over (partition by bt.type,bt.uid order by bp.dateline desc, bp.id desc) rank_last_reply,
        rank() over (partition by bt.type,bt.uid order by bp.dateline desc, bp.id desc) rank_dateline
from ba_user bu
         inner join ba_topic bt on bu.id = bt.uid
         inner join ba_post bp on bt.last_post_pid = bp.id and bt.type = bp.type
/* ba_v_topic_username_rank_by_last_reply_and_dateline(type,id,uid,sid,dateline,state,top_post_pid,last_post_pid,title,last_update_time,username,rank_last_reply,rank_dateline) */;


CREATE OR REPLACE VIEW ba_v_post_count_30d_group_by_type_space_uid_state as
    with tmp as (select bp.type, bp.uid, bp.state, bp.sid, count(*) count
    from ba_post bp
    where dateline >= (((select extract(epoch from now()))::bigint - 86400 * 30))
    group by bp.type, bp.uid, bp.state, bp.sid)
select tmp.type, tmp.uid, bsnm.name, bsnm.display_name, bu.username, tmp.state, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on tmp.type = bsnm.type and tmp.sid = bsnm.sid
/* ba_v_post_count_30d_group_by_type_space_uid_state(type,uid,name,display_name,username,state,count) */;


CREATE OR REPLACE VIEW ba_v_post_count_7d_group_by_type_space_uid_state as
    with tmp as (select bp.type, bp.uid, bp.state, bp.sid, count(*) count
    from ba_post bp
    where dateline >= (((select extract(epoch from now()))::bigint - 86400 * 7))
    group by bp.type, bp.uid, bp.state, bp.sid)
select tmp.type, tmp.uid, bsnm.name, bsnm.display_name, bu.username, tmp.state, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on tmp.type = bsnm.type and tmp.sid = bsnm.sid
/* ba_v_post_count_7d_group_by_type_space_uid_state(type,uid,name,display_name,username,state,count) */;


CREATE OR REPLACE VIEW ba_v_topic_count_30d_group_by_type_space_uid_state as
    with tmp as (select bt.type, bt.uid, bt.sid, bt.state, count(*) count
    from ba_topic bt
    where dateline >= (((select extract(epoch from now()))::bigint - 86400 * 30))
    group by bt.type, bt.uid, bt.sid, bt.state)
select tmp.type, tmp.uid, tmp.state, bsnm.name, bsnm.display_name, bu.username, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on tmp.type = bsnm.type and tmp.sid = bsnm.sid
/* ba_v_topic_count_30d_group_by_type_space_uid_state(type,uid,state,name,display_name,username,count) */;


CREATE OR REPLACE VIEW ba_v_topic_count_7d_group_by_type_space_uid_state as
    with tmp as (select bt.type, bt.uid, bt.sid, bt.state, count(*) count
    from ba_topic bt
    where dateline >= (((select extract(epoch from now()))::bigint - 86400 * 7))
    group by bt.type, bt.uid, bt.sid, bt.state)
select tmp.type, tmp.uid, tmp.state, bsnm.name, bsnm.display_name, bu.username, tmp.count
from tmp
         inner join ba_user bu on tmp.uid = bu.id
         inner join ba_space_naming_mapping bsnm on tmp.type = bsnm.type and tmp.sid = bsnm.sid
/* ba_v_topic_count_7d_group_by_type_space_uid_state(type,uid,state,name,display_name,username,count) */;

