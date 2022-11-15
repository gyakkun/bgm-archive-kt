package moe.nyamori.bgm

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.Header.CACHE_CONTROL
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
                .get("/history/group/{topicId}/{timestamp}", FileOnCommit(SpaceType.GROUP))
                .get("/history/subject/{topicId}", FileHistory(SpaceType.SUBJECT))
                .get("/history/subject/{topicId}/{timestamp}", FileOnCommit(SpaceType.SUBJECT))
                .start(5926)
            Runtime.getRuntime().addShutdownHook(Thread {
                app.stop()
            })
        }

        class CommitHook : Handler {
            override fun handle(ctx: Context) {
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

        class FileOnCommit(private val spaceType: SpaceType) : Handler {
            override fun handle(ctx: Context) {
                val topicId = ctx.pathParam("topicId").toInt()
                val timestamp = ctx.pathParam("timestamp").toLong()
                val relativePath = spaceType.name.lowercase() + "/" + FilePathHelper.numberToPath(topicId) + ".json"
                val timestampList = FileHistoryLookup.getJsonTimestampList(
                    relativePath
                )
                val ts = TreeSet<Long>().apply {
                    addAll(timestampList)
                }
                if (!ts.contains(timestamp)) {
                    var ceilingTimestamp = ts.floor(timestamp)
                    if (ceilingTimestamp == null) {
                        ceilingTimestamp = ts.first()
                    }
                    ctx.redirect(ctx.path().replace(timestamp.toString(), ceilingTimestamp.toString()))
                    return
                }

                ctx.header(CACHE_CONTROL, "max-age=86400")
                ctx.json(
                    GitHelper.getFileContentInACommit(
                        GitHelper.getJsonRepo(),
                        FileHistoryLookup.getJsonCommitAtTimestamp(relativePath, timestamp),
                        relativePath
                    )
                )
            }

        }
    }
}