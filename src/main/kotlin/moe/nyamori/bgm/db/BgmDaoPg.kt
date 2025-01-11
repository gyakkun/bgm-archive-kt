package moe.nyamori.bgm.db

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.model.Like
import moe.nyamori.bgm.model.Post
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.model.User
import moe.nyamori.bgm.util.StringHashingHelper.hashedAbsolutePathWithoutGitId
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
interface BgmDaoPg : Transactional<BgmDaoPg>, IBgmDao {

    val LOGGER: Logger
        get() = LoggerFactory.getLogger(BgmDaoPg::class.java)

    @SqlQuery(
        """
        select 1
    """
    )
    override fun healthCheck(): Int


    @SqlUpdate(
        """
        insert into meta_data (k,v) values (:k,:v) on conflict(k) do update set v = :v
        """
    )
    @Transaction
    override fun upsertMetaData(@Bind("k") k: String, @Bind("v") v: String): Int

    @SqlQuery(
        """
        select v from meta_data where k = :k
        """
    )
    override fun getMetaData(@Bind("k") k: String): String?

    @SqlQuery(
        """
        select k, v from meta_data
        """
    )
    @RegisterKotlinMapper(MetaRow::class)
    override fun getAllMetaData(): List<MetaRow>

    override fun updatePrevPersistedCommitId(
        repo: Repository,
        prevPersistRevId: String
    ): Int {
        return upsertMetaData(
            Config.dbMetaKeyPrevPersistedJsonCommitRevId
                    + repo.hashedAbsolutePathWithoutGitId(), prevPersistRevId
        )
    }


    override fun getPrevPersistedCommitId(repo: Repository): String {
        return getMetaData(
            Config.dbMetaKeyPrevPersistedJsonCommitRevId
                    + repo.hashedAbsolutePathWithoutGitId()
        ) ?: ""
    }

    override fun getPrevCachedCommitId(repo: Repository): String? {
        return getMetaData(
            Config.dbMetaKeyPrevCachedCommitRevId
                    + repo.hashedAbsolutePathWithoutGitId()
        )
    }

    override fun updatePrevCachedCommitId(
        repo: Repository,
        prevCachedCommitId: String
    ): Int {
        return upsertMetaData(
            Config.dbMetaKeyPrevCachedCommitRevId
                    + repo.hashedAbsolutePathWithoutGitId(), prevCachedCommitId
        )
    }

    @SqlBatch(
        """
         insert into ba_cache_file_relative_path (file_relative_path) values (:t)
         on conflict do nothing;
     """
    )
    @Transaction
    // WARN: No generated key back from sqlite jdbc driver
    override fun batchUpsertFileRelativePathForCache(@Bind("t") fileRelativePaths: List<String>): IntArray
//    {
//        return Dao.bgmDao.withHandle<List<Long>, Exception> {
//            val batch = it.prepareBatch(
//                """
//                insert into ba_cache_file_relative_path (file_relative_path) values (?)
//                on conflict do nothing;
//        """.trimIndent()
//            );
//            fileRelativePaths.forEach { batch.add(it) }
//            val res = batch.executePreparedBatch("id")
//            return@withHandle res.mapTo(Long::class.java).list()
//        }
//    }

    @SqlUpdate(
        """
        insert into ba_cache_repo_commit (repo_id, commit_id) values (:repoId, :commitId)
        on conflict do nothing;
    """
    )
    @Transaction
    override fun insertRepoCommitForCache(@Bind("repoId") repoId: Long, @Bind("commitId") commitId: String): Int

    @SqlBatch(
        """
        insert into ba_cache_file_commit (fid, cid) values (
         (select id from ba_cache_file_relative_path where file_relative_path = :frp ), 
        (select id from ba_cache_repo_commit where repo_id = :rid and commit_id = :cid))
        on conflict do nothing;
    """
    )
    @Transaction
    override fun batchUpsertFileCommitForCache(
        @Bind("frp") frp: Iterable<String>,
        @Bind("rid") rid: Long,
        @Bind("cid") cid: String
    ): IntArray

    @SqlQuery(
        """
            select bcrc.repo_id, bcrc.commit_id
            from ba_cache_file_commit bcfc
                     inner join ba_cache_file_relative_path bcfrp on bcfc.fid = bcfrp.id
                     inner join ba_cache_repo_commit bcrc on bcfc.cid = bcrc.id
            where bcfrp.file_relative_path = :frp
    """
    )
    override fun queryRepoCommitForCacheByFileRelativePath(@Bind("frp") frp: String): List<RepoIdCommitId>


