drop table if exists ba_cache_repo_commit;

create table ba_cache_repo_commit
(
    id        integer
        constraint ba_cache_repo_commit_pk
            primary key autoincrement,
    repo_id   integer,
    commit_id text,
    constraint ba_cache_repo_commit_pk_2
        unique (repo_id, commit_id)
);

drop table if exists ba_cache_file_relative_path;

create table ba_cache_file_relative_path
(
    id                 integer
        constraint ba_cache_file_relative_path_pk
            primary key,
    file_relative_path text
        constraint ba_cache_file_relative_path_pk_2
            unique
);

drop table if exists ba_cache_file_commit;

create table ba_cache_file_commit
(
    fid integer not null
        constraint ba_cache_file_commit_ba_cache_file_relative_path_id_fk
            references ba_cache_file_relative_path,
    cid integer not null
        constraint ba_cache_file_commit_ba_cache_repo_commit_id_fk
            references ba_cache_repo_commit,
    constraint ba_cache_file_commit_pk
        primary key (fid, cid)
);

drop index if exists ba_cache_file_commit_cid_index;

create index ba_cache_file_commit_cid_index
    on ba_cache_file_commit (cid);

drop index if exists ba_cache_file_commit_fid_index;

create index ba_cache_file_commit_fid_index
    on ba_cache_file_commit (fid);


