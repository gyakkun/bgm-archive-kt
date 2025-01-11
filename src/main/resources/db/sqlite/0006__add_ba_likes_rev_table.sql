create table if not exists ba_likes_rev
(
    type  integer,
    mid   integer,
    pid   integer,
    value integer,
    uid   integer,
    total integer,
    constraint ba_likes_rev_pk
        primary key (type, mid, pid, value, uid)
);

-- drop index if exists ba_likes_pid_index;

-- create index if not exists ba_likes_rev_type_mid_index
--     on ba_likes_rev (type, mid);

create index if not exists ba_likes_rev_type_uid_index
    on ba_likes_rev (type, uid);