package moe.nyamori.bgm.db

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.util.StringHashingHelper
import org.eclipse.jgit.lib.Repository
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

    @SqlQuery(
        """
        select k, v from meta_data
        """
    )
    @RegisterKotlinMapper(MetaRow::class)
    fun getAllMetaData(): List<MetaRow>

    data class MetaRow(val k: String, val v: String)

    fun updatePrevPersistedCommitId(
        repo: Repository,
        prevPersistRevId: String
    ): Int {
        return upsertMetaData(
            Config.BGM_ARCHIVE_DB_META_KEY_PREV_PERSISTED_JSON_COMMIT_REV_ID
                    + StringHashingHelper.stringHash(repo.absolutePathWithoutDotGit()), prevPersistRevId
        )
    }

    fun getPrevPersistedCommitId(repo: Repository): String {
        return getMetaData(
            Config.BGM_ARCHIVE_DB_META_KEY_PREV_PERSISTED_JSON_COMMIT_REV_ID
                    + StringHashingHelper.stringHash(repo.absolutePathWithoutDotGit())
        ) ?: ""
    }


    @SqlBatch(
        """
        insert into ba_user (id, username, nickname) values (
            :id,
            :username,
            :nickname
        ) on conflict(id) do update set username = :username, nickname = coalesce(:nickname, nickname) 
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
    fun getValidUsernameListFromList(@BindList("l") l: List<String>): List<String>

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
    fun handleNegativeUid(): List<Pair<Int/*TYPE*/, Int /*Mid*/>> {
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

        // First upsert naming mapping for blog
        val upsertBlogCount = upsertPositiveSidForBlog()
        // Remove the negative one
        val deleteBlogCount = deleteNegativeSidForBlog()
        LOGGER.info("upsertBlogCount $upsertBlogCount , deleteBlogCount $deleteBlogCount")

        val canUpdate = preRemoveConflictSidInLikesRev(userList)
        updateNegativeUidInLikesRev(canUpdate)

        removeNegativeUidUser(userList)

        return emptyList()
    }

    @SqlUpdate(
        """
            insert into ba_space_naming_mapping (type, sid, name, display_name)
            select 100      as type,
                   id       as sid,
                   username as name,
                   nickname as display_name
            from ba_user
            inner join ba_space_naming_mapping on ba_space_naming_mapping.type = 100 and ba_space_naming_mapping.sid < 0 and ba_space_naming_mapping.name = ba_user.username
            where true
            on conflict(type,sid) do update set name         = excluded.name,
                                                display_name = excluded.display_name;
        """
    )
    @Transaction
    fun upsertPositiveSidForBlog():Int

    @SqlUpdate(
        """
        delete
        from ba_space_naming_mapping
        where type = 100
          and name in (select name
                       from (select type, name, count(*) count
                             from ba_space_naming_mapping
                             where type = 100
                             group by type, name
                             having count > 1))
          and sid < 0;
    """
    )
    @Transaction
    fun deleteNegativeSidForBlog():Int

    @SqlBatch(
        """
            update ba_likes_rev set uid = :t.first -- positive 
            where 1 = 1
                and type  = :t.third.type
                and mid   = :t.third.mid
                and pid   = :t.third.pid
                and value = :t.third.value
                and uid   = :t.second -- negative
        """
    )
    @Transaction
    fun updateNegativeUidInLikesRev(@BindBean("t") l: List<Triple<Int, Int, DeReplicaLikeRev>>)

    @SqlQuery(
        """
        select * from ba_likes_rev where uid = :t.first or uid = :t.second
    """
    )
    @RegisterKotlinMapper(LikeRevRow::class)
    fun selectLikeRevByUidPair(@BindBean("t") t: Pair<Int, Int>): List<LikeRevRow>

    @SqlUpdate(
        """
        delete from ba_likes_rev where 1=1
         and type = :t.type 
         and mid = :t.mid 
         and pid = :t.pid 
         and value = :t.value 
         and uid < 0
    """
    )
    @Transaction
    fun doRemoveConflictInLikesRev(@BindBean("t") t: DeReplicaLikeRev): Int

    data class DeReplicaLikeRev(
        val type: Int,
        val mid: Int,
        val pid: Int,
        val value: Int
    )

    fun preRemoveConflictSidInLikesRev(uidPairList: List<Pair<Int/*pos*/, Int/*neg*/>>): List<Triple<Int, Int, DeReplicaLikeRev>> {
        return uidPairList.mapNotNull { i ->
            val deReplicaLikeRev = selectLikeRevByUidPair(i)
                .groupBy {
                    DeReplicaLikeRev(
                        it.type,
                        it.mid,
                        it.pid,
                        it.value
                    )
                }
            if (deReplicaLikeRev.isEmpty()) return@mapNotNull null
            val canRemove = deReplicaLikeRev.filter {
                it.value.isNotEmpty()
                        && it.value.size == 2
                        && it.value.map { it.total }.distinct().size == 1
            }.keys
            val canUpdate = deReplicaLikeRev.filter {
                it.value.isNotEmpty()
                        && it.value.size == 1
                        && it.value[0].uid < 0
            }.map {
                Triple(i.first, i.second, it.key)
            }
            canRemove.forEach { j ->
                val res = doRemoveConflictInLikesRev(j)
                LOGGER.info("$res rows removed from ba_likes_rev by key $j")
            }
            return@mapNotNull canUpdate
        }.flatten()
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
            from ba_likes bl -- So far ba_likes is the smallest table
                     inner join ba_topic bt on bl.type = bt.type and bl.mid = bt.id and bt.state != 1
                     inner join ba_post bp on bp.id = bl.pid and bp.type = bl.type and bp.state != 1
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
                from ba_user bu
                     inner join ba_post bp on bp.uid = bu.id
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
                   bt.state as topic_state,
                   bsnm.display_name as space_display_name,
                   rank() over (partition by bp.type,bp.mid, bp.uid order by bp.dateline desc,bp.id desc) rank_reply_asc
            from ba_user bu
            inner join ba_post bp on bu.id = bp.uid
            inner join ba_topic bt on bp.mid = bt.id and bp.type = bt.type and bt.top_post_pid!=bp.id
            left  join ba_space_naming_mapping bsnm on bt.type = bsnm.type and bt.sid = bsnm.sid 
            where bp.type = :t
              -- and bt.state != 1
              -- and bp.state != 1
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
                     bp.dateline       as last_update_time,
                     bu.username       as username,
                     bsnm.display_name as space_display_name,
                     rank() over (partition by bt.type,bt.uid order by bp.dateline desc, bp.id desc) rank_last_reply
              from ba_user bu
                       inner join ba_topic bt on bu.id = bt.uid
                       inner join ba_post bp on bt.last_post_pid = bp.id and bt.type = bp.type
                       left  join ba_space_naming_mapping bsnm on bt.type = bsnm.type and bt.sid = bsnm.sid 
              where bt.type = :t
                and bt.state != 1
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


    // Like rev
    @SqlQuery(
        """
            select * from ba_user where username in (<l>)
        """
    )
    @RegisterKotlinMapper(UserRow::class)
    fun getUserRowByUsernameList(
        @BindList("l") l: Iterable<String>
    ): List<UserRow>

    @SqlQuery(
        """
        select * from ba_likes_rev where type = :type and mid = :topicId
    """
    )
    @RegisterKotlinMapper(PostRow::class)
    fun getLikeRevListByTypeAndTopicId(@Bind("type") type: Int, @Bind("topicId") topicId: Int): List<LikeRevRow>

    @SqlBatch(
        """
            insert into ba_likes_rev (type, mid, pid, value, uid, total)
            values (:type,
                    :mid,
                    :pid,
                    :value,
                    :uid,
                    :total
            ) on conflict(type,mid,pid,value,uid) do update set total = :total
        """
    )
    @Transaction
    fun batchUpsertLikesRev(@BindBean likeList: Iterable<LikeRevRow>): IntArray


    @SqlQuery(
        """
            select bl.type     as type,
                   bl.value    as face_key,
                   bl.total    as face_count,
                   bu.username as username,
                   sum(bl.total)  count
            from  ba_user bu 
                     inner join ba_likes_rev bl on bl.uid = bu.id  -- bl_r will be larger and larger
                     inner join ba_topic bt on bl.type = bt.type and bl.mid = bt.id and bt.state != 1
                     inner join ba_post bp on bp.id = bl.pid and bp.type = bl.type and bp.state != 1
            where bu.username in
                  (<l>)
              and bl.type = :t
            group by bl.type, face_key, username
            having count > 0;
        """
    )
    @RegisterKotlinMapper(VLikesSumRow::class)
    fun getLikeRevSumByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VLikesSumRow>


    @SqlQuery(
        """
        select bl.type           as type,
               bu.username       as username,
               bsnm.name         as space_name,
               bsnm.display_name as space_display_name,
               sum(bl.total)        count
        from ba_likes_rev bl -- So far ba_likes is the smallest table
                 inner join ba_topic bt on bl.type = bt.type and bl.mid = bt.id and bt.state != 1
                 inner join ba_space_naming_mapping bsnm on bt.type = bsnm.type and bt.sid = bsnm.sid
                 -- inner join ba_post bp on bp.id = bl.pid and bp.type = bl.type and bp.state != 1
                 inner join ba_user bu on bl.uid = bu.id
        where bu.username in
              (<l>)
          and bl.type = :t
        group by bl.type, username, bsnm.name
        having count > 0;
    """
    )
    @RegisterKotlinMapper(VLikeRevCountSpaceRow::class)
    fun getLikeRevStatForSpaceByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VLikeRevCountSpaceRow>


    @SqlQuery(
        """
            select bl.type           as type,
                   bu.username       as username,
                   bsnm.name         as space_name,
                   bsnm.display_name as space_display_name,
                   sum(bl.total)        count
            from ba_likes bl -- So far ba_likes is the smallest table
                     inner join ba_topic bt on bl.type = bt.type and bl.mid = bt.id and bt.state != 1
                     inner join ba_space_naming_mapping bsnm on bt.type = bsnm.type and bt.sid = bsnm.sid
                     inner join ba_post bp on bp.id = bl.pid and bp.type = bl.type and bp.state != 1
                     inner join ba_user bu on bp.uid = bu.id
            where bu.username in
                  (<l>)
              and bl.type = :t
            group by bl.type, username, bsnm.name
            having count > 0;
        """
    )
    @RegisterKotlinMapper(VLikeCountSpaceRow::class)
    fun getLikeStatForSpaceByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VLikeCountSpaceRow>


    @SqlQuery(
        """
        select *
        from (select bp.type,
                     bp.mid,
                     bp.id       as  pid,
                     bu.username,
                     blr.value   as  face_key,
                     bt.title    as  title,
                     bp.dateline as  dateline,
                     bsnm.display_name   as  space_display_name,
                     rank() over (partition by bp.type,bp.mid, blr.uid order by bp.dateline desc,bp.id desc) rank_like_rev_asc
              from ba_user bu
                       inner join ba_likes_rev blr on blr.uid = bu.id
                       inner join ba_post bp on blr.type = bp.type and blr.mid = bp.mid and blr.pid = bp.id
                       inner join ba_topic bt on bp.type = bt.type and bp.mid = bt.id
                       left  join ba_space_naming_mapping bsnm on bt.type = bsnm.type and bt.sid = bsnm.sid
              where blr.type = :t
                and blr.total != 0
                and bp.state!=1 and bp.state!=16
                and bt.state!=1
                and blr.uid in (select bu.id
                                from ba_user bu
                                where bu.username in (<l>)))
        where 1 = 1
          and rank_like_rev_asc <=5
          and dateline >= ((select unixepoch() - 86400 * 365 * 3))
    """
    )
    @RegisterKotlinMapper(VUserLatestLikeRevRow::class)
    fun getUserLatestLikeRevByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VUserLatestLikeRevRow>


    @SqlQuery("select id from ba_topic where type = ?")
    fun getAllTopicIdByType(type: Int): List<Int>

    @SqlUpdate("delete from meta_data")
    @Transaction
    fun _TRUNCATE_ALL_META():Int
}

