package moe.nyamori.bgm.http

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import moe.nyamori.bgm.db.*
import moe.nyamori.bgm.model.Post
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.HttpHelper
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit

object ForumEnhanceHandler : Handler {
    private val LOGGER = LoggerFactory.getLogger(ForumEnhanceHandler::class.java)
    private val GSON = Gson()
    private val STRING_OBJECT_TYPE_TOKEN = object : TypeToken<Map<String, Any>>() {}.type
    private val CACHE =
        Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(120))
            .build(object : CacheLoader<Pair<SpaceType, String/*username*/>, UserStat> {

                override fun load(key: Pair<SpaceType, String>?): UserStat? = runBlocking {
                    return@runBlocking getInfoBySpaceTypeAndUsernameList(
                        key!!.first,
                        listOf(key.second)
                    ).await()[key.second]!!
                }

                override fun loadAll(keys: MutableSet<out Pair<SpaceType, String>>?): MutableMap<out Pair<SpaceType, String>, out UserStat> =
                    runBlocking {
                        val tmp = keys!!.groupBy { it.first }.map { it.key to it.value.map { it.second } }
                            .toMap()
                            .map {
                                val type = it.key
                                val usernameList = it.value
                                val toMerge = getInfoBySpaceTypeAndUsernameList(type, usernameList).await()
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
            if (!HttpHelper.DB_WRITE_LOCK.tryLock(30, TimeUnit.SECONDS) || !HttpHelper.DB_READ_SEMAPHORE.tryAcquire(
                    30,
                    TimeUnit.SECONDS
                )
            ) {
                ctx.status(HttpStatus.GATEWAY_TIMEOUT)
                ctx.result("Server is busy. Please try later.")
                return@runBlocking
            }
            val ch = checkValidReq(ctx) ?: return@runBlocking
            val (spaceType, userList) = ch
            // val res = getInfoBySpaceTypeAndUsernameList(spaceType, userList).await()
            val res = CACHE.getAll(userList.map { spaceType to it }).map { it.key.second to it.value }.toMap()
            ctx.json(res)
            HttpHelper.DB_READ_SEMAPHORE.release()
        } catch (ex: Exception) {
            LOGGER.error("Ex when handling forum enhance: ", ex)
        } finally {
            if (HttpHelper.DB_WRITE_LOCK.isHeldByCurrentThread) {
                HttpHelper.DB_WRITE_LOCK.unlock()
            }
        }

    }

    fun checkValidReq(ctx: Context): Pair<SpaceType, List<String>>? {
        val bodyStr = ctx.body()
        val bodyMap = GSON.fromJson<Map<String, Any>>(bodyStr, STRING_OBJECT_TYPE_TOKEN)
        if (bodyMap["users"] == null || bodyMap["users"]!! !is List<*>) {
            ctx.status(HttpStatus.BAD_REQUEST)
            ctx.result("Users should be a list of string.")
            return null
        }
        var userList = (bodyMap["users"]!! as List<String>).distinct()
        if (userList.size > 200) {
            LOGGER.warn("User list too large, take the first 200.")
            userList = userList.take(200)
        }
        if (bodyMap["type"] == null || bodyMap["type"]!! !in SpaceType.values().map { it.name.lowercase() }) {
            ctx.status(HttpStatus.BAD_REQUEST)
            ctx.result("Space type should be one of group, subject and blog")
            return null
        }
        val spaceType = SpaceType.valueOf((bodyMap["type"] as String).uppercase())
        return Pair(spaceType, userList)
    }

    fun getInfoBySpaceTypeAndUsernameList(
        spaceType: SpaceType,
        usernameList: List<String>
    ): Deferred<Map<String, UserStat>> = GlobalScope.async {
        val allPostCountByTypeAndUsernameList = async {
            timingWrapper("getAllPostCountByTypeAndUsernameList") {
                Dao.bgmDao().getAllPostCountByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val allTopicCountByTypeAndUsernameList = async {
            timingWrapper("getAllTopicCountByTypeAndUsernameList") {
                Dao.bgmDao().getAllTopicCountByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val likesSumByTypeAndUsernameList = async {
            timingWrapper("getLikesSumByTypeAndUsernameList") {
                Dao.bgmDao().getLikesSumByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val postCountSpaceByTypeAndUsernameList = async {
            timingWrapper("getPostCountSpaceByTypeAndUsernameList") {
                Dao.bgmDao().getPostCountSpaceByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val topicCountSpaceByTypeAndUsernameList = async {
            timingWrapper("getTopicCountSpaceByTypeAndUsernameList") {
                Dao.bgmDao().getTopicCountSpaceByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val userLastReplyTopicByTypeAndUsernameList = async {
            timingWrapper("getUserLastReplyTopicByTypeAndUsernameList") {
                Dao.bgmDao().getUserLastReplyTopicByTypeAndUsernameList(spaceType.id, usernameList)
            }
        }
        val userLatestCreateTopicAndUsernameList = async {
            timingWrapper("getUserLatestCreateTopicAndUsernameList") {
                Dao.bgmDao().getUserLatestCreateTopicAndUsernameList(spaceType.id, usernameList)
            }
        }
        val vAllPostCountRows = allPostCountByTypeAndUsernameList.await()
        val vAllTopicCountRows = allTopicCountByTypeAndUsernameList.await()
        val vLikesSumRows = likesSumByTypeAndUsernameList.await()
        val vPostCountSpaceRows = postCountSpaceByTypeAndUsernameList.await()
        val vTopicCountSpaceRows = topicCountSpaceByTypeAndUsernameList.await()
        val vUserLastReplyTopicRows = userLastReplyTopicByTypeAndUsernameList.await()
        val vUserLatestCreateTopicRows = userLatestCreateTopicAndUsernameList.await()
        val res = aggregateResult(
            usernameList,
            vAllPostCountRows,
            vAllTopicCountRows,
            vLikesSumRows,
            vPostCountSpaceRows,
            vTopicCountSpaceRows,
            vUserLastReplyTopicRows,
            vUserLatestCreateTopicRows
        )
        return@async res
    }

    private fun aggregateResult(
        usernameList: List<String>,
        vAllPostCountRows: List<VAllPostCountRow> = emptyList(),
        vAllTopicCountRows: List<VAllTopicCountRow> = emptyList(),
        vLikesSumRows: List<VLikesSumRow> = emptyList(),
        vPostCountSpaceRows: List<VPostCountSpaceRow> = emptyList(),
        vTopicCountSpaceRows: List<VTopicCountSpaceRow> = emptyList(),
        vUserLastReplyTopicRows: List<VUserLastReplyTopicRow> = emptyList(),
        vUserLatestCreateTopicRows: List<VUserLatestCreateTopicRow> = emptyList()
    ): Map<String, UserStat> {
        val postStatMap = vAllPostCountRows.groupBy { it.username } // ->poststat
            .map {
                val total = it.value.sumOf { it.count }
                val deleted = it.value.filter { it.state.isPostDeleted() }.sumOf { it.count }
                val adminDeleted = it.value.filter { it.state.isPostAdminDeleted() }.sumOf { it.count }
                it.key to PostStat(total, deleted, adminDeleted)
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
        val spacePostStatMap = vPostCountSpaceRows.groupBy { it.username }
            .map {
                it.key to it.value.groupBy { it.name }.map {
                    val total = it.value.sumOf { it.count }
                    val deleted = it.value.filter { it.state.isPostDeleted() }.sumOf { it.count }
                    val adminDeleted = it.value.filter { it.state.isPostAdminDeleted() }.sumOf { it.count }
                    it.key to PostStat(total, deleted, adminDeleted) // boring to (1,2,3)
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
        val spaceStatMergedMap = run {
            val userKeys = mutableSetOf<String>().apply {
                addAll(spacePostStatMap.keys)
                addAll(spaceTopicStatMap.keys)
            }
            val spaceNameDisplayNameMap = mutableMapOf<String, String>().apply {
                putAll(vTopicCountSpaceRows.map { it.name to it.displayName }.distinct().toMap())
                putAll(vPostCountSpaceRows.map { it.name to it.displayName }.distinct().toMap())
            }
            userKeys.map { username ->
                val spaceNameToTopicStatMap = spaceTopicStatMap[username] ?: emptyMap()
                val spaceNameToPostStatMap = spacePostStatMap[username] ?: emptyMap()
                username to spaceNameDisplayNameMap.keys.mapNotNull { spaceName ->
                    if (spaceNameToTopicStatMap[spaceName] == null && spaceNameToPostStatMap[spaceName] == null) {
                        return@mapNotNull null
                    }
                    SpaceStat(
                        spaceName, spaceNameDisplayNameMap[spaceName]!!,
                        spaceNameToPostStatMap[spaceName] ?: PostStat(),
                        spaceNameToTopicStatMap[spaceName] ?: TopicStat(),
                    )
                }.sortedBy { -(it.post.total + it.topic.total) }
                    .take(5)
            }.toMap()
        }
        val lastReplyTopicMap = vUserLastReplyTopicRows.groupBy { it.username }
            .map {
                it.key to it.value.mapNotNull {
                    if (it.title == null) return@mapNotNull null
                    if (!it.state.isPostNormal()) return@mapNotNull null
                    PostBrief(it.title, it.mid, it.id, it.dateline)
                }.sortedBy { -it.dateline }.take(10)
            }.toMap()
        val lastCreateTopicMap = vUserLatestCreateTopicRows.groupBy { it.username }
            .map {
                it.key to it.value.mapNotNull {
                    if (it.title == null) return@mapNotNull null
                    if (it.state.isTopicDeleted()) return@mapNotNull null
                    TopicBrief(it.title, it.id, it.dateline)
                }.take(10)
            }.toMap()
        val result = run {
            usernameList.associateWith { un ->
                UserStat(
                    postStat = postStatMap[un] ?: PostStat(),
                    topicStat = topicStatMap[un] ?: TopicStat(),
                    likeStat = likeStatMap[un] ?: emptyMap(),
                    spaceStat = spaceStatMergedMap[un] ?: emptyList(),
                    recentActivities = Recent(
                        topic = lastCreateTopicMap[un] ?: emptyList(),
                        post = lastReplyTopicMap[un] ?: emptyList()
                    )
                )
            }
        }
        return result
        // user : {
        //    postStat : {
        //      total: 123,
        //      deleted: 12,
        //      adminDeleted: 1
        //    }
        //    topicStat : {
        //      total: 45,
        //      deleted: 1,
        //      silent: 0,
        //      closed: 1,
        //      reopen: 0
        //    },
        //    likesStat : {
        //      faceKey1 : faceValue1
        //    }
        //    spaceStat : [
        //       {
        //         displayName: 茶话会,
        //         name: boring,
        //         post : {
        //            total: 123,
        //            deleted: 4,
        //            adminDeleted: 5
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

    data class PostStat(val total: Int = 0, val deleted: Int = 0, val adminDeleted: Int = 0)
    data class TopicStat(
        val total: Int = 0,
        val deleted: Int = 0,
        val silent: Int = 0,
        val closed: Int = 0,
        val reopen: Int = 0
    )

    data class SpaceStat(
        val name: String, val displayName: String,
        val post: PostStat = PostStat(0, 0, 0),
        val topic: TopicStat = TopicStat(0, 0, 0, 0, 0)
    )

    data class TopicBrief(val title: String, val id: Int, val dateline: Long)
    data class PostBrief(val title: String, val mid: Int, val pid: Int, val dateline: Long)
    data class Recent(val topic: List<TopicBrief> = emptyList(), val post: List<PostBrief> = emptyList())
    data class UserStat(
        val postStat: PostStat = PostStat(),
        val topicStat: TopicStat = TopicStat(),
        val likeStat: Map<Int, Int> = mapOf(),
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