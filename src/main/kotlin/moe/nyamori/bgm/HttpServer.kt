package moe.nyamori.bgm

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.Header.CACHE_CONTROL
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.CommitToJsonProcessor
import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.FilePathHelper
import org.slf4j.LoggerFactory
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
                .get("/history/group/{topicId}", FileHistory(SpaceType.GROUP, isRaw = true))
                .get("/history/group/{topicId}/link", LinkHandler)
                .get("/history/group/{topicId}/{timestamp}/html", FileOnCommit(SpaceType.GROUP, isRaw = true))
//                .get("/history/group/{topicId}/{timestamp}", FileOnCommit(SpaceType.GROUP))
                .get("/history/subject/{topicId}", FileHistory(SpaceType.SUBJECT, isRaw = true))
                .get("/history/subject/{topicId}/link", LinkHandler)
                .get("/history/subject/{topicId}/{timestamp}/html", FileOnCommit(SpaceType.SUBJECT, isRaw = true))
//                .get("/history/subject/{topicId}/{timestamp}", FileOnCommit(SpaceType.SUBJECT))
                .get("/history/blog/{topicId}", FileHistory(SpaceType.BLOG, isRaw = true))
                .get("/history/blog/{topicId}/link", LinkHandler)
                .get("/history/blog/{topicId}/{timestamp}/html", FileOnCommit(SpaceType.BLOG, isRaw = true))
                .start(Config.BGM_ARCHIVE_ADDRESS, Config.BGM_ARCHIVE_PORT)
            Runtime.getRuntime().addShutdownHook(Thread {
                app.stop()
            })
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

        class FileHistory(private val spaceType: SpaceType, private val isRaw: Boolean = false) : Handler {
            override fun handle(ctx: Context) {
                try {
                    if (!lock.tryLock(30, TimeUnit.SECONDS)) {
                        ctx.status(HttpStatus.REQUEST_TIMEOUT)
                        ctx.html("The server is busy. Please wait and refresh later.")
                        return
                    }
                    val topicId = ctx.pathParam("topicId").toInt()
                    val timestampList = if (isRaw) {
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

        class FileOnCommit(private val spaceType: SpaceType, private val isRaw: Boolean = false) : Handler {
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
                        spaceType.name.lowercase() + "/" + FilePathHelper.numberToPath(topicId) + if (isRaw) ".html" else ".json"
                    val timestampList = if (isRaw) {
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
                            if (isRaw) {
                                ctx.path().replace(Regex("/${timestampPathParam}/html"), "/${ceilingTimestamp}/html")
                            } else {
                                ctx.path().replace(Regex("/${timestampPathParam}/*\$"), "/${ceilingTimestamp}")
                            }
                        )
                        return
                    }

                    ctx.header(CACHE_CONTROL, "max-age=86400")
                    if (isRaw) {
                        var html = GitHelper.getFileContentInACommit(
                            GitHelper.archiveRepoSingleton,
                            FileHistoryLookup.getArchiveCommitAtTimestamp(relativePath, timestamp),
                            relativePath
                        )
                        html = html.replace("chii.in", "bgm.tv")
                        html = html.replace("bangumi.tv", "bgm.tv")
                        html = html.replace("//lain.bgm.tv", "https://lain.bgm.tv")
                        html = html.replace("src=\"/", "src=\"https://bgm.tv/")
                        html = html.replace("href=\"/", "href=\"https://bgm.tv/")
                        ctx.html(html)
                    } else {
                        ctx.json(
                            GitHelper.getFileContentInACommit(
                                GitHelper.jsonRepoSingleton,
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