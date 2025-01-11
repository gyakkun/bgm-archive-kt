
CREATE INDEX IF NOT EXISTS ba_topic_type_uid_index
    on ba_topic (type, uid);
CREATE INDEX IF NOT EXISTS ba_topic_uid_index
    on ba_topic (uid);
CREATE INDEX IF NOT EXISTS ba_topic_type_sid_index
    on ba_topic (type, sid);
CREATE INDEX IF NOT EXISTS ba_post_type_uid_index
    on ba_post (type, uid);
CREATE INDEX IF NOT EXISTS ba_likes_pid_index
    on ba_likes (pid);
CREATE INDEX IF NOT EXISTS ba_likes_type_mid_index
    on ba_likes (type, mid);
CREATE INDEX IF NOT EXISTS ba_space_naming_mapping_type_name_index
    on ba_space_naming_mapping (type, name);
CREATE INDEX IF NOT EXISTS ba_blog_subject_id_mapping_subject_id_index
    on ba_blog_subject_id_mapping (subject_id);
CREATE INDEX IF NOT EXISTS ba_blog_tag_mapping_tag_index
    on ba_blog_tag_mapping (tag);
CREATE INDEX IF NOT EXISTS ba_post_dateline_index
    on ba_post (dateline desc);
CREATE INDEX IF NOT EXISTS ba_post_type_mid_index
    on ba_post (type, mid);
CREATE INDEX IF NOT EXISTS ba_post_type_uid_state_index
    on ba_post (type, uid, state);
CREATE INDEX IF NOT EXISTS ba_topic_type_uid_state_index
    on ba_topic (type, uid, state);
CREATE INDEX IF NOT EXISTS ba_likes_type_pid_index
    on ba_likes (type, pid);
CREATE INDEX IF NOT EXISTS ba_user_username_index
    on ba_user (username);
CREATE INDEX IF NOT EXISTS ba_post_type_uid_state_sid_index
    on ba_post (type, uid, state, sid);
CREATE INDEX IF NOT EXISTS ba_likes_rev_type_uid_index
    on ba_likes_rev (type, uid);
CREATE INDEX IF NOT EXISTS ba_likes_rev_type_pid_uid_index
    on ba_likes_rev (type, pid, uid);
CREATE INDEX IF NOT EXISTS ba_cache_file_commit_cid_index
    on ba_cache_file_commit (cid);
CREATE INDEX IF NOT EXISTS ba_cache_file_commit_fid_index
    on ba_cache_file_commit (fid);
CREATE INDEX IF NOT EXISTS ba_topic_type_uid_state_sid_index
    on ba_topic (type, uid, state, sid);
CREATE INDEX IF NOT EXISTS ba_topic_type_uid_state_sid_date_index
    on ba_topic (type, uid, state, sid, dateline);
CREATE INDEX IF NOT EXISTS ba_post_type_uid_state_sid_date_index
    on ba_post (type, uid, state, sid, dateline);
