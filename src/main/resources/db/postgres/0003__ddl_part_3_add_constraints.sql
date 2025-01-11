alter table public.meta_data
    drop constraint if exists meta_data_pk;

alter table public.meta_data
    add constraint meta_data_pk
        primary key (k);

alter table public.ba_user
    drop constraint if exists ba_user_pk;

alter table public.ba_user
    add constraint ba_user_pk
        primary key (id);

alter table public.ba_topic
    drop constraint if exists ba_topic_pk;

alter table public.ba_topic
    add constraint ba_topic_pk
        primary key (type, id);

alter table public.ba_post
    drop constraint if exists ba_post_pk;

alter table public.ba_post
    add constraint ba_post_pk
        primary key (type, id, mid);

alter table public.ba_likes
    drop constraint if exists ba_likes_pk;

alter table public.ba_likes
    add constraint ba_likes_pk
        primary key (type, mid, pid, value);

alter table public.ba_space_naming_mapping
    drop constraint if exists ba_space_naming_mapping_pk;

alter table public.ba_space_naming_mapping
    add constraint ba_space_naming_mapping_pk
        primary key (type, sid);

alter table public.ba_blog_subject_id_mapping
    drop constraint if exists ba_blog_subject_id_mapping_pk;

alter table public.ba_blog_subject_id_mapping
    add constraint ba_blog_subject_id_mapping_pk
        primary key (blog_topic_id, subject_id);

alter table public.ba_blog_tag_mapping
    drop constraint if exists ba_blog_tag_mapping_pk;

alter table public.ba_blog_tag_mapping
    add constraint ba_blog_tag_mapping_pk
        primary key (blog_topic_id, tag);

alter table public.ba_likes_rev
    drop constraint if exists ba_likes_rev_pk;

alter table public.ba_likes_rev
    add constraint ba_likes_rev_pk
        primary key (type, mid, pid, value, uid);

alter table public.ba_cache_repo_commit
    drop constraint if exists ba_cache_repo_commit_pk;

alter table public.ba_cache_repo_commit
    add constraint ba_cache_repo_commit_pk
        primary key (id);

alter table public.ba_cache_repo_commit
    drop constraint if exists ba_cache_repo_commit_pk_2;

alter table public.ba_cache_repo_commit
    add constraint ba_cache_repo_commit_pk_2
        unique (repo_id, commit_id);

alter table public.ba_cache_file_relative_path
    drop constraint if exists ba_cache_file_relative_path_pk;

alter table public.ba_cache_file_relative_path
    add constraint ba_cache_file_relative_path_pk
        primary key (id);

alter table public.ba_cache_file_relative_path
    drop constraint if exists ba_cache_file_relative_path_pk_2;

alter table public.ba_cache_file_relative_path
    add constraint ba_cache_file_relative_path_pk_2
        unique (file_relative_path);

alter table public.ba_cache_file_commit
    drop constraint if exists ba_cache_file_commit_pk;

alter table public.ba_cache_file_commit
    add constraint ba_cache_file_commit_pk
        primary key (fid, cid);

alter table public.ba_cache_file_commit
    drop constraint if exists ba_cache_file_commit_ba_cache_file_relative_path_id_fk;

alter table public.ba_cache_file_commit
    add constraint ba_cache_file_commit_ba_cache_file_relative_path_id_fk
        foreign key (fid) references public.ba_cache_file_relative_path (id);

alter table public.ba_cache_file_commit
    drop constraint if exists ba_cache_file_commit_ba_cache_repo_commit_id_fk;

alter table public.ba_cache_file_commit
    add constraint ba_cache_file_commit_ba_cache_repo_commit_id_fk
        foreign key (cid) references public.ba_cache_repo_commit (id);