    @SqlBatch(
        """
        insert into ba_user (id, username, nickname) values (
            :id,
            :username,
            :nickname
        ) on conflict(id) do update set username = :username, nickname = coalesce(:nickname, excluded.nickname)
        """
    )
    @Transaction
    override fun batchUpsertUser(@BindBean userList: Iterable<User>): IntArray

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
            uid = coalesce(:t.uid, excluded.uid),
            sid = coalesce(:t.sid, excluded.sid),
            dateline = coalesce(:t.dateline,excluded.dateline),
            state = :t.state,
            top_post_pid = coalesce(:t.topPostPid, excluded.top_post_pid),
            last_post_pid = coalesce(:t.lastPostPid, excluded.last_post_pid, -1),
            title = coalesce(:t.title , excluded.title)
    """
    )
    @Transaction
    override fun batchUpsertTopic(@Bind("typeId") typeId: Int, @BindBean("t") topicList: Iterable<Topic>): IntArray

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
    override fun batchUpsertPost(
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
    override fun batchUpsertPostRow(
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
    override fun batchUpsertLikes(@BindBean likeList: Iterable<Like>): IntArray


    @SqlQuery(
        """
            select *
            from ba_user
            where username in (select username
                               from (select count(*) c, username
                                     from ba_user
                                     group by username
                                     having count(*) >= 2) as cu);
        """
    )
    @RegisterKotlinMapper(User::class)
    override fun getNegativeUidUsers(): List<User>

    @SqlQuery(
        """
        select distinct username from ba_user where username in (<l>) and username is not null
    """
    )
    override fun getValidUsernameListFromList(@BindList("l") l: List<String>): List<String>

    @SqlBatch(
        """
        UPDATE ba_topic
        SET uid = :p.first -- positive
        WHERE 
          uid = :p.second -- negative
    """
    )
    @Transaction
    override fun updateNegativeUidInTopic(@BindBean("p") userList: Iterable<Pair<Int, Int>>): IntArray


    @SqlBatch(
        """
        UPDATE ba_post
        SET uid = :p.first -- positive
        WHERE 
          uid = :p.second -- negative
    """
    )
    @Transaction
    override fun updateNegativeUidInPost(@BindBean("p") userList: Iterable<Pair<Int, Int>>): IntArray

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
    override fun updateNegativeSidInBlogTopic(@BindBean("p") userList: Iterable<Pair<Int, Int>>): IntArray

    @SqlBatch(
        """
            DELETE FROM ba_user WHERE id = :p.second;
        """
    )
    @Transaction
    override fun removeNegativeUidUser(@BindBean("p") userList: Iterable<Pair<Int, Int>>)

    @Transaction
    override fun handleNegativeUid(): List<Pair<Int/*TYPE*/, Int /*Mid*/>> {
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
    override fun upsertPositiveSidForBlog():Int

    @SqlUpdate(
        """
        delete
        from ba_space_naming_mapping
        where type = 100
          and name in (select name
                       from (select type, name
                             from ba_space_naming_mapping
                             where type = 100
                             group by type, name
                             having count(*) > 1) as tn)
          and sid < 0;
    """
    )
    @Transaction
    override fun deleteNegativeSidForBlog():Int

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
    override fun updateNegativeUidInLikesRev(@BindBean("t") l: List<Triple<Int, Int, DeReplicaLikeRev>>)

    @SqlQuery(
        """
        select * from ba_likes_rev where uid = :t.first or uid = :t.second
    """
    )
    @RegisterKotlinMapper(LikeRevRow::class)
    override fun selectLikeRevByUidPair(@BindBean("t") t: Pair<Int, Int>): List<LikeRevRow>

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
    override fun doRemoveConflictInLikesRev(@BindBean("t") t: DeReplicaLikeRev): Int

    override fun preRemoveConflictSidInLikesRev(uidPairList: List<Pair<Int/*pos*/, Int/*neg*/>>): List<Triple<Int, Int, DeReplicaLikeRev>> {
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
        from (select bat.* from ba_topic bat where  uid = :t.first  or uid = :t.second  group by type, id having count(*) >= 2) as tmp
    """
    )
    @RegisterKotlinMapper(DeRepetitiveTopicData::class)
    override fun selectTopicTypeAndIdByUidListAndGroupByPk(@BindBean("t") t: Pair<Int, Int>): List<DeRepetitiveTopicData>

    @SqlUpdate(
        """
        delete from ba_topic where type = :t.type and id = :t.id and uid < 0
    """
    )
    @Transaction
    override fun doRemoveConflictTopic(@BindBean("t") t: DeRepetitiveTopicData): Int

    override fun preRemoveConflictTopic(userList: List<Pair<Int, Int>>) {
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
        from (select bap.* from ba_post bap where  uid = :t.first or uid = :t.second  group by type, id, mid having count(*) >= 2) as tmp
    """
    )
    @RegisterKotlinMapper(DeRepetitivePostData::class)
    override fun selectPostTypeAndIdAndMidByUidListAndGroupByPk(@BindBean("t") t: Pair<Int, Int>): List<DeRepetitivePostData>

    @SqlUpdate(
        """
        delete from ba_post where type = :t.type and id = :t.id and mid = :t.mid and uid < 0
    """
    )
    @Transaction
    override fun doRemoveConflictPost(@BindBean("t") t: DeRepetitivePostData): Int

    override fun preRemoveConflictPost(userList: List<Pair<Int, Int>>) {
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
        from (select bat.* from ba_topic bat where type = 100 and ( uid = :t.first or uid = :t.second ) group by type, id, sid having count(*) >= 2) as tmp
    """
    )
    @RegisterKotlinMapper(DeRepetitiveBlogTopicData::class)
    override fun selectBlogTopicTypeAndIdByUidListAndGroupByTypeIdAndSid(@BindBean("t") t: Pair<Int, Int>): List<DeRepetitiveBlogTopicData>

    @SqlUpdate(
        """
        delete from ba_topic where type = 100 and id = :t.id and sid < 0
    """
    )
    @Transaction
    override fun doRemoveConflictBlogTopic(@BindBean("t") t: DeRepetitiveBlogTopicData): Int
    override fun preRemoveConflictSidInBlog(userList: List<Pair<Int, Int>>) {
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
    override fun upsertSidAlias(@BindBean("t") sidAliasMappingList: Iterable<SpaceNameMappingData>): IntArray

    @SqlBatch(
        """
            insert into ba_blog_subject_id_mapping (blog_topic_id, subject_id) values (
            :t.first,
            :t.second
        ) on conflict(blog_topic_id, subject_id) do nothing
        """
    )
    @Transaction
    override fun upsertBlogSubjectIdMapping(@BindBean("t") blogSubjectIdMappingList: Iterable<Pair<Int, Int>>)

    @SqlBatch(
        """
            insert into ba_blog_tag_mapping (blog_topic_id, tag) values (
            :t.first,
            :t.second
        ) on conflict(blog_topic_id, tag) do nothing
        """
    )
    @Transaction
    override fun upsertBlogTagMapping(@BindBean("t") blogSubjectIdMappingList: Iterable<Pair<Int, String>>)

    @SqlQuery(
        """
        select * from ba_post where type = :type and mid = :topicId
    """
    )
    @RegisterKotlinMapper(PostRow::class)
    override fun getPostListByTypeAndTopicId(@Bind("type") type: Int, @Bind("topicId") topicId: Int): List<PostRow>

    @SqlQuery(
        """
        select * from ba_likes where type = :type and mid = :topicId
    """
    )
    @RegisterKotlinMapper(PostRow::class)
    override fun getLikeListByTypeAndTopicId(@Bind("type") type: Int, @Bind("topicId") topicId: Int): List<LikeRow>

    @SqlQuery(
        """
        select * from ba_topic where type = :type and id = :topicId
    """
    )
    @RegisterKotlinMapper(PostRow::class)
    override fun getTopicListByTypeAndTopicId(@Bind("type") type: Int, @Bind("topicId") topicId: Int): List<TopicRow>


    // View queries
    @SqlQuery(
        """
            select * from ba_v_all_post_count_group_by_type_uid_state where type = :t and username in (<l>)
        """
    )
    @RegisterKotlinMapper(VAllPostCountRow::class)
    override fun getAllPostCountByTypeAndUsernameList(
        @Bind("t") t: Int,
        @BindList("l") l: Iterable<String>
    ): List<VAllPostCountRow>

    @SqlQuery(
        """
            select * from ba_v_all_topic_count_group_by_type_uid_state where type = :t and username in (<l>)
        """
    )
    @RegisterKotlinMapper(VAllTopicCountRow::class)
    override fun getAllTopicCountByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VAllTopicCountRow>


    @SqlQuery(
        """
            select * from ba_v_all_post_count_30d_group_by_type_uid_state where type = :t and username in (<l>)
        """
    )
    @RegisterKotlinMapper(VAllPostCount30dRow::class)
    override fun getAllPostCount30dByTypeAndUsernameList(
        @Bind("t") t: Int,
        @BindList("l") l: Iterable<String>
    ): List<VAllPostCount30dRow>

    @SqlQuery(
        """
            select * from ba_v_all_topic_count_30d_group_by_type_uid_state where type = :t and username in (<l>)
        """
    )
    @RegisterKotlinMapper(VAllTopicCount30dRow::class)
    override fun getAllTopicCount30dByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VAllTopicCount30dRow>

    @SqlQuery(
        """
            select * from ba_v_all_post_count_7d_group_by_type_uid_state where type = :t and username in (<l>)
        """
    )
    @RegisterKotlinMapper(VAllPostCount7dRow::class)
    override fun getAllPostCount7dByTypeAndUsernameList(
        @Bind("t") t: Int,
        @BindList("l") l: Iterable<String>
    ): List<VAllPostCount7dRow>

    @SqlQuery(
        """
            select * from ba_v_all_topic_count_7d_group_by_type_uid_state where type = :t and username in (<l>)
        """
    )
    @RegisterKotlinMapper(VAllTopicCount7dRow::class)
    override fun getAllTopicCount7dByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VAllTopicCount7dRow>


    @SqlQuery(
        """
            select bl.type     as type,
                   bl.value    as face_key,
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
            having sum(bl.total) > 0;
        """
    )
    @RegisterKotlinMapper(VLikesSumRow::class)
    override fun getLikesSumByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VLikesSumRow>

    @SqlQuery(
        """
            select * from ba_v_post_count_group_by_type_space_uid_state where username in (<l>) and type=:t
        """
    )
    @RegisterKotlinMapper(VPostCountSpaceRow::class)
    override fun getPostCountSpaceByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VPostCountSpaceRow>

    @SqlQuery(
        """
            select * from ba_v_post_count_30d_group_by_type_space_uid_state where username in (<l>) and type=:t
        """
    )
    @RegisterKotlinMapper(VPostCountSpaceRow::class)
    override fun getPostCountSpace30dByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VPostCountSpaceRow>

    @SqlQuery(
        """
            select * from ba_v_post_count_7d_group_by_type_space_uid_state where username in (<l>) and type=:t
        """
    )
    @RegisterKotlinMapper(VPostCountSpaceRow::class)
    override fun getPostCountSpace7dByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VPostCountSpaceRow>


    @SqlQuery(
        """
            select * from ba_v_topic_count_group_by_type_space_uid_state where username in (<l>) and type = :t  
        """
    )
    @RegisterKotlinMapper(VTopicCountSpaceRow::class)
    override fun getTopicCountSpaceByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VTopicCountSpaceRow>


    @SqlQuery(
        """
            select * from ba_v_topic_count_30d_group_by_type_space_uid_state where type = :t and username in (<l>)
        """
    )
    @RegisterKotlinMapper(VTopicCountSpaceRow::class)
    override fun getTopicCountSpace30dByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VTopicCountSpaceRow>


    @SqlQuery(
        """
            select * from ba_v_topic_count_7d_group_by_type_space_uid_state where username in (<l>) and type = :t 
        """
    )
    @RegisterKotlinMapper(VTopicCountSpaceRow::class)
    override fun getTopicCountSpace7dByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VTopicCountSpaceRow>


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
            where rank_reply_asc = 1 and dateline >= (((select EXTRACT(EPOCH FROM NOW())::bigint) - 86400*365*3))
        """
    )
    @RegisterKotlinMapper(VUserLastReplyTopicRow::class)
    override fun getUserLastReplyTopicByTypeAndUsernameList(
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
              where bu.username in (<l>) 
                and bt.type = :t
                and bt.state != 1
                and bt.dateline >= (((select EXTRACT(EPOCH FROM NOW())::bigint) - 86400 * 365 * 3)))
        where rank_last_reply <= 10
        """
    )
    @RegisterKotlinMapper(VUserLatestCreateTopicRow::class)
    override fun getUserLatestCreateTopicAndUsernameList(
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
    override fun getUserRowByUsernameList(
        @BindList("l") l: Iterable<String>
    ): List<UserRow>

    @SqlQuery(
        """
        select * from ba_likes_rev where type = :type and mid = :topicId
    """
    )
    @RegisterKotlinMapper(PostRow::class)
    override fun getLikeRevListByTypeAndTopicId(@Bind("type") type: Int, @Bind("topicId") topicId: Int): List<LikeRevRow>

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
    override fun batchUpsertLikesRev(@BindBean likeList: Iterable<LikeRevRow>): IntArray


    @SqlQuery(
        """
            select bl.type     as type,
                   bl.value    as face_key,
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
            having sum(bl.total) > 0;
        """
    )
    @RegisterKotlinMapper(VLikesSumRow::class)
    override fun getLikeRevSumByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VLikesSumRow>


    @SqlQuery(
        """
            with tmp as (select bl.type             as type,
                                bu.username         as username,
                                bsnm.sid            as sid,
                                sum(bl.total)       as count
                         from ba_likes_rev bl -- So far ba_likes is the smallest table
                                  inner join ba_topic bt on bl.type = bt.type and bl.mid = bt.id and bt.state != 1
                                  inner join ba_space_naming_mapping bsnm on bt.type = bsnm.type and bt.sid = bsnm.sid
                             -- inner join ba_post bp on bp.id = bl.pid and bp.type = bl.type and bp.state != 1
                                  inner join ba_user bu on bl.uid = bu.id
                         where bu.username in
                               (<l>)
                           and bl.type = :t
                         group by bl.type, username, bsnm.sid
                         having sum(bl.total) > 0)
            select tmp.type as type, tmp.username as username, bsnm2.name as space_name , bsnm2.display_name as space_display_name,  tmp.count as count
            from tmp
                     inner join ba_space_naming_mapping bsnm2
                                on tmp.type = bsnm2.type and tmp.sid = bsnm2.sid;
    """
    )
    @RegisterKotlinMapper(VLikeRevCountSpaceRow::class)
    override fun getLikeRevStatForSpaceByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VLikeRevCountSpaceRow>


    @SqlQuery(
        """
            with tmp as (select bl.type       as type,
                                bu.username   as username,
                                bsnm.sid      as sid,
                                sum(bl.total) as count
                         from ba_likes_rev bl -- So far ba_likes is the smallest table
                                  inner join ba_topic bt on bl.type = bt.type and bl.mid = bt.id and bt.state != 1
                                  inner join ba_space_naming_mapping bsnm on bt.type = bsnm.type and bt.sid = bsnm.sid
                             -- inner join ba_post bp on bp.id = bl.pid and bp.type = bl.type and bp.state != 1
                                  inner join ba_user bu on bl.uid = bu.id
                         where bu.username in
                               (<l>)
                           and bl.type = :t
                         group by bl.type, username, bsnm.sid
                         having sum(bl.total) > 0)
            select tmp.*,
                   bsnm2.display_name as space_display_name,
                   bsnm2.name         as space_name
            from tmp
                     inner join ba_space_naming_mapping bsnm2
                                on bsnm2.type = tmp.type and bsnm2.sid = tmp.sid;
        """
    )
    @RegisterKotlinMapper(VLikeCountSpaceRow::class)
    override fun getLikeStatForSpaceByTypeAndUsernameList(
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
          and dateline >= (((select EXTRACT(EPOCH FROM NOW())::bigint) - 86400 * 365 * 3))
    """
    )
    @RegisterKotlinMapper(VUserLatestLikeRevRow::class)
    override fun getUserLatestLikeRevByTypeAndUsernameList(
        @Bind("t") type: Int,
        @BindList("l") l: Iterable<String>
    ): List<VUserLatestLikeRevRow>


    @SqlQuery("select id from ba_topic where type = ?")
    override fun getAllTopicIdByType(type: Int): ArrayList<Int>

    @SqlQuery("select max(id) from ba_topic where type = ?")
    override fun getMaxTopicIdByType(type: Int): Int

    @SqlUpdate("delete from meta_data")
    @Transaction
    override fun _TRUNCATE_ALL_META():Int
}

