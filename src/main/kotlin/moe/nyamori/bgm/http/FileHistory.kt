package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.Header.CACHE_CONTROL
import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.HttpHelper.GIT_RELATED_LOCK
import org.slf4j.LoggerFactory

class FileHistory(private val spaceType: SpaceType) : Handler {
    private val log = LoggerFactory.getLogger(FileHistory::class.java)

    override fun handle(ctx: Context) {
        try {
            // Since we use cache in db now, no need to lock on git repo
            // if (!GIT_RELATED_LOCK.tryLock(Config.gitRelatedLockTimeoutMs * 1.5, TimeUnit.MILLISECONDS)) {
            //    ctx.status(HttpStatus.GATEWAY_TIMEOUT)
            //    ctx.html("The server is busy. Please wait and refresh later.")
            //    return
            // }
            val isHtml = ctx.queryParam("isHtml")?.toBooleanStrictOrNull() ?: false
            val topicId = ctx.pathParam("topicId").toInt()
            val timestampList = if (isHtml) {
                FileHistoryLookup.getArchiveTimestampList(spaceType, topicId)
            } else {
                FileHistoryLookup.getJsonTimestampList(spaceType, topicId)
            }
            val filtered = filterBySpaceBlockList(this.spaceType, topicId, timestampList)
            ctx.header(CACHE_CONTROL, "max-age=3600")
            ctx.json(filtered)
        } catch (ex: Exception) {
            log.error("Ex: ", ex)
            throw ex
        } finally {
            if (GIT_RELATED_LOCK.isHeldByCurrentThread) GIT_RELATED_LOCK.unlock()
        }
    }
}