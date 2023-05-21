package moe.nyamori.bgm.db

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.model.*
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transaction
import org.jdbi.v3.sqlobject.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@JvmDefaultWithCompatibility
interface BgmDao : Transactional<BgmDao> {

    val LOGGER: Logger
        get() = LoggerFactory.getLogger(BgmDao::class.java)

    @SqlQuery(
        """
        select 1
    """
    )
    fun healthCheck(): Int


    @SqlUpdate(
        """
        insert into meta_data (k,v) values (:k,:v) on conflict(k) do update set v = :v
        """
    )
    @Transaction
    fun upsertMetaData(@Bind("k") k: String, @Bind("v") v: String): Int

    @SqlQuery(
        """
        select v from meta_data where k = :k
        """
    )
    fun getMetaData(@Bind("k") k: String): String?

    fun updatePrevPersistedCommitId(prevPersistRevId: String): Int {
        return upsertMetaData(Config.BGM_ARCHIVE_DB_META_KEY_PREV_PERSISTED_JSON_COMMIT_REV_ID, prevPersistRevId)
    }

    fun getPrevPersistedCommitId(): String {
        return getMetaData(Config.BGM_ARCHIVE_DB_META_KEY_PREV_PERSISTED_JSON_COMMIT_REV_ID) ?: ""
    }


    @SqlBatch(
        """
        insert into ba_user (id, username, nickname) values (
            :id,
            :username,
            :nickname
        ) on conflict(id) do update set username = :username, nickname = :nickname 
        """
    )
    @Transaction
    fun batchUpsertUser(@BindBean userList: Iterable<User>): IntArray

    @SqlBatch(
        """
        insert into ba_topic (type, id,uid,sid, dateline, state, top_post_pid, last_post_pid, title) values (
            :typeId,
            :t.id,
            :t.uid,
            :t.sid,
            :t.dateline,
            :t.state,
            :t.topPostPid,
            :t.lastPostPid,
            :t.title
        ) on conflict(type,id) do update set
            uid = coalesce(:t.uid, uid),
            sid = coalesce(:t.sid, sid),
            dateline = coalesce(:t.dateline,dateline),
            state = :t.state,
            top_post_pid = coalesce(:t.topPostPid, top_post_pid),
            last_post_pid = coalesce(:t.lastPostPid, last_post_pid, -1),
            title = coalesce(:t.title , title)
    """
    )
    @Transaction
    fun batchUpsertTopic(@Bind("typeId") typeId: Int, @BindBean("t") topicList: Iterable<Topic>): IntArray

    @SqlBatch(
        """
        insert into ba_post (type, id, mid, uid,dateline,state,sid) values (
            :typeId,
            :p.id,
            :p.mid,
            :p.user.id,
            :p.dateline,
            :p.state,
            coalesce(:sid, 0)
        ) on conflict(type,id,mid) do update set 
            state = :p.state
    """
    )
    @Transaction
    fun batchUpsertPost(
        @Bind("typeId") typeId: Int,
        @Bind("sid") sid: Int?,
        @BindBean("p") postList: Iterable<Post>
    ): IntArray

    @SqlBatch(
        """
        insert into ba_post (type, id, mid, uid,dateline,state,sid) values (
            :p.type,
            :p.id,
            :p.mid,
            :p.uid,
            :p.dateline,
            :p.state,
            coalesce(:p.sid, 0)
        ) on conflict(type,id,mid) do update set 
            state = :p.state
    """
    )
    @Transaction
    fun batchUpsertPostRow(
        @BindBean("p") postList: Iterable<PostRow>
    ): IntArray

    @SqlBatch(
        """
            insert into ba_likes (type, mid, pid, value, total)
            values (:type,
                    :mid,
                    :pid,
                    :value,
                    :total
            ) on conflict(type,mid,pid,value) do update set total = :total
        """
    )
    @Transaction
    fun batchUpsertLikes(@BindBean likeList: Iterable<Like>): IntArray


    @SqlQuery(
        """
        with a as (select username from ( select count(*) c, username
        from ba_user
        -- where id < 0
        group by username
        having c >= 2))
        select * from ba_user where username in a;
    """
    )
    @RegisterKotlinMapper(User::class)
    fun getNegativeUidUsers(): List<User>

    @SqlQuery(
        """
        select distinct username from ba_user where username in (<l>) and username is not null
    """
    )
    fun getValidUsernameListFromList(@BindList("l") l: List<String>):List<String>

