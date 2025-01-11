drop index if exists ba_topic_type_uid_state_sid_index;
create index ba_topic_type_uid_state_sid_index
    on ba_topic (type, uid, state, sid);


drop index if exists ba_topic_type_uid_state_sid_date_index;
create index ba_topic_type_uid_state_sid_date_index
    on ba_topic (type, uid, state, sid, dateline);

drop index if exists ba_post_type_uid_state_sid_date_index;
create index ba_post_type_uid_state_sid_date_index
    on ba_post (type, uid, state, sid, dateline);

