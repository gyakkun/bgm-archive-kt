drop index if exists ba_likes_rev_type_pid_uid_index;

create index ba_likes_rev_type_pid_uid_index
    on ba_likes_rev (type, pid, uid);
