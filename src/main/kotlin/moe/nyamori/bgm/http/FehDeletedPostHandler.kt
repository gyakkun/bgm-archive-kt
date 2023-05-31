package moe.nyamori.bgm.http

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import io.javalin.http.util.NaiveRateLimit
import io.javalin.http.util.RateLimitUtil
import io.javalin.http.util.RateLimiter
import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.model.Post
import moe.nyamori.bgm.model.Space
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.util.BinarySearchHelper
import moe.nyamori.bgm.util.FilePathHelper
import moe.nyamori.bgm.util.HttpHelper
import moe.nyamori.bgm.util.SealedTypeAdapterFactory
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

object FehDeletedPostHandler : Handler {
    val GSON = GsonBuilder()
        .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .registerTypeAdapterFactory(
            SealedTypeAdapterFactory.of(Space::class)
        ).create()
    val LOGGER = LoggerFactory.getLogger(FehDeletedPostHandler::class.java)
    override fun handle(ctx: Context) {
        NaiveRateLimit.requestPerTimeUnit(ctx, 20, TimeUnit.MINUTES)
        try {
            if (!HttpHelper.GIT_RELATED_LOCK.tryLock(30, TimeUnit.SECONDS)) {
                ctx.status(HttpStatus.GATEWAY_TIMEOUT)
                ctx.html("The server is busy. Please wait and refresh later.")
                return
            }
            doHandle(ctx)
        } catch (ex: Exception) {
            LOGGER.error("Ex: ", ex)
            throw ex
        } finally {
            if (HttpHelper.GIT_RELATED_LOCK.isHeldByCurrentThread) HttpHelper.GIT_RELATED_LOCK.unlock()
        }
    }

    fun doHandle(ctx: Context) {
        val type = ctx.pathParam("type")
        require(type.isNotBlank() && type in SpaceType.values().map { it.name.lowercase() }) {
            "Type should be within blog, subject and group!"
        }
        val topicId =
            ctx.pathParam("topicId").toIntOrNull() ?: throw IllegalArgumentException("topicId should be a valid number")
        val postId =
            ctx.pathParam("postId").toIntOrNull() ?: throw IllegalArgumentException("postId should be a valid number")

        val jsonPath = "$type/${FilePathHelper.numberToPath(topicId)}.json"
        val timestampList = FileHistoryLookup.getJsonTimestampList(jsonPath)
        if (timestampList.isEmpty()) {
            LOGGER.info("Empty for topic : $type - $topicId")
            ctx.status(HttpStatus.NOT_FOUND)
            return
        }
        val cache = HashMap<Long, Topic>()
        val topicAtTs = fun(ts: Long): Topic {
            return cache.computeIfAbsent(ts) {
                GSON.fromJson(
                    FileHistoryLookup.getJsonFileContentAsStringAtTimestamp(
                        ts,
                        jsonPath
                    ), Topic::class.java
                )
            }
        }

        val bsFunMinExistPostGen =
            BinarySearchHelper.binarySearchFunctionGenerator<Long>(BinarySearchHelper.BSType.FLOOR)
        val earliestTsWithWantedPost = bsFunMinExistPostGen(timestampList) { ts ->
            val topic = topicAtTs(ts)
            topic.getAllPosts().any { it.id == postId }
        } ?: run {
            LOGGER.info("Empty for post : $type - $topicId - $postId")
            ctx.status(HttpStatus.NOT_FOUND)
            return
        }

        val bsFunMaxPostGen = BinarySearchHelper.binarySearchFunctionGenerator<Long>(BinarySearchHelper.BSType.CEILING)
        val filteredTsList = timestampList.filter { it >= earliestTsWithWantedPost }
        val theWantedPostTs = bsFunMaxPostGen(filteredTsList) { ts ->
            val topic = topicAtTs(ts)
            val post = topic.getAllPosts().first { it.id == postId }
            !post.isDeleted() && !post.isAdminDeleted()
        } ?: run {
            LOGGER.info("All iterated but still not found : $type - $topicId - $postId")
            ctx.status(HttpStatus.NOT_FOUND)
            return
        }

        val thePost = topicAtTs(theWantedPostTs).getAllPosts().first { it.id == postId }
        ctx.html(thePost.contentHtml ?: "(bgm-archive 未收录)")
        ctx.status(thePost.contentHtml?.let { HttpStatus.OK } ?: HttpStatus.NOT_FOUND)
        return
    }

}