    @SqlBatch(
        """
        UPDATE ba_topic
        SET uid = :p.first -- positive
        WHERE 
          uid = :p.second -- negative
    """
    )
    @Transaction
    fun updateNegativeUidInTopic(@BindBean("p") userList: Iterable<Pair<Int, Int>>): IntArray


    @SqlBatch(
        """
        UPDATE ba_post
        SET uid = :p.first -- positive
        WHERE 
          uid = :p.second -- negative
    """
    )
    @Transaction
    fun updateNegativeUidInPost(@BindBean("p") userList: Iterable<Pair<Int, Int>>): IntArray

    @SqlBatch(
        """
        UPDATE ba_topic
        SET sid = :p.first -- positive
        WHERE 
          sid = :p.second -- negative
          and type = 100 -- \$\{SpaceType.BLOG.id}
    """
    )
    @Transaction
    fun updateNegativeSidInBlogTopic(@BindBean("p") userList: Iterable<Pair<Int, Int>>): IntArray

    @SqlBatch(
        """
            DELETE FROM ba_user WHERE id = :p.second;
        """
    )
    @Transaction
    fun removeNegativeUidUser(@BindBean("p") userList: Iterable<Pair<Int, Int>>)

    @Transaction
    fun handleNegativeUid() {
        val negativeUidUsers = getNegativeUidUsers()
        LOGGER.info("Negative uid list: ${negativeUidUsers.map { Pair(it.username, it.id) }}")
        val userList = negativeUidUsers.groupBy { it.username }.map {
            if (it.value.size > 2) {
                LOGGER.error("${it.key} has more than 2 uids: ${it.value}")
                return@map null
            } else {
                var pos: Int? = null
                var neg: Int? = null
                it.value.forEach {
                    if (it.id!! > 0) pos = it.id!!
                    if (it.id!! < 0) neg = it.id!!
                }
                if (pos == null || neg == null) {
                    LOGGER.error("Something null when hanlding neg / pos uid: pos=$pos, neg=$neg, username = ${it.key}")
                    return@map null
                } else {
                    return@map Pair(pos!!, neg!!)
                }
            }
        }.filterNotNull()

        // If having conflict (more than 2 rows actually share the same primary key), first remove the row with neg uid
        // preRemoveConflictTopic(userList)
        updateNegativeUidInTopic(userList)
        // preRemoveConflictPost(userList)
        updateNegativeUidInPost(userList)
        // preRemoveConflictSidInBlog(userList)
        updateNegativeSidInBlogTopic(userList)
        // removeNegativeUidUser(userList)
        // No need to remove Negative Sid In Space Naming Mapping , otherwise it will conflict the unique key constraint
    }


    @SqlQuery(
        """
        select type, id
        from (select bat.*, count(*) c from ba_topic bat where  uid = :t.first  or uid = :t.second  group by type, id having c >= 2)
    """
    )
    @RegisterKotlinMapper(DeRepetitiveTopicData::class)
    fun selectTopicTypeAndIdByUidListAndGroupByPk(@BindBean("t") t: Pair<Int, Int>): List<DeRepetitiveTopicData>

    data class DeRepetitiveTopicData(val type: Int, val id: Int)

    @SqlUpdate(
        """
        delete from ba_topic where type = :t.type and id = :t.id and uid < 0
    """
    )
    @Transaction
    fun doRemoveConflictTopic(@BindBean("t") t: DeRepetitiveTopicData): Int

    fun preRemoveConflictTopic(userList: List<Pair<Int, Int>>) {
        for (i in userList) {
            val deRepetitiveTopicData = selectTopicTypeAndIdByUidListAndGroupByPk(i)
            if (deRepetitiveTopicData.isEmpty()) continue
            for (j in deRepetitiveTopicData) {
                val res = doRemoveConflictTopic(j)
                LOGGER.info("$res rows removed from ba_topic by key $j")
            }
        }
    }


    @SqlQuery(
        """
        select type, id, mid
        from (select bap.*, count(*) c from ba_post bap where  uid = :t.first or uid = :t.second  group by type, id, mid having c >= 2)
    """
    )
    @RegisterKotlinMapper(DeRepetitivePostData::class)
    fun selectPostTypeAndIdAndMidByUidListAndGroupByPk(@BindBean("t") t: Pair<Int, Int>): List<DeRepetitivePostData>

