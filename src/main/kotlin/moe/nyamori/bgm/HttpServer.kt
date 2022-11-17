package moe.nyamori.bgm

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.Header.CACHE_CONTROL
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
                .get("/history/group/{topicId}", FileHistory(SpaceType.GROUP))
                .get("/history/group/{topicId}/{timestamp}/html", FileOnCommit(SpaceType.GROUP, isRaw = true))
                .get("/history/group/{topicId}/{timestamp}", FileOnCommit(SpaceType.GROUP))
                .get("/history/subject/{topicId}", FileHistory(SpaceType.SUBJECT))
                .get("/history/subject/{topicId}/{timestamp}/html", FileOnCommit(SpaceType.SUBJECT, isRaw = true))
                .get("/history/subject/{topicId}/{timestamp}", FileOnCommit(SpaceType.SUBJECT))
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

        class FileHistory(private val spaceType: SpaceType) : Handler {
            override fun handle(ctx: Context) {
                val topicId = ctx.pathParam("topicId").toInt()
                val timestampList = FileHistoryLookup.getJsonTimestampList(
                    spaceType.name.lowercase() + "/" + FilePathHelper.numberToPath(topicId) + ".json"
                )
                ctx.header(CACHE_CONTROL, "max-age=86400")
                ctx.json(timestampList)
            }
        }

        class FileOnCommit(private val spaceType: SpaceType, private val isRaw: Boolean = false) : Handler {
            override fun handle(ctx: Context) {
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
                    ctx.redirect(ctx.path().replace(timestampPathParam, ceilingTimestamp.toString()))
                    return
                }

                // ctx.header(CACHE_CONTROL, "max-age=86400")
                if (isRaw) {
                    var html = GitHelper.getFileContentInACommit(
                        GitHelper.archiveRepoSingleton,
                        FileHistoryLookup.getArchiveCommitAtTimestamp(relativePath, timestamp),
                        relativePath
                    )
                    html = html.replace("chii.in", "bgm.tv")
                    html = html.replace("bangumi.tv", "bgm.tv")
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
            }

        }
    }
}