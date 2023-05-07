package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.Header.CACHE_CONTROL
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.FilePathHelper
import moe.nyamori.bgm.util.HttpHelper.GIT_RELATED_LOCK
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class FileHistory(private val spaceType: SpaceType, private val isHtml: Boolean = false) : Handler {
    private val log = LoggerFactory.getLogger(FileHistory::class.java)

    override fun handle(ctx: Context) {
        try {
            if (!GIT_RELATED_LOCK.tryLock(30, TimeUnit.SECONDS)) {
                ctx.status(HttpStatus.REQUEST_TIMEOUT)
                ctx.html("The server is busy. Please wait and refresh later.")
                return
            }
            val topicId = ctx.pathParam("topicId").toInt()
            val timestampList = if (isHtml) {
                FileHistoryLookup.getArchiveTimestampList(
                    spaceType.name.lowercase() + "/" + FilePathHelper.numberToPath(topicId) + ".html"
                )
            } else {
                FileHistoryLookup.getJsonTimestampList(
                    spaceType.name.lowercase() + "/" + FilePathHelper.numberToPath(topicId) + ".json"
                )
            }
            ctx.header(CACHE_CONTROL, "max-age=3600")
            ctx.json(timestampList)
        } catch (ex: Exception) {
            log.error("Ex: ", ex)
            throw ex
        } finally {
            if (GIT_RELATED_LOCK.isHeldByCurrentThread) GIT_RELATED_LOCK.unlock()
        }
    }
}