    data class DeRepetitivePostData(val type: Int, val id: Int, val mid: Int)

    @SqlUpdate(
        """
        delete from ba_post where type = :t.type and id = :t.id and mid = :t.mid and uid < 0
    """
    )
    @Transaction
    fun doRemoveConflictPost(@BindBean("t") t: DeRepetitivePostData): Int

    fun preRemoveConflictPost(userList: List<Pair<Int, Int>>) {
        for (i in userList) {
            val deRepetitivePostData = selectPostTypeAndIdAndMidByUidListAndGroupByPk(i)
            if (deRepetitivePostData.isEmpty()) continue
            for (j in deRepetitivePostData) {
                val res = doRemoveConflictPost(j)
                LOGGER.info("$res rows removed from ba_topic by key $j")
            }
        }
    }


    @SqlQuery(
        """
        select type, id
        from (select bat.*, count(*) c from ba_topic bat where type = 100 and ( uid = :t.first or uid = :t.second ) group by type, id, sid having c >= 2)
    """
    )
    @RegisterKotlinMapper(DeRepetitiveBlogTopicData::class)
    fun selectBlogTopicTypeAndIdByUidListAndGroupByTypeIdAndSid(@BindBean("t") t: Pair<Int, Int>): List<DeRepetitiveBlogTopicData>

    data class DeRepetitiveBlogTopicData(val type: Int, val id: Int)

    @SqlUpdate(
        """
        delete from ba_topic where type = 100 and id = :t.id and sid < 0
    """
    )
    @Transaction
    fun doRemoveConflictBlogTopic(@BindBean("t") t: DeRepetitiveBlogTopicData): Int
    fun preRemoveConflictSidInBlog(userList: List<Pair<Int, Int>>) {
        for (i in userList) {
            val deRepetitiveBlogTopicData = selectBlogTopicTypeAndIdByUidListAndGroupByTypeIdAndSid(i)
            if (deRepetitiveBlogTopicData.isEmpty()) continue
            for (j in deRepetitiveBlogTopicData) {
                val res = doRemoveConflictBlogTopic(j)
                LOGGER.info("$res rows removed from ba_topic by key $j")
            }
        }
    }


    @SqlBatch(
        """
        insert into ba_space_naming_mapping (type,sid,name,display_name) values (
            :t.type,
            :t.sid,
            :t.name,
            :t.displayName
        ) on conflict(type,sid) do nothing
    """
    )
    @Transaction
    fun upsertSidAlias(@BindBean("t") sidAliasMappingList: Iterable<SpaceNameMappingData>): IntArray

    @SqlBatch(
        """
            insert into ba_blog_subject_id_mapping (blog_topic_id, subject_id) values (
            :t.first,
            :t.second
        ) on conflict(blog_topic_id, subject_id) do nothing
        """
    )
    @Transaction
    fun upsertBlogSubjectIdMapping(@BindBean("t") blogSubjectIdMappingList: Iterable<Pair<Int, Int>>)

    @SqlBatch(
        """
            insert into ba_blog_tag_mapping (blog_topic_id, tag) values (
            :t.first,
            :t.second
        ) on conflict(blog_topic_id, tag) do nothing
        """
    )
    @Transaction
    fun upsertBlogTagMapping(@BindBean("t") blogSubjectIdMappingList: Iterable<Pair<Int, String>>)

    @SqlQuery(
        """
        select * from ba_post where type = :type and mid = :topicId
    """
    )
    @RegisterKotlinMapper(PostRow::class)
    fun getPostListByTypeAndTopicId(@Bind("type") type: Int, @Bind("topicId") topicId: Int): List<PostRow>

    @SqlQuery(
        """
        select * from ba_likes where type = :type and mid = :topicId
    """
    )
    @RegisterKotlinMapper(PostRow::class)
    fun getLikeListByTypeAndTopicId(@Bind("type") type: Int, @Bind("topicId") topicId: Int): List<LikeRow>

    @SqlQuery(
        """
        select * from ba_topic where type = :type and id = :topicId
    """
    )
    @RegisterKotlinMapper(PostRow::class)
    fun getTopicListByTypeAndTopicId(@Bind("type") type: Int, @Bind("topicId") topicId: Int): List<TopicRow>


    // View queries

