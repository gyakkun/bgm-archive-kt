package moe.nyamori.bgm.db

import moe.nyamori.bgm.model.Like
import moe.nyamori.bgm.model.Post
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.model.User
import org.eclipse.jgit.lib.Repository

interface IBgmDao {

    fun healthCheck(): Int

    fun upsertMetaData(k: String, v: String): Int

    fun getMetaData(k: String): String?

    fun getAllMetaData(): List<MetaRow>

    fun updatePrevPersistedCommitId(
        repo: Repository,
        prevPersistRevId: String
    ): Int

    fun getPrevPersistedCommitId(repo: Repository): String

    fun getPrevCachedCommitId(repo: Repository): String?

    fun updatePrevCachedCommitId(
        repo: Repository,
        prevCachedCommitId: String
    ): Int

    fun batchUpsertFileRelativePathForCache(fileRelativePaths: List<String>): IntArray

    fun insertRepoCommitForCache(repoId: Long, commitId: String): Int

    fun batchUpsertFileCommitForCache(
        frp: Iterable<String>,
        rid: Long,
        cid: String
    ): IntArray

    fun queryRepoCommitForCacheByFileRelativePath(frp: String): List<RepoIdCommitId>

    fun batchUpsertUser(userList: Iterable<User>): IntArray

    fun batchUpsertTopic(typeId: Int, topicList: Iterable<Topic>): IntArray

    fun batchUpsertPost(
        typeId: Int,
        sid: Int?,
        postList: Iterable<Post>
    ): IntArray

    fun batchUpsertPostRow(
        postList: Iterable<PostRow>
    ): IntArray

    fun batchUpsertLikes(likeList: Iterable<Like>): IntArray

    fun getNegativeUidUsers(): List<User>

    fun getValidUsernameListFromList(l: List<String>): List<String>

    fun updateNegativeUidInTopic(userList: Iterable<Pair<Int, Int>>): IntArray

    fun updateNegativeUidInPost(userList: Iterable<Pair<Int, Int>>): IntArray

    fun updateNegativeSidInBlogTopic(userList: Iterable<Pair<Int, Int>>): IntArray

    fun removeNegativeUidUser(userList: Iterable<Pair<Int, Int>>)

    fun handleNegativeUid(): List<Pair<Int/*TYPE*/, Int /*Mid*/>>

    fun upsertPositiveSidForBlog(): Int

    fun deleteNegativeSidForBlog(): Int

    fun updateNegativeUidInLikesRev(l: List<Triple<Int, Int, DeReplicaLikeRev>>)

    fun selectLikeRevByUidPair(t: Pair<Int, Int>): List<LikeRevRow>

    fun doRemoveConflictInLikesRev(t: DeReplicaLikeRev): Int

    fun preRemoveConflictSidInLikesRev(uidPairList: List<Pair<Int/*pos*/, Int/*neg*/>>): List<Triple<Int, Int, DeReplicaLikeRev>>

    fun selectTopicTypeAndIdByUidListAndGroupByPk(t: Pair<Int, Int>): List<DeRepetitiveTopicData>

    fun doRemoveConflictTopic(t: DeRepetitiveTopicData): Int

    fun preRemoveConflictTopic(userList: List<Pair<Int, Int>>)

    fun selectPostTypeAndIdAndMidByUidListAndGroupByPk(t: Pair<Int, Int>): List<DeRepetitivePostData>

    fun doRemoveConflictPost(t: DeRepetitivePostData): Int

    fun preRemoveConflictPost(userList: List<Pair<Int, Int>>)

    fun selectBlogTopicTypeAndIdByUidListAndGroupByTypeIdAndSid(t: Pair<Int, Int>): List<DeRepetitiveBlogTopicData>

    fun doRemoveConflictBlogTopic(t: DeRepetitiveBlogTopicData): Int
    fun preRemoveConflictSidInBlog(userList: List<Pair<Int, Int>>)

    fun upsertSidAlias(sidAliasMappingList: Iterable<SpaceNameMappingData>): IntArray

    fun upsertBlogSubjectIdMapping(blogSubjectIdMappingList: Iterable<Pair<Int, Int>>)

    fun upsertBlogTagMapping(blogSubjectIdMappingList: Iterable<Pair<Int, String>>)

    fun getPostListByTypeAndTopicId(type: Int, topicId: Int): List<PostRow>

    fun getLikeListByTypeAndTopicId(type: Int, topicId: Int): List<LikeRow>

    fun getTopicListByTypeAndTopicId(type: Int, topicId: Int): List<TopicRow>

    fun getSpaceNamingMappingByTypeAndSid(type:Int, sid:Int): List<SpaceNameMappingData>

    fun getAllPostCountByTypeAndUsernameList(
        t: Int,
        l: Iterable<String>
    ): List<VAllPostCountRow>

    fun getAllTopicCountByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VAllTopicCountRow>

    fun getAllPostCount30dByTypeAndUsernameList(
        t: Int,
        l: Iterable<String>
    ): List<VAllPostCount30dRow>

    fun getAllTopicCount30dByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VAllTopicCount30dRow>

    fun getAllPostCount7dByTypeAndUsernameList(
        t: Int,
        l: Iterable<String>
    ): List<VAllPostCount7dRow>

    fun getAllTopicCount7dByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VAllTopicCount7dRow>

    fun getLikesSumByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VLikesSumRow>

    fun getPostCountSpaceByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VPostCountSpaceRow>

    fun getPostCountSpace30dByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VPostCountSpaceRow>

    fun getPostCountSpace7dByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VPostCountSpaceRow>

    fun getTopicCountSpaceByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VTopicCountSpaceRow>

    fun getTopicCountSpace30dByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VTopicCountSpaceRow>

    fun getTopicCountSpace7dByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VTopicCountSpaceRow>

    fun getUserLastReplyTopicByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VUserLastReplyTopicRow>

    fun getUserLatestCreateTopicAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VUserLatestCreateTopicRow>


    fun getUserRowByUsernameList(
        l: Iterable<String>
    ): List<UserRow>

    fun getLikeRevListByTypeAndTopicId(type: Int, topicId: Int): List<LikeRevRow>

    fun batchUpsertLikesRev(likeList: Iterable<LikeRevRow>): IntArray

    fun getLikeRevSumByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VLikesSumRow>

    fun getLikeRevStatForSpaceByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VLikeRevCountSpaceRow>

    fun getLikeStatForSpaceByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VLikeCountSpaceRow>

    fun getUserLatestLikeRevByTypeAndUsernameList(
        type: Int,
        l: Iterable<String>
    ): List<VUserLatestLikeRevRow>

    fun getAllTopicIdByType(type: Int): ArrayList<Int>

    fun getAllTopicIdByTypeAndState(type: Int, state: Int): ArrayList<Int>

    fun getMaxTopicIdByType(type: Int): Int

    fun _TRUNCATE_ALL_META(): Int
}

