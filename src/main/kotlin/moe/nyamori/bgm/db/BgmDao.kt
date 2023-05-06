package moe.nyamori.bgm.db

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.model.*
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
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
    fun getMetaData(@Bind("k") k: String): String

    fun updatePrevPersistedCommitId(prevPersistRevId: String): Int {
        return upsertMetaData(Config.BGM_ARCHIVE_DB_META_KEY_PREV_PERSISTED_JSON_COMMIT_REV_ID, prevPersistRevId)
    }

    fun getPrevPersistedCommitId(): String {
        return getMetaData(Config.BGM_ARCHIVE_DB_META_KEY_PREV_PERSISTED_JSON_COMMIT_REV_ID)
    }


    @SqlBatch(
        """
        insert into ba_user (id, username) values (
            :id,
            :username
        ) on conflict(id) do update set username = :username 
        """
    )
    @Transaction
    fun batchUpsertUser(@BindBean userList: Iterable<User>): IntArray

    @SqlBatch(
        """
        insert into ba_topic (type, id,uid,sid, dateline, state, last_post_pid, title) values (
            :typeId,
            :t.id,
            :t.uid,
            :t.sid,
            :t.dateline,
            :t.state,
            :t.lastPostPid,
            :t.title
        ) on conflict(type,id,uid) do update set
            sid = :t.sid,
            state = :t.state,
            last_post_pid = :t.lastPostPid,
            title = :t.title
    """
    )
    @Transaction
    fun batchUpsertTopic(@Bind("typeId") typeId: Int, @BindBean("t") topicList: Iterable<Topic>): IntArray

    @SqlBatch(
        """
        insert into ba_post (type, id, mid, uid,dateline,state) values (
            :typeId,
            :p.id,
            :p.mid,
            :p.user.id,
            :p.dateline,
            :p.state
        ) on conflict(type,id,mid,uid) do update set state = :p.state
    """
    )
    @Transaction
    fun batchUpsertPost(@Bind("typeId") typeId: Int, @BindBean("p") topicList: Iterable<Post>): IntArray

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

    @SqlBatch(
        """
        UPDATE ba_topic
        SET uid = :p.first -- positive
        WHERE 
          uid = :p.second -- negative
    """
    )
    fun updateNegativeUidInTopic(@BindBean("p") userList: Iterable<Pair<Int, Int>>): IntArray


    @SqlBatch(
        """
        UPDATE ba_post
        SET uid = :p.first -- positive
        WHERE 
          uid = :p.second -- negative
    """
    )
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
    fun updateNegativeSidInBlogTopic(@BindBean("p") userList: Iterable<Pair<Int, Int>>): IntArray

    @SqlBatch(
        """
            DELETE FROM ba_user WHERE id = :p.second;
        """
    )
    fun removeNegativeUidUser(@BindBean("p") userList: Iterable<Pair<Int, Int>>)

    @Transaction
    fun handleNegativeUid() {
        val negativeUidUsers = getNegativeUidUsers()
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

        updateNegativeUidInTopic(userList)
        updateNegativeUidInPost(userList)
        updateNegativeSidInBlogTopic(userList)
        removeNegativeUidUser(userList)
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
}