package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.Header.CACHE_CONTROL
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.lowercaseName
import moe.nyamori.bgm.util.HttpHelper
import moe.nyamori.bgm.util.ParserHelper.getStyleRevNumberFromHtmlString
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit

class FileOnCommit(private val spaceType: SpaceType, private val isHtml: Boolean = false) : Handler {
    private val log = LoggerFactory.getLogger(FileHistory::class.java)
    override fun handle(ctx: Context) {
        try {
            // Since we no longer rely on git repo to get commit history
            // Here should be the db read semaphore
            if (!HttpHelper.DB_READ_SEMAPHORE.tryAcquire(30, TimeUnit.SECONDS)) {
                ctx.status(HttpStatus.GATEWAY_TIMEOUT)
                ctx.html("The server is busy. Please wait and refresh later.")
                return
            }
            try {
                val topicId = ctx.pathParam("topicId").toInt()
                val timestampPathParam = ctx.pathParam("timestamp")
                val timestamp =
                    if (timestampPathParam == "latest") Long.MAX_VALUE
                    else if (timestampPathParam.toLongOrNull() != null) timestampPathParam.toLong()
                    else -1L
                val timestampList = if (isHtml) {
                    FileHistoryLookup.getArchiveTimestampList(spaceType, topicId)
                } else {
                    FileHistoryLookup.getJsonTimestampList(spaceType, topicId)
                }
                val ts = TreeSet<Long>().apply {
                    addAll(timestampList)
                }
                if (ts.isEmpty()) {
                    ctx.status(HttpStatus.BAD_REQUEST)
                    ctx.html(
                        """<html><body><p>No content found for ${spaceType.lowercaseName()}/$topicId (yet).</p></body>
                    <style type="text/css">@media (prefers-color-scheme: dark) {body {color: #eee;background: #121212;}</style></html>""".trimMargin()
                    )
                    return
                }
                if (!ts.contains(timestamp)) {
                    var floorTimestamp = ts.floor(timestamp)
                    if (floorTimestamp == null) {
                        floorTimestamp = ts.first()
                    }
                    ctx.redirect(
                        if (isHtml) {
                            ctx.path().replace(Regex("/${timestampPathParam}/html"), "/${floorTimestamp}/html")
                        } else {
                            ctx.path().replace(Regex("/${timestampPathParam}/*\$"), "/${floorTimestamp}")
                        }
                    )
                    return
                }

                ctx.header(CACHE_CONTROL, "max-age=86400")
                if (isHtml) {
                    var html = FileHistoryLookup.getArchiveFileContentAsStringAtTimestamp(
                        spaceType,
                        topicId,
                        timestamp
                    )
                    html = htmlModifier(html)
                    ctx.html(html)
                } else {
                    ctx.json(
                        FileHistoryLookup.getJsonFileContentAsStringAtTimestamp(
                            spaceType,
                            topicId,
                            timestamp
                        )
                    )
                }
            } finally {
                HttpHelper.DB_READ_SEMAPHORE.release()
            }
        } catch (ex: Exception) {
            log.error("Ex: ", ex)
            throw ex
        }
    }

    private fun htmlModifier(html: String): String {
        var result = html
        val rev = getStyleRevNumberFromHtmlString(html)
        result = result
            .replace("chii.in", "bgm.tv")
            .replace("bangumi.tv", "bgm.tv")
            .replace("data-theme=\"light\"", "data-theme=\"dark\"")
            .replace(
            "</body>",
            """
                <script src="https://bgm.tv/min/g=ui?r$rev" type="text/javascript"></script>
                <script src="https://bgm.tv/min/g=mobile?r$rev" type="text/javascript"></script>
                <script type="text/javascript">chiiLib.topic_history.init();chiiLib.likes.init();</script>
                </body>
            """.trimIndent()
        )
        return result
    }
}
