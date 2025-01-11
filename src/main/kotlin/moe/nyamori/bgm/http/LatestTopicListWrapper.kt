package moe.nyamori.bgm.http

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.git.GitRepoHolder
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.HttpHelper.checkAndExtractSpaceTypeInContext
import moe.nyamori.bgm.util.TopicListHelper.getTopicList
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class LatestTopicListWrapper(
    private val prevProcessedCommitRevIdFileName: String,
    private val preferJgit: Boolean,
    private val gitRepoHolder: GitRepoHolder
) : Handler {
    private val log = LoggerFactory.getLogger(LatestTopicListWrapper::class.java)
    private val lock = ReentrantLock()
    private val topicListCache: LoadingCache<SpaceType, List<Int>> =
        Caffeine.newBuilder()
            .maximumSize(5)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build { spaceType ->
                getTopicList(
                    spaceType,

                    prevProcessedCommitRevIdFileName,
                    preferJgit,
                    gitRepoHolder
                )
            }

    override fun handle(ctx: Context) {
        val spaceType = checkAndExtractSpaceTypeInContext(ctx)
        try {
            if (!lock.tryLock(30, TimeUnit.SECONDS)) {
                ctx.status(HttpStatus.GATEWAY_TIMEOUT)
                ctx.html("The server is busy. Please wait and refresh later.")
                return
            }
            ctx.json(topicListCache[spaceType])
        } catch (ex: Exception) {
            log.error("Ex: ", ex)
            throw ex
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
        }
    }
}