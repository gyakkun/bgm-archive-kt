package moe.nyamori.bgm.http

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.Header.CACHE_CONTROL
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.git.GitHelper.simpleName
import moe.nyamori.bgm.model.Space
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.model.lowercaseName
import moe.nyamori.bgm.util.BinarySearchHelper
import moe.nyamori.bgm.util.HttpHelper
import moe.nyamori.bgm.util.ParserHelper.getStyleRevNumberFromHtmlString
import moe.nyamori.bgm.util.SealedTypeAdapterFactory
import org.slf4j.LoggerFactory
import java.time.Instant
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
                val filtered = filterBySpaceBlockList(this.spaceType, topicId, timestampList)
                val treeSet = TreeSet(filtered)
                if (treeSet.isEmpty()) {
                    ctx.status(HttpStatus.BAD_REQUEST)
                    ctx.html(
                        """<html><body><p>No content found for ${spaceType.lowercaseName()}/$topicId (yet).</p></body>
                    <style type="text/css">@media (prefers-color-scheme: dark) {body {color: #eee;background: #121212;}</style></html>""".trimMargin()
                    )
                    return
                }
                if (!treeSet.contains(timestamp)) {
                    var floorTimestamp = treeSet.floor(timestamp)
                    if (floorTimestamp == null) {
                        floorTimestamp = treeSet.first()
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
                    var (cp, html) = FileHistoryLookup.getArchiveFileHashMsgContentAsStringAtTimestamp(
                        spaceType,
                        topicId,
                        timestamp
                    )
                    fillMetaHeader(ctx, cp)
                    html = htmlModifier(html)
                    ctx.html(html)
                } else {
                    val (cp, jsonStr) = FileHistoryLookup.getJsonFileHashMsgContentAsStringTimestamp(
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
        header("x-bak-hrn", cp.html.repo.simpleName())
        header("x-bak-jrn", cp.json.repo.simpleName())
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

private val SBLOGGER = LoggerFactory.getLogger("SpaceBlocker")

internal fun filterBySpaceBlockList(
    spaceType: SpaceType,
    topicId: Int,
    timestampList: SortedSet<Long>
): List<Long?> {
    if (timestampList.isEmpty()) return emptyList()
    val topicDtoList = Dao.bgmDao.getTopicListByTypeAndTopicId(spaceType.id, topicId)
    val topicDto = topicDtoList.firstOrNull() ?: return emptyList()
    val spaceNameMapping = Dao.bgmDao.getSpaceNamingMappingByTypeAndSid(spaceType.id, topicDto.sid)
    val validBlockers = Config.spaceBlockList
        .mapNotNull {
            it.validateOrNull()
        }.filter {
            spaceType.name.equals(it.spaceType, true)
                    && spaceNameMapping.isNotEmpty()
                    && spaceNameMapping.first().name == it.spaceName
        }
    if (validBlockers.isEmpty()) return timestampList.toList()

    // We assume that a thread can be moved to a blocked space
    // some time in the future.
    // Reference the feh deleted post handler
    val GSON = GsonBuilder()
        .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .registerTypeAdapterFactory(
            SealedTypeAdapterFactory.of(Space::class)
        ).create()
    val topicAtTs = fun(ts: Long): Topic {
        return GSON.fromJson(
            FileHistoryLookup.getJsonFileContentAsStringAtTimestamp(
                spaceType, topicId, ts
            ), Topic::class.java
        )
    }

    // Use binary search to find the last/max timestamp that it's not blocked
    // aka ceiling
    val bsFunMaxNonBlockPostGen =
        BinarySearchHelper.binarySearchFunctionGenerator<Long>(BinarySearchHelper.BSType.CEILING)

    val lastTsNotBlocked = bsFunMaxNonBlockPostGen(timestampList.toList()) { ts ->
        val ins = Instant.ofEpochMilli(ts)
        val topicAtm = topicAtTs(ts)
        // return true if it's NOT blocked
        validBlockers.none { b ->
            val (startIns, endIns) = b.blockRange?.toInstantPairOrNull()
                ?: return@none false
            (topicAtm.space?.name == b.spaceName && ins in startIns..endIns)
        }
    }
    if (lastTsNotBlocked == null) return emptyList()
    val res = timestampList.subSet(timestampList.first, lastTsNotBlocked + 1L)
    val sizeDiff = timestampList.size - res.size
    if (sizeDiff != 0) SBLOGGER.info(
        "Blocked {}/{} captures for {} topic {} : {} - {}",
        sizeDiff,
        timestampList.size,
        spaceType,
        topicId,
        spaceNameMapping.firstOrNull()?.displayName,
        topicDto.title
    )
    return res.toList()
}
