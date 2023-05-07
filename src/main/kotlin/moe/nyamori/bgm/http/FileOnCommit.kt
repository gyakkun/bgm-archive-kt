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
import java.util.*
import java.util.concurrent.TimeUnit

class FileOnCommit(private val spaceType: SpaceType, private val isHtml: Boolean = false) : Handler {
    private val log = LoggerFactory.getLogger(FileHistory::class.java)
    override fun handle(ctx: Context) {
        try {
            if (!GIT_RELATED_LOCK.tryLock(30, TimeUnit.SECONDS)) {
                ctx.status(HttpStatus.REQUEST_TIMEOUT)
                ctx.html("The server is busy. Please wait and refresh later.")
                return
            }
            val topicId = ctx.pathParam("topicId").toInt()
            val timestampPathParam = ctx.pathParam("timestamp")
            val timestamp =
                if (timestampPathParam == "latest") Long.MAX_VALUE
                else if (timestampPathParam.toLongOrNull() != null) timestampPathParam.toLong()
                else -1L
            val relativePath =
                spaceType.name.lowercase() + "/" + FilePathHelper.numberToPath(topicId) + if (isHtml) ".html" else ".json"
            val timestampList = if (isHtml) {
                FileHistoryLookup.getArchiveTimestampList(relativePath)
            } else {
                FileHistoryLookup.getJsonTimestampList(relativePath)
            }
            val ts = TreeSet<Long>().apply {
                addAll(timestampList)
            }
            if (!ts.contains(timestamp)) {
                var ceilingTimestamp = ts.floor(timestamp)
                if (ceilingTimestamp == null) {
                    ceilingTimestamp = ts.first()
                }
                ctx.redirect(
                    if (isHtml) {
                        ctx.path().replace(Regex("/${timestampPathParam}/html"), "/${ceilingTimestamp}/html")
                    } else {
                        ctx.path().replace(Regex("/${timestampPathParam}/*\$"), "/${ceilingTimestamp}")
                    }
                )
                return
            }

            ctx.header(CACHE_CONTROL, "max-age=86400")
            if (isHtml) {
                var html = FileHistoryLookup.getArchiveFileContentAsStringAtTimestamp(
                    timestamp,
                    relativePath
                )
                html = htmlModifier(html, timestamp)

                ctx.html(html)
            } else {
                ctx.json(
                    FileHistoryLookup.getJsonFileContentAsStringAtTimestamp(
                        timestamp,
                        relativePath
                    )
                )
            }
        } catch (ex: Exception) {
            log.error("Ex: ", ex)
            throw ex
        } finally {
            if (GIT_RELATED_LOCK.isHeldByCurrentThread) GIT_RELATED_LOCK.unlock()
        }
    }

    private fun htmlModifier(html: String, timestamp: Long): String {
        var result = html
        result = result.replace("chii.in", "bgm.tv")
        result = result.replace("bangumi.tv", "bgm.tv")
        result = result.replace("data-theme=\"light\"", "data-theme=\"dark\"")
        result = result.replace("//lain.bgm.tv", "https://lain.bgm.tv")
        result = result.replace("src=\"/", "src=\"https://bgm.tv/")
        result = result.replace("href=\"/", "href=\"https://bgm.tv/")

        // R412 "Tietie"
        if (timestamp >= 1680307200000) {
            result = result.replace(
                "</body>", """
                                            <script src="https://bgm.tv/min/g=ui?r412" type="text/javascript"></script>
                                            <script src="https://bgm.tv/min/g=mobile?r412" type="text/javascript"></script>
                                            <script type="text/javascript">chiiLib.topic_history.init();chiiLib.likes.init();</script>
                                            </body>
                                        """.trimIndent()
            )
        }
        return result
    }
}
