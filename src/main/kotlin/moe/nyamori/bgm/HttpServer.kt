package moe.nyamori.bgm

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.Header.CACHE_CONTROL
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.CommitToJsonProcessor
import moe.nyamori.bgm.git.CommitToJsonProcessor.blockAndPrintProcessResults
import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.FilePathHelper
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.IllegalArgumentException
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class HttpServer {
    companion object {
        private val lock = ReentrantLock()
        private val log = LoggerFactory.getLogger(HttpServer.javaClass)

        @JvmStatic
        fun main(args: Array<String>) {

            val app = Javalin.create { config ->
                config.compression.brotliAndGzip()
                /*config.plugins.enableCors { cors ->

                }*/
            }
                .get("/after-commit-hook", CommitHook())
                .get("/img/*") { ctx ->
                    ctx.redirect("https://bgm.tv" + ctx.path())
                    return@get
                }
                .get("/history/{spaceType}/latest_topic_list", LatestTopicListWrapper)
                .get("/history/{spaceType}/{topicId}", FileHistoryWrapper)
                .get("/history/{spaceType}/{topicId}/link", LinkHandlerWrapper)
                .get("/history/{spaceType}/{topicId}/{timestamp}/html", FileOnCommitWrapper(isHtml = true))
                .get("/history/{spaceType}/{topicId}/{timestamp}", FileOnCommitWrapper())
                .get("/history/status") { ctx ->
                    ctx.json(
                        mapOf(
                            "jsonRepoStatus" to run {
                                val gitProcess = Runtime.getRuntime()
                                    .exec("git count-objects -vH", null, File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR))
                                gitProcess.blockAndPrintProcessResults().map {
                                    it.split(":")
                                }.associate { Pair(it[0].trim(), it[1].trim()) }
                            },
                            "archiveRepoStatus" to run {
                                val gitProcess = Runtime.getRuntime()
                                    .exec("git count-objects -vH", null, File(Config.BGM_ARCHIVE_GIT_REPO_DIR))
                                gitProcess.blockAndPrintProcessResults().map {
                                    it.split(":")
                                }.associate { Pair(it[0].trim(), it[1].trim()) }
                            }
                        )
                    )
                }
                .start(Config.BGM_ARCHIVE_ADDRESS, Config.BGM_ARCHIVE_PORT)
            Runtime.getRuntime().addShutdownHook(Thread {
                app.stop()
            })
        }

        private fun checkAndExtractSpaceTypeInContext(ctx: Context): SpaceType {
            val spaceTypeFromPath = ctx.pathParam("spaceType")
            if (SpaceType.values().none {
                    it.name.equals(spaceTypeFromPath, ignoreCase = true)
                }
            ) {
                throw IllegalArgumentException("$spaceTypeFromPath is not a supported topic type")
            }
            return SpaceType.valueOf(spaceTypeFromPath.uppercase())
        }

        object FileHistoryWrapper : Handler {

            private val fileHistoryJsonHandlerMap = mapOf<SpaceType, Handler>(
                SpaceType.GROUP to FileHistory(SpaceType.GROUP),
                SpaceType.SUBJECT to FileHistory(SpaceType.SUBJECT),
                SpaceType.BLOG to FileHistory(SpaceType.BLOG)
            )

            override fun handle(ctx: Context) {
                val spaceType = checkAndExtractSpaceTypeInContext(ctx)
                fileHistoryJsonHandlerMap[spaceType]!!.handle(ctx)
            }
        }

        class FileOnCommitWrapper(val isHtml: Boolean = false) : Handler {
            private val fileOnCommitJsonHandlerMap = mapOf<SpaceType, Handler>(
                SpaceType.GROUP to FileOnCommit(SpaceType.GROUP),
                SpaceType.SUBJECT to FileOnCommit(SpaceType.SUBJECT),
                SpaceType.BLOG to FileOnCommit(SpaceType.BLOG)
            )

            private val fileOnCommitHtmlHandlerMap = mapOf<SpaceType, Handler>(
                SpaceType.GROUP to FileOnCommit(SpaceType.GROUP, isHtml = true),
                SpaceType.SUBJECT to FileOnCommit(SpaceType.SUBJECT, isHtml = true),
                SpaceType.BLOG to FileOnCommit(SpaceType.BLOG, isHtml = true)
            )

            override fun handle(ctx: Context) {
                val spaceType = checkAndExtractSpaceTypeInContext(ctx)
                if (isHtml) {
                    fileOnCommitHtmlHandlerMap[spaceType]!!.handle(ctx)
                } else {
                    fileOnCommitJsonHandlerMap[spaceType]!!.handle(ctx)
                }
            }
        }

        object LinkHandlerWrapper : Handler {
            override fun handle(ctx: Context) {
                checkAndExtractSpaceTypeInContext(ctx)
                LinkHandler.handle(ctx)
            }
        }

        object LatestTopicListWrapper : Handler {
            private val lock = ReentrantLock()
            private val topicListCache: LoadingCache<SpaceType, List<Int>> =
                Caffeine.newBuilder()
                    .maximumSize(5)
                    .expireAfterWrite(Duration.ofMinutes(30))
                    .build { spaceType ->
                        getTopicList(spaceType)
                    }

            private fun getTopicList(spaceType: SpaceType): List<Int> {
                val topicListFile: String = GitHelper.archiveRepoSingleton.getFileContentAsStringInACommit(
                    GitHelper.getPrevProcessedCommitRef(),
                    spaceType.name.lowercase() + "/topiclist.txt"
                )
                return topicListFile.lines().mapNotNull { it.toIntOrNull() }.sorted()
            }

            override fun handle(ctx: Context) {
                val spaceType = checkAndExtractSpaceTypeInContext(ctx)
                try {
                    if (!lock.tryLock(30, TimeUnit.SECONDS)) {
                        ctx.status(HttpStatus.REQUEST_TIMEOUT)
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

        class CommitHook : Handler {
            override fun handle(ctx: Context) {
                if (Config.BGM_ARCHIVE_DISABLE_HOOK) {
                    ctx.status(400)
                    return
                }
                Thread {
                    try {
                        if (lock.tryLock(10, TimeUnit.SECONDS)) {
                            CommitToJsonProcessor.job()
                        }
                    } catch (ignore: Exception) {

                    } finally {
                        if (lock.isHeldByCurrentThread) {
                            lock.unlock()
                        }
                    }
                }.start()
                ctx.status(200)
            }
        }

        class FileHistory(private val spaceType: SpaceType, private val isHtml: Boolean = false) : Handler {
            override fun handle(ctx: Context) {
                try {
                    if (!lock.tryLock(30, TimeUnit.SECONDS)) {
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
                    if (lock.isHeldByCurrentThread) lock.unlock()
                }
            }
        }

        class FileOnCommit(private val spaceType: SpaceType, private val isHtml: Boolean = false) : Handler {
            override fun handle(ctx: Context) {
                try {
                    if (!lock.tryLock(30, TimeUnit.SECONDS)) {
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
                        var html = GitHelper.archiveRepoSingleton.getFileContentAsStringInACommit(
                            FileHistoryLookup.getArchiveCommitAtTimestamp(relativePath, timestamp),
                            relativePath
                        )
                        html = htmlModifier(html, timestamp)

                        ctx.html(html)
                    } else {
                        ctx.json(
                            GitHelper.jsonRepoSingleton.getFileContentAsStringInACommit(
                                FileHistoryLookup.getJsonCommitAtTimestamp(relativePath, timestamp),
                                relativePath
                            )
                        )
                    }
                } catch (ex: Exception) {
                    log.error("Ex: ", ex)
                    throw ex
                } finally {
                    if (lock.isHeldByCurrentThread) lock.unlock()
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

        object LinkHandler : Handler {

            override fun handle(ctx: Context) {
                ctx.html(html)
            }

            const val html = """
<html>

<body>
    <div id="content">Loading...</div>
</body>
<script type="text/javascript">
    const onErr = () => {
        let content = document.getElementById("content")
        content.innerHTML = "Error"
    }
    const onNoContent = () => {
        let content = document.getElementById("content")
        content.innerHTML = "No content"
    }
    const getTimeline = async () => {
        let path = document.location.pathname
        let timelineList = []
        let jsonListPath = path.replace("/link", "")
        timelineList = await fetch(jsonListPath)
            .then(d => { return d.text() })
            .then(t => { return JSON.parse(t) })
            .catch(e => { onErr(); console.error("Ex: ", e) })
        return timelineList
    }

    const go = async () => {
        let timelineJsonArr = await getTimeline()
        if (timelineJsonArr.length == 0) {
            onNoContent()
            return
        }
        let cele = document.getElementById("content")
        let ulele = document.createElement("ul")
        cele.innerHTML = ""
        cele.appendChild(ulele)
        timelineJsonArr.forEach(element => {
            let li = document.createElement("li")
            let a = document.createElement("a")
            a.innerHTML = new Date(element).toLocaleString()
            a.setAttribute("href", document.location.pathname.replace("/link", "/") + element + "/html")
            li.appendChild(a)
            ulele.appendChild(li)
        });

    }
    go()
</script>
<style type="text/css">
    body {
        color: #222;
        background: #fff;
        font: 100% system-ui;
    }

    a {
        color: #0033cc;
    }

    @media (prefers-color-scheme: dark) {
        body {
            color: #eee;
            background: #121212;
        }

        body a {
            color: #afc3ff;
        }
    }
</style>

</html>
            """


        }
    }
}