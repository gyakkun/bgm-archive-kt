package moe.nyamori.bgm.http

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import kotlinx.coroutines.*
import moe.nyamori.bgm.db.*
import moe.nyamori.bgm.model.Post
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.HttpHelper
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

object ForumEnhanceHandler : Handler {
    private val LOGGER = LoggerFactory.getLogger(ForumEnhanceHandler::class.java)
    private val GSON = Gson()
    @Suppress("UNCHECKED_CAST")
    private val STRING_OBJECT_TYPE_TOKEN =
        TypeToken.getParameterized(Map::class.java, String::class.java, Any::class.java) as TypeToken<Map<String, Any>>
    private val CACHE_DURATION = Duration.ofHours(2)
    private const val CACHE_SIZE = 600L
    private val VT_EXECUTOR = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("feh-", 0).factory()
    )

    // private val VT_DISP = VT_EXECUTOR.asCoroutineDispatcher()
    private val CACHE =
        Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE)
            .expireAfterWrite(CACHE_DURATION)
            .build(object : CacheLoader<Pair<SpaceType, String/*username*/>, UserStat> {

                override fun load(key: Pair<SpaceType, String>?): UserStat = runBlocking {
                    return@runBlocking getInfoBySpaceTypeAndUsernameList(
                        key!!.first,
                        listOf(key.second)
                    ).get()[key.second]!!
                }

                override fun loadAll(keys: MutableSet<out Pair<SpaceType, String>>?): MutableMap<out Pair<SpaceType, String>, out UserStat> =
                    runBlocking {
                        val tmp = keys!!.groupBy { it.first }.map { it.key to it.value.map { it.second } }
                            .toMap()
                            .map {
                                val type = it.key
                                val usernameList = it.value
                                val toMerge = getInfoBySpaceTypeAndUsernameList(type, usernameList).get()
                                    .map { Pair(type, it.key) to it.value }
                                    .toMap()
                                return@map toMerge
                            }
                        return@runBlocking mutableMapOf<Pair<SpaceType, String>, UserStat>().apply {
                            tmp.forEach {
                                putAll(it)
                            }
                        }
                    }

            })

    override fun handle(ctx: Context) = runBlocking {
        try {
            if (!HttpHelper.DB_READ_SEMAPHORE.tryAcquire(
                    30,
                    TimeUnit.SECONDS
                )
            ) {
                ctx.status(HttpStatus.GATEWAY_TIMEOUT)
                ctx.result("Server is busy. Please try later.")
                return@runBlocking
            }
            try {
                val ch = checkValidReq(ctx) ?: return@runBlocking
                val (spaceType, userList) = ch
                // val res = getInfoBySpaceTypeAndUsernameList(spaceType, userList).await()
                val res = CACHE.getAll(userList.map { spaceType to it }).map { it.key.second to it.value }.toMap()
                ctx.json(res)
            } catch (innerEx: Exception) {
                throw innerEx
            } finally {
                HttpHelper.DB_READ_SEMAPHORE.release()
            }
        } catch (outerEx: Exception) {
            LOGGER.error("Ex when handling forum enhance: ", outerEx)
            throw outerEx
        }

    }

    fun checkValidReq(ctx: Context): Pair<SpaceType, List<String>>? {
        val bodyStr = ctx.body()
        val bodyMap = GSON.fromJson(bodyStr, STRING_OBJECT_TYPE_TOKEN)
        if (bodyMap["type"] == null || bodyMap["type"] !is String || bodyMap["type"]!! !in SpaceType.entries
                .map { it.name.lowercase() }
        ) {
            ctx.status(HttpStatus.BAD_REQUEST)
            ctx.result("Field \"type\" should be one of group, subject and blog")
            return null
        }
        val spaceType = SpaceType.valueOf((bodyMap["type"] as String).uppercase())

        if (bodyMap["users"] == null
            || bodyMap["users"]!! !is List<*>
            || (bodyMap["users"]!! as List<*>).isEmpty()
            || (bodyMap["users"]!! as List<*>).any { it !is String }
        ) {
            ctx.status(HttpStatus.BAD_REQUEST)
            ctx.result("Field \"users\" should be a list of string.")
            return null
        }
        var userList = (bodyMap["users"]!! as List<String>).distinct()
        userList = Dao.bgmDao.getValidUsernameListFromList(userList)
        if (userList.size > 200) {
            LOGGER.warn("User list too large, take the first 200.")
            userList = userList.take(200)
        }
        return Pair(spaceType, userList)
    }

    fun getInfoBySpaceTypeAndUsernameList(
        spaceType: SpaceType,
        usernameList: List<String>
    ): Future<Map<String, UserStat>> = VT_EXECUTOR.submit<Map<String, UserStat>> {
//    ): Deferred<Map<String, UserStat>> = GlobalScope.async {
        val allPostCountByTypeAndUsernameList = VT_EXECUTOR.submit<List<VAllPostCountRow>> {
            timingWrapper("getAllPostCountByTypeAndUsernameList") {
                Dao.bgmDao.getAllPostCountByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val allTopicCountByTypeAndUsernameList = VT_EXECUTOR.submit<List<VAllTopicCountRow>> {
            timingWrapper("getAllTopicCountByTypeAndUsernameList") {
                Dao.bgmDao.getAllTopicCountByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val likesSumByTypeAndUsernameList = VT_EXECUTOR.submit<List<VLikesSumRow>> {
            timingWrapper("getLikesSumByTypeAndUsernameList") {
                Dao.bgmDao.getLikesSumByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val likeRevSumByTypeAndUsernameList = VT_EXECUTOR.submit<List<VLikesSumRow>> {
            timingWrapper("getLikeRevSumByTypeAndUsernameList") {
                Dao.bgmDao.getLikeRevSumByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val postCountSpaceByTypeAndUsernameList = VT_EXECUTOR.submit<List<VPostCountSpaceRow>> {
            timingWrapper("getPostCountSpaceByTypeAndUsernameList") {
                Dao.bgmDao.getPostCountSpaceByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val topicCountSpaceByTypeAndUsernameList = VT_EXECUTOR.submit<List<VTopicCountSpaceRow>> {
            timingWrapper("getTopicCountSpaceByTypeAndUsernameList") {
                Dao.bgmDao.getTopicCountSpaceByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val userLastReplyTopicByTypeAndUsernameList = VT_EXECUTOR.submit<List<VUserLastReplyTopicRow>> {
            timingWrapper("getUserLastReplyTopicByTypeAndUsernameList") {
                Dao.bgmDao.getUserLastReplyTopicByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val userLatestCreateTopicAndUsernameList = VT_EXECUTOR.submit<List<VUserLatestCreateTopicRow>> {
            timingWrapper("getUserLatestCreateTopicAndUsernameList") {
                Dao.bgmDao.getUserLatestCreateTopicAndUsernameList(spaceType.id, usernameList)
            }
        }
        val likeRevCountForSpaceByTypeAndUsernameList = VT_EXECUTOR.submit<List<VLikeRevCountSpaceRow>> {
            timingWrapper("getLikeRevStatForSpaceByTypeAndUsernameList") {
                Dao.bgmDao.getLikeRevStatForSpaceByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val likeCountForSpaceByTypeAndUsernameList = VT_EXECUTOR.submit<List<VLikeCountSpaceRow>> {
            timingWrapper("getLikeStatForSpaceByTypeAndUsernameList") {
                Dao.bgmDao.getLikeStatForSpaceByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val userLatestLikeRevByTypeAndUsernameList = VT_EXECUTOR.submit<List<VUserLatestLikeRevRow>> {
            timingWrapper("getUserLatestLikeRevByTypeAndUsernameList") {
                Dao.bgmDao.getUserLatestLikeRevByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val vAllPostCountRows = allPostCountByTypeAndUsernameList.get()
        val vAllTopicCountRows = allTopicCountByTypeAndUsernameList.get()
        val vLikesSumRows = likesSumByTypeAndUsernameList.get()
        val vLikeRevSumRows = likeRevSumByTypeAndUsernameList.get()
        val vPostCountSpaceRows = postCountSpaceByTypeAndUsernameList.get()
        val vTopicCountSpaceRows = topicCountSpaceByTypeAndUsernameList.get()
        val vUserLastReplyTopicRows = userLastReplyTopicByTypeAndUsernameList.get()
        val vUserLatestCreateTopicRows = userLatestCreateTopicAndUsernameList.get()
        val vLikeRevCountSpaceRows = likeRevCountForSpaceByTypeAndUsernameList.get()
        val vLikeCountSpaceRows = likeCountForSpaceByTypeAndUsernameList.get()
        val vUserLatestLikeRevRows = userLatestLikeRevByTypeAndUsernameList.get()
        val res = aggregateResult(
            spaceType,
            usernameList,
            vAllPostCountRows,
            vAllTopicCountRows,
            vLikesSumRows,
            vLikeRevSumRows,
            vPostCountSpaceRows,
            vTopicCountSpaceRows,
            vUserLastReplyTopicRows,
            vUserLatestCreateTopicRows,
            vLikeRevCountSpaceRows,
            vLikeCountSpaceRows,
            vUserLatestLikeRevRows
        )
        return@submit res
    }

    private fun aggregateResult(
        spaceType: SpaceType,
        usernameList: List<String>,
        vAllPostCountRows: List<VAllPostCountRow> = emptyList(),
        vAllTopicCountRows: List<VAllTopicCountRow> = emptyList(),
        vLikesSumRows: List<VLikesSumRow> = emptyList(),
        vLikeRevSumRows: List<VLikesSumRow> = emptyList(),
        vPostCountSpaceRows: List<VPostCountSpaceRow> = emptyList(),
        vTopicCountSpaceRows: List<VTopicCountSpaceRow> = emptyList(),
        vUserLastReplyTopicRows: List<VUserLastReplyTopicRow> = emptyList(),
        vUserLatestCreateTopicRows: List<VUserLatestCreateTopicRow> = emptyList(),
        vLikeRevCountSpaceRows: List<VLikeRevCountSpaceRow>,
        vLikeCountSpaceRows: List<VLikeCountSpaceRow>,
        vUserLatestLikeRevRows: List<VUserLatestLikeRevRow>
    ): Map<String, UserStat> {
        val postStatMap = vAllPostCountRows.groupBy { it.username } // ->poststat
            .map {
                val total = it.value.sumOf { it.count }
                val deleted = it.value.filter { it.state.isPostDeleted() }.sumOf { it.count }
                val adminDeleted = it.value.filter { it.state.isPostAdminDeleted() }.sumOf { it.count }
                val violative = it.value.filter { it.state.isViolative() }.sumOf { it.count }
                val collapsed = it.value.filter { it.state.isCollapsed() }.sumOf { it.count }
                it.key to PostStat(total, deleted, adminDeleted, violative, collapsed)
            }.toMap()
        val topicStatMap = vAllTopicCountRows.groupBy { it.username }
            .map {
                val total = it.value.sumOf { it.count }
                val deleted = it.value.filter { it.state.isTopicDeleted() }.sumOf { it.count }
                val silent = it.value.filter { it.state.isTopicSilent() }.sumOf { it.count }
                val closed = it.value.filter { it.state.isTopicClosed() }.sumOf { it.count }
                val reopen = it.value.filter { it.state.isTopicReopen() }.sumOf { it.count }
                it.key to TopicStat(total, deleted, silent, closed, reopen)
            }.toMap()
        val likeStatMap = vLikesSumRows.groupBy { it.username }
            .map {
                it.key to it.value.associate { it.faceKey to it.count }
            }.toMap()
        val likeRevStatMap = vLikeRevSumRows.groupBy { it.username }
            .map {
                it.key to it.value.associate { it.faceKey to it.count }
            }.toMap()
        val spacePostStatMap = vPostCountSpaceRows.groupBy { it.username }
            .map {
                it.key to it.value.groupBy { it.name }.map {
                    val total = it.value.sumOf { it.count }
                    val deleted = it.value.filter { it.state.isPostDeleted() }.sumOf { it.count }
                    val adminDeleted = it.value.filter { it.state.isPostAdminDeleted() }.sumOf { it.count }
                    val violative = it.value.filter { it.state.isViolative() }.sumOf { it.count }
                    val collapsed = it.value.filter { it.state.isCollapsed() }.sumOf { it.count }
                    it.key to PostStat(total, deleted, adminDeleted, violative, collapsed) // boring to (1,2,3,0)
                }.sortedBy { -(it.second.total) }
                    .toMap() // sai -> { boring: (1,2,3) }
            }.toMap() // { sai: {boring: (1,2,3)} , trim21: {a:(4,5,6)} }
        val spaceTopicStatMap = vTopicCountSpaceRows.groupBy { it.username }
            .map {
                it.key to it.value.groupBy { it.name }.map {
                    val total = it.value.sumOf { it.count }
                    val deleted = it.value.filter { it.state.isTopicDeleted() }.sumOf { it.count }
                    val silent = it.value.filter { it.state.isTopicSilent() }.sumOf { it.count }
                    val closed = it.value.filter { it.state.isTopicClosed() }.sumOf { it.count }
                    val reopen = it.value.filter { it.state.isTopicReopen() }.sumOf { it.count }
                    it.key to TopicStat(total, deleted, silent, closed, reopen) // boring to (1,2,3)
                }.sortedBy { -(it.second.total) }
                    .toMap() // sai -> { boring: (1,2,3) }
            }.toMap() // { sai: {boring: (1,2,3)} , trim21: {a:(4,5,6)} }
        val spaceLikeRevStatMap = vLikeRevCountSpaceRows.groupBy { it.username }
            .map {
                it.key /*username*/ to it.value.groupBy { it.spaceName }.map {
                    it.key /*spaceName*/ to LikeRevStatForSpace(it.value.sumOf { it.count })// no need for sum of actually because it should be 1 ele
                }.sortedBy { -(it.second.total) }
                    .toMap()
            }.toMap()
        val spaceLikeStatMap = vLikeCountSpaceRows.groupBy { it.username }
            .map {
                it.key /*username*/ to it.value.groupBy { it.spaceName }.map {
                    it.key /*spaceName*/ to LikeStatForSpace(it.value.sumOf { it.count })// no need for sum of actually because it should be 1 ele
                }.sortedBy { -(it.second.total) }
                    .toMap()
            }.toMap()
        val spaceStatMergedMap = run {
            val userKeys = mutableSetOf<String>().apply {
                addAll(spacePostStatMap.keys)
                addAll(spaceTopicStatMap.keys)
                addAll(spaceLikeRevStatMap.keys)
                addAll(spaceLikeStatMap.keys)
            }
            val spaceNameDisplayNameMap = mutableMapOf<String, String>().apply {
                putAll(vTopicCountSpaceRows.map { it.name to it.displayName }.distinct().toMap())
                putAll(vPostCountSpaceRows.map { it.name to it.displayName }.distinct().toMap())
                putAll(vLikeRevCountSpaceRows.map { it.spaceName to it.spaceDisplayName }.distinct().toMap())
                putAll(vLikeCountSpaceRows.map { it.spaceName to it.spaceDisplayName }.distinct().toMap())
            }
            userKeys.map { username ->
                val spaceNameToTopicStatMap = spaceTopicStatMap[username] ?: emptyMap()
                val spaceNameToPostStatMap = spacePostStatMap[username] ?: emptyMap()
                val spaceNameToLikeRevStatMap = spaceLikeRevStatMap[username] ?: emptyMap()
                val spaceNameToLikeStatMap = spaceLikeStatMap[username] ?: emptyMap()
                username to spaceNameDisplayNameMap.keys.mapNotNull { spaceName ->
                    if (spaceNameToTopicStatMap[spaceName] == null && spaceNameToPostStatMap[spaceName] == null) {
                        return@mapNotNull null
                    }
                    SpaceStat(
                        spaceName, spaceNameDisplayNameMap[spaceName]!!,
                        spaceNameToPostStatMap[spaceName] ?: PostStat(),
                        spaceNameToTopicStatMap[spaceName] ?: TopicStat(),
                        spaceNameToLikeRevStatMap[spaceName] ?: LikeRevStatForSpace(),
                        spaceNameToLikeStatMap[spaceName] ?: LikeStatForSpace(),
                    )
                }.sortedBy { -(it.post.total + it.likeRev.total) }
                    .take(5)
            }.toMap()
        }
        val lastReplyTopicMap = vUserLastReplyTopicRows.groupBy { it.username }
            .map {
                it.key to it.value.mapNotNull {
                    if (it.title == null) return@mapNotNull null
                    if (!it.state.isPostNormal()) return@mapNotNull null
                    if (it.topicState.isTopicDeleted()) return@mapNotNull null
                    PostBrief(it.title, it.mid, it.id, it.dateline, it.spaceDisplayName)
                }.sortedBy { -it.dateline }.take(10)
            }.toMap()
        val lastCreateTopicMap = vUserLatestCreateTopicRows.groupBy { it.username }
            .map {
                it.key to it.value.mapNotNull {
                    if (it.title == null) return@mapNotNull null
                    if (it.state.isTopicDeleted()) return@mapNotNull null
                    TopicBrief(it.title, it.id, it.dateline, it.spaceDisplayName)
                }.take(10)
            }.toMap()
        val latestLikeRevMap = vUserLatestLikeRevRows.groupBy { it.username }
            .map {
                it.key to it.value.groupBy { it.mid }.mapNotNull {
                    if (it.value.size == 0) return@mapNotNull null
                    if (it.value[0].title == null) return@mapNotNull null
                    val title = it.value[0].title!!
                    val mid = it.key
                    val dateline = it.value.maxOf { it.dateline }
                    val likeRevList = it.value.map { PidFaceKeyPair(it.pid, it.faceKey) }.sortedBy { -it.pid }
                    LikeRevBrief(
                        title = title,
                        mid = mid,
                        dateline = dateline,
                        likeRevList = likeRevList,
                        spaceDisplayName = it.value[0].spaceDisplayName
                    )
                }.sortedBy { -it.dateline }.take(10)
            }.toMap()
        val result = run {
            usernameList.associateWith { un ->
                UserStat(
                    _meta = mutableMapOf(
                        "expiredAt" to (System.currentTimeMillis() + CACHE_DURATION.toMillis())
                    ),
                    type = spaceType.name.lowercase(),
                    postStat = postStatMap[un] ?: PostStat(),
                    topicStat = topicStatMap[un] ?: TopicStat(),
                    likeStat = likeStatMap[un] ?: emptyMap(),
                    likeRevStat = likeRevStatMap[un] ?: emptyMap(),
                    spaceStat = spaceStatMergedMap[un] ?: emptyList(),
                    recentActivities = Recent(
                        topic = lastCreateTopicMap[un] ?: emptyList(),
                        post = lastReplyTopicMap[un] ?: emptyList(),
                        likeRev = latestLikeRevMap[un] ?: emptyList()
                    )
                )
            }
        }
        return result
        // user : {
        //    postStat : {
        //      total: 123,
        //      deleted: 12,
        //      adminDeleted: 1,
        //      violative: 1
        //    }
        //    topicStat : {
        //      total: 45,
        //      deleted: 1,
        //      silent: 0,
        //      closed: 1,
        //      reopen: 0
        //    },
        //    likeStat : {
        //      faceKey1 : faceValue1
        //    }
        //    spaceStat : [
        //       {
        //         displayName: 茶话会,
        //         name: boring,
        //         post : {
        //            total: 123,
        //            deleted: 4,
        //            adminDeleted: 5,
        //            violative: 1
        //         },
        //         topic : {
        //           total: 45,
        //           deleted: 1,
        //           silent: 0,
        //           closed: 1,
        //           reopen: 0
        //         },
        //       }
        //    ],
        //    recentActivities : {
        //        topic : [
        //           {
        //              title: fishing,
        //              id: 12345,
        //              dateline: 163928989
        //           }
        //        ] ,
        //        post  : [
        //           {
        //              title: stop fishing,
        //              mid: 23456,
        //              pid: 1234567,
        //              dateline: 1623898298
        //           }
        //        ]
        //
        //
        //    }
        //
        //
        // }

    }

    data class PostStat(
        val total: Int = 0,
        val deleted: Int = 0,
        val adminDeleted: Int = 0,
        val violative: Int = 0,
        val collapsed: Int = 0,
    )

    data class TopicStat(
        val total: Int = 0,
        val deleted: Int = 0,
        val silent: Int = 0,
        val closed: Int = 0,
        val reopen: Int = 0
    )

    data class LikeRevStatForSpace(
        val total: Int = 0
    )

    data class LikeStatForSpace(
        val total: Int = 0
    )

    data class SpaceStat(
        val name: String, val displayName: String,
        val post: PostStat = PostStat(),
        val topic: TopicStat = TopicStat(),
        val likeRev: LikeRevStatForSpace = LikeRevStatForSpace(),
        val like: LikeStatForSpace = LikeStatForSpace()
    )

    data class TopicBrief(val title: String, val id: Int, val dateline: Long, val spaceDisplayName: String? = null)
    data class PostBrief(
        val title: String, val mid: Int, val pid: Int, val dateline: Long, val spaceDisplayName: String? = null
    )

    data class LikeRevBrief(
        val title: String,
        val mid: Int,
        val dateline: Long, /*latest post dateline*/
        val likeRevList: List<PidFaceKeyPair>,
        val spaceDisplayName: String? = null
    )

    data class PidFaceKeyPair(
        val pid: Int,
        val faceKey: Int
    )

    data class Recent(
        val topic: List<TopicBrief> = emptyList(),
        val post: List<PostBrief> = emptyList(),
        val likeRev: List<LikeRevBrief> = emptyList()
    )

    data class UserStat(
        val _meta: MutableMap<String, Any?> = mutableMapOf(),
        val type: String,
        val postStat: PostStat = PostStat(),
        val topicStat: TopicStat = TopicStat(),
        val likeStat: Map<Int, Int> = mapOf(),
        val likeRevStat: Map<Int, Int> = mapOf(),
        val spaceStat: List<SpaceStat> = listOf(),
        val recentActivities: Recent = Recent()
    )

    private fun <T, R> T.timingWrapper(funName: String = "", block: T.() -> R): R {
        if (funName.isNotBlank()) {
            LOGGER.info("Function $funName start")
        }
        val timing = System.currentTimeMillis()
        val res = block()
        LOGGER.info("Timing:${funName.ifBlank { "" }} ${System.currentTimeMillis() - timing}ms.")
        return res
    }

    private fun Long.isPostDeleted(): Boolean {
        return this and Post.STATE_DELETED == Post.STATE_DELETED
    }

    private fun Long.isPostAdminDeleted(): Boolean {
        return this and Post.STATE_ADMIN_DELETED == Post.STATE_ADMIN_DELETED
    }

    private fun Long.isViolative() = this and Post.STATE_VIOLATIVE == Post.STATE_VIOLATIVE
    private fun Long.isCollapsed() = this and Post.STATE_COLLAPSED == Post.STATE_COLLAPSED

    private fun Long.isPostNormal(): Boolean {
        return this and 1L == 0L
    }

    private fun Long.isTopicDeleted() = this.isPostDeleted()
    private fun Long.isTopicSilent(): Boolean {
        return this and Post.STATE_SILENT == Post.STATE_SILENT
    }

    private fun Long.isTopicClosed(): Boolean {
        return this and Post.STATE_CLOSED == Post.STATE_CLOSED
    }

    private fun Long.isTopicReopen(): Boolean {
        return this and Post.STATE_REOPEN == Post.STATE_REOPEN
    }
}