package moe.nyamori.bgm

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.Header.CONTENT_TYPE
import io.javalin.http.HttpStatus
import io.javalin.plugin.bundled.RouteOverviewUtil.metaInfo
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.http.*
import moe.nyamori.bgm.util.StringHashingHelper

object HttpServer {

    @JvmStatic
    fun main(args: Array<String>) {
        val app = Javalin.create { config ->
            config.useVirtualThreads = true
            config.http.brotliAndGzipCompression()
            config.bundledPlugins.enableCors { cors ->
                cors.addRule {
                    it.allowHost("bgm.tv", "bangumi.tv", "chii.in")
                    it.allowHost("*.bgm.tv", "*.bangumi.tv", "*.chii.in")
                }
            }
            config.router.apiBuilder {
                path("/history") {
                    path("/{spaceType}") {
                        get("/latest_topic_list", LatestTopicListWrapper)
                        path("/{topicId}") {
                            get(FileHistoryWrapper)
                            get("/link", LinkHandlerWrapper)
                            path("/{timestamp}") {
                                get(FileOnCommitWrapper())
                                get("/html", FileOnCommitWrapper(isHtml = true))
                            }
                        }

                    }
                    get("/status", RepoStatusHandler)
                }
                path("/info") {
                    get("/meta") {
                        if (!it.isLocalhost()) {
                            it.status(HttpStatus.NOT_FOUND)
                            return@get
                        }
                        it.result(
                            GitHelper.GSON.toJson(
                                Dao.bgmDao().getAllMetaData().associate { it.k to it.v }
                            )
                        )
                    }
                    path("/repo") {
                        get("/html") {
                            if (!it.isLocalhost()) {
                                it.status(HttpStatus.NOT_FOUND)
                                return@get
                            }
                            it.result(
                                GitHelper.GSON.toJson(
                                    GitHelper.allArchiveRepoListSingleton.mapIndexed { idx, repo ->
                                        Pair(
                                            idx,
                                            StringHashingHelper.stringHash(repo.absolutePathWithoutDotGit())
                                        ) to repo.absolutePathWithoutDotGit()
                                    }.toMap()
                                )
                            )
                        }
                        get("/json") {
                            if (!it.isLocalhost()) {
                                it.status(HttpStatus.NOT_FOUND)
                                return@get
                            }
                            it.result(
                                GitHelper.GSON.toJson(
                                    GitHelper.allJsonRepoListSingleton.mapIndexed { idx, repo ->
                                        Pair(
                                            idx,
                                            StringHashingHelper.stringHash(repo.absolutePathWithoutDotGit())
                                        ) to repo.absolutePathWithoutDotGit()
                                    }.toMap()
                                )
                            )
                        }
                    }
                }
                path("/hook") {
                    path("/db") {
                        get("/persist", DbPersistHook)
                        get("/reset", DbSetPersistIdHandler)
                        get("/purge", DbPurgeAllMetaHandler)
                    }

                    get("/commit", CommitHook)
                }
                path("/forum-enhance") {
                    post("/query", ForumEnhanceHandler)
                    get("/deleted_post/{type}/{topicId}/{postId}", FehDeletedPostHandler)
                }
                get("/*") { // redirect all
                    it.redirect("https://bgm.tv" + it.path())
                }
                after {
                    if (it.res().contentType == ContentType.APPLICATION_JSON.mimeType) {
                        it.contentType(ContentType.APPLICATION_JSON.mimeType + "; charset=utf-8")
                    }
                }
            }
        }.start(Config.BGM_ARCHIVE_ADDRESS, Config.BGM_ARCHIVE_PORT)

        Runtime.getRuntime().addShutdownHook(Thread
        {
            app.stop()
        })
    }

    private fun ip(ctx: Context) = ctx.header("X-Forwarded-For")?.split(",")?.get(0) ?: ctx.ip()
    private fun Context.isLocalhost() = ip(this).let { it == "localhost" || it == "127.0.0.1" }
}

