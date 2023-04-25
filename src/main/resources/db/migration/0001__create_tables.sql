create table if not exists meta_data
(
    k text,
    v text
);

create table if not exists ba_user
(
    id       integer
        constraint ba_user_pk
            primary key,
    username text not null
);

create table if not exists ba_topic
(
    type     integer not null,
    id       integer,
    title    text,
    uid      integer,
    dateline integer,
    constraint ba_topic_pk
        primary key (type, id, uid)
);

create index if not exists ba_topic_type_uid_index
    on ba_topic (type, uid);

create index if not exists ba_topic_uid_index
    on ba_topic (uid);

create table if not exists ba_post
(
    type     integer,
    id       integer,
    mid      integer,
    uid      integer,
    dateline integer,
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
