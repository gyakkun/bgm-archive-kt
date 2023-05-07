package moe.nyamori.bgm.http

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.db.Dao
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
        val userList = bodyMap["users"]!! as List<String>

        if (bodyMap["type"] == null || bodyMap["type"]!! !in SpaceType.values().map { it.name.lowercase() }) {
            ctx.status(HttpStatus.BAD_REQUEST)
            ctx.result("Space type should be one of group, subject and blog")
            return null
        }
        val spaceType = SpaceType.valueOf((bodyMap["type"] as String).uppercase())
        return Pair(spaceType, userList)
    }

    fun getInfoBySpaceTypeAndUsernameList(spaceType: SpaceType, usernameList: List<String>) {
        val allPostCountByTypeAndUsernameList =
            Dao.bgmDao().getAllPostCountByTypeAndUsernameList(spaceType.id, usernameList)
        val allTopicCountByTypeAndUsernameList =
            Dao.bgmDao().getAllTopicCountByTypeAndUsernameList(spaceType.id, usernameList)
        val likesSumByTypeAndUsernameList = Dao.bgmDao().getLikesSumByTypeAndUsernameList(spaceType.id, usernameList)
        val postCountSpaceByTypeAndUsernameList =
            Dao.bgmDao().getPostCountSpaceByTypeAndUsernameList(spaceType.id, usernameList)
        val topicCountSpaceByTypeAndUsernameList =
            Dao.bgmDao().getTopicCountSpaceByTypeAndUsernameList(spaceType.id, usernameList)
        val userLastReplyTopicByTypeAndUsernameList =
            Dao.bgmDao().getUserLastReplyTopicByTypeAndUsernameList(spaceType.id, usernameList)
        val userLatestCreateTopicAndUsernameList =
            Dao.bgmDao().getUserLatestCreateTopicAndUsernameList(spaceType.id, usernameList)
        System.err.println("end")
    }
}