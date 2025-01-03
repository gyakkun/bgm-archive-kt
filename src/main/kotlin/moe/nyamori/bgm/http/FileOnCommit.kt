package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.Header.CACHE_CONTROL
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.git.GitHelper.simpleName
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.lowercaseName
import moe.nyamori.bgm.util.HttpHelper
import moe.nyamori.bgm.util.ParserHelper.getStyleRevNumberFromHtmlString
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit

class FileOnCommit(
    private val spaceType: SpaceType,
    private val isHtml: Boolean = false,
    private val fileHistoryLookup: FileHistoryLookup
) : Handler {
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
                    fileHistoryLookup.getArchiveTimestampList(spaceType, topicId)
                } else {
                    fileHistoryLookup.getJsonTimestampList(spaceType, topicId)
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
                    var (cp, html) = fileHistoryLookup.getArchiveFileHashMsgContentAsStringAtTimestamp(
                        spaceType,
                        topicId,
                        timestamp
                    )
                    fillMetaHeader(ctx, cp)
                    html = htmlModifier(html)
                    ctx.html(html)
                } else {
                    val (cp, jsonStr) = fileHistoryLookup.getJsonFileHashMsgContentAsStringTimestamp(
                        spaceType,
                        topicId,
                        timestamp
                    )
                    fillMetaHeader(ctx, cp)
                    ctx.json(jsonStr)
                }
            } finally {
                HttpHelper.DB_READ_SEMAPHORE.release()
            }
        } catch (ex: Exception) {
            log.error("Ex: ", ex)
            throw ex
        }
    }

    private fun fillMetaHeader(ctx: Context, cp: FileHistoryLookup.ChatamPair) = with(ctx) {
        header("x-bak-hrn", cp.html.repoDto.repo.simpleName())
        header("x-bak-jrn", cp.json.repoDto.repo.simpleName())
        header("x-bak-hch", cp.html.hash)
        header("x-bak-jch", cp.json.hash)
        header("x-bak-hcm", cp.html.msg)
        header("x-bak-jcm", cp.json.msg)
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
