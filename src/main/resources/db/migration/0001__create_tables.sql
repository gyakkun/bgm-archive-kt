create table if not exists meta_data
(
    k text not null
        constraint ba_meta_data_pk
            primary key,
    v text
);

create table if not exists ba_user
(
    id       integer not null
        constraint ba_user_pk
            primary key,
    username text    not null
);

create table if not exists ba_topic
(
    type          integer not null,
    id            integer not null,
    uid           integer not null,
    sid           integer,
    dateline      integer,
    state         integer,
    last_post_pid integer,
    title         text,
    constraint ba_topic_pk
        primary key (type, id, uid)
);

create index if not exists ba_topic_type_uid_index
    on ba_topic (type, uid);

create index if not exists ba_topic_uid_index
    on ba_topic (uid);

create index if not exists ba_topic_type_sid_index
    on ba_topic (type, sid);

create table if not exists ba_post
(
    type     integer not null,
    id       integer not null,
    mid      integer not null,
    uid      integer not null,
    dateline integer,
    state    integer,
    constraint ba_post_pk
        primary key (type, id, mid, uid)
);

create index if not exists ba_post_type_uid_index
    on ba_post (type, uid);

create table if not exists ba_likes
(
    type  integer,
    mid   integer,
    pid   integer,
    value integer,
    total integer,
    constraint ba_likes_pk
        primary key (type, mid, pid, value)
);

create index if not exists ba_likes_pid_index
    on ba_likes (pid);

create index if not exists ba_likes_type_mid_index
    on ba_likes (type, mid);

create table if not exists ba_space_naming_mapping -- Should be for group/subject only
(
    type         integer,
    sid          integer,
    name         TEXT,
    display_name TEXT,
    constraint ba_space_naming_mapping_pk
        primary key (type, sid)
);

create index if not exists ba_space_naming_mapping_type_name_index
    on ba_space_naming_mapping (type, name);

CREATE TABLE ba_blog_subject_id_mapping -- Should be for blog only
(
    blog_topic_id integer,
    subject_id    integer,
    constraint ba_blog_subject_id_mapping_pk
        primary key (blog_topic_id, subject_id)
);

CREATE INDEX ba_blog_subject_id_mapping_subject_id_index
    on ba_blog_subject_id_mapping (subject_id);

create table ba_blog_tag_mapping
(
    blog_topic_id integer,
    tag           integer,
    constraint ba_blog_tag_mapping_pk
        primary key (blog_topic_id, tag)
);

create index ba_blog_tag_mapping_tag_index
    on ba_blog_tag_mapping (tag);

