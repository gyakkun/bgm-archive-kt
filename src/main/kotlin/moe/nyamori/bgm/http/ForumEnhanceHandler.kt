package moe.nyamori.bgm.http

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import moe.nyamori.bgm.db.*
import moe.nyamori.bgm.http.ForumEnhanceHandler.isPostDeleted
import moe.nyamori.bgm.model.Post
import moe.nyamori.bgm.model.SpaceType
import org.slf4j.LoggerFactory

object ForumEnhanceHandler : Handler {
    private val LOGGER = LoggerFactory.getLogger(ForumEnhanceHandler::class.java)
    private val GSON = Gson()
    private val STRING_OBJECT_TYPE_TOKEN = object : TypeToken<Map<String, Any>>() {}.type
    override fun handle(ctx: Context) {

        val ch = checkValidReq(ctx) ?: return
        val (spaceType, userList) = ch
        getInfoBySpaceTypeAndUsernameList(spaceType, userList)
    }

    fun checkValidReq(ctx: Context): Pair<SpaceType, List<String>>? {
        val bodyStr = ctx.body()
        val bodyMap = GSON.fromJson<Map<String, Any>>(bodyStr, STRING_OBJECT_TYPE_TOKEN)
        if (bodyMap["users"] == null || bodyMap["users"]!! !is List<*>) {
            ctx.status(HttpStatus.BAD_REQUEST)
            ctx.result("Users should be a list of string.")
            return null
        }
        val userList = (bodyMap["users"]!! as List<String>).distinct()

        if (bodyMap["type"] == null || bodyMap["type"]!! !in SpaceType.values().map { it.name.lowercase() }) {
            ctx.status(HttpStatus.BAD_REQUEST)
            ctx.result("Space type should be one of group, subject and blog")
            return null
        }
        val spaceType = SpaceType.valueOf((bodyMap["type"] as String).uppercase())
        return Pair(spaceType, userList)
    }

    fun getInfoBySpaceTypeAndUsernameList(spaceType: SpaceType, usernameList: List<String>) = runBlocking {
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
        aggregateResult(
            usernameList,
            vAllPostCountRows,
            vAllTopicCountRows,
            vLikesSumRows,
            vPostCountSpaceRows,
            vTopicCountSpaceRows,
            vUserLastReplyTopicRows,
            vUserLatestCreateTopicRows
        )
        System.err.println("end")
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
    ) {
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


        vAllPostCountRows.groupBy { it.username }
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

    private inline fun <T, R> T.timingWrapper(funName: String = "", block: T.() -> R): R {
        if (funName.isNotBlank()) System.err.println("Function $funName start")
        val timing = System.currentTimeMillis()
        val res = block()
        System.err.println("Timing:${funName.ifBlank { "" }} ${System.currentTimeMillis() - timing}ms.")
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