    @SqlQuery(
        """
            select * from ba_v_all_post_count_group_by_type_uid_state where username in (<l>)
        """
    )
    @RegisterKotlinMapper(VAllPostCountRow::class)
    fun getAllPostCountByUsernameList(
        @BindList("l") l: Iterable<String>
    ): List<VAllPostCountRow>

    fun getAllPostCountByTypeAndUsernameList(
        t: Int,
        l: Iterable<String>
    ): List<VAllPostCountRow> {
        return getAllPostCountByUsernameList(l)
            .filter { it.type == t }
    }

    @SqlQuery(
        """
            select * from ba_v_all_topic_count_group_by_type_uid_state where type = :t and username in (<l>)
        """
    )
    @RegisterKotlinMapper(VAllTopicCountRow::class)
    fun getAllTopicCountByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VAllTopicCountRow>

    @SqlQuery(
        """
            select bl.type     as type,
                   bl.value    as face_key,
                   bl.total    as face_count,
                   bu.username as username,
                   sum(bl.total)  count
            from ba_likes bl
                     inner join ba_topic bt on bl.type = bt.type and bl.mid = bt.id and bt.state != 1
                     inner join ba_post bp on bp.id = bl.pid and bp.type = bl.type
                     inner join ba_user bu on bp.uid = bu.id
            where bu.username in
                  (<l>)
              and bl.type = :t
            group by bl.type, face_key, username
            having count > 0;
        """
    )
    @RegisterKotlinMapper(VLikesSumRow::class)
    fun getLikesSumByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VLikesSumRow>

    @SqlQuery(
        """
            select bp.type, bp.uid, bsnm.name, bsnm.display_name, bu.username, bp.state, count(1) count
                from ba_post bp
                         inner join ba_user bu on bp.uid = bu.id
                         inner join ba_space_naming_mapping bsnm on bp.type = bsnm.type and bp.sid = bsnm.sid
                 where bu.username in (<l>) and bp.type=:t
                group by bp.type, bp.uid, bp.state, bp.sid;

        """
    )
    @RegisterKotlinMapper(VPostCountSpaceRow::class)
    fun getPostCountSpaceByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VPostCountSpaceRow>


    @SqlQuery(
        """
            select * from ba_v_topic_count_group_by_type_space_uid_state where username in (<l>)
        """
    )
    @RegisterKotlinMapper(VTopicCountSpaceRow::class)
    fun getTopicCountSpaceByUsernameList(
        @BindList("l") l: Iterable<String>
    ): List<VTopicCountSpaceRow>

    fun getTopicCountSpaceByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VTopicCountSpaceRow> {
        return getTopicCountSpaceByUsernameList(l)
            .filter { it.type == type }
    }

    @SqlQuery(
        """
            select * from
            (select bp.*,
                   bt.title,
                   bu.username,
                   rank() over (partition by bp.type,bp.mid, bp.uid order by bp.dateline desc,bp.id desc) rank_reply_asc
            from ba_post bp
            inner join ba_topic bt on bp.mid = bt.id and bp.type = bt.type and bt.top_post_pid!=bp.id
            inner join ba_user bu on bu.id = bp.uid
            where bp.type = :t
              and bp.uid in (select bu.id
                          from ba_user bu
                          where bu.username in (<l>)))
            where rank_reply_asc = 1 and dateline >= ((select unixepoch() - 86400*365*3))
        """
    )
    @RegisterKotlinMapper(VUserLastReplyTopicRow::class)
    fun getUserLastReplyTopicByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VUserLastReplyTopicRow>


    @SqlQuery(
        """
        select *
        from (select bt.*,
                     bp.dateline as                                                                  last_update_time,
                     bu.username as                                                                  username,
                     rank() over (partition by bt.type,bt.uid order by bp.dateline desc, bp.id desc) rank_last_reply
              from ba_topic bt
                       inner join ba_post bp on bt.last_post_pid = bp.id and bt.type = bp.type
                       inner join ba_user bu on bu.id = bt.uid
              where bt.type = :t
                and bu.username in (<l>)
                and bt.dateline >= ((select unixepoch() - 86400 * 365 * 3)))
        where rank_last_reply <= 10
        """
    )
    @RegisterKotlinMapper(VUserLatestCreateTopicRow::class)
    fun getUserLatestCreateTopicAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VUserLatestCreateTopicRow>


}

