package moe.nyamori.bgm

import io.javalin.Javalin
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.CommitToJsonProcessor.blockAndPrintProcessResults
import moe.nyamori.bgm.http.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.locks.ReentrantLock

class HttpServer {
    companion object {
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

    }
}