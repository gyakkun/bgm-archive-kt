package moe.nyamori.bgm

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.SpotChecker
import moe.nyamori.bgm.http.*
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.RangeHelper
import moe.nyamori.bgm.util.StringHashingHelper
import java.io.File
import java.util.*

object HttpServer {

    @JvmStatic
    fun main(args: Array<String>) {
        val app = Javalin.create { config ->
            config.useVirtualThreads = true
            config.http.brotliAndGzipCompression()
            // Global json pretty
            // config.jsonMapper(JavalinJackson().updateMapper{
            //     it.enable(SerializationFeature.INDENT_OUTPUT)
            // })
            config.bundledPlugins.enableCors { cors ->
                cors.addRule {
                    it.allowHost("bgm.tv", "bangumi.tv", "chii.in")
                    it.allowHost("*.bgm.tv", "*.bangumi.tv", "*.chii.in")
                }
            }
            config.router.apiBuilder {
                get("/status", AppStatusHandler)
                get("/holes") { it.redirect("/holes/blog") }
                path("/holes") {
                    get("/{spaceType}") {
                        val spaceTypeParam = it.pathParam("spaceType")
                        if (spaceTypeParam.uppercase() !in SpaceType.entries.map { it.name }) {
                            it.redirect("/holes/blog")
                            return@get
                        }
                        val spaceType = SpaceType.valueOf(spaceTypeParam.uppercase())
                        it.html(
                            RangeHelper.checkHolesForType(spaceType).joinToString("\n")
                        )
                    }
                    post("/{spaceType}") {
                        val spaceTypeParam = it.pathParam("spaceType")
                        if (spaceTypeParam.uppercase() !in SpaceType.entries.map { it.name }) {
                            throw HttpResponseException(HttpStatus.BAD_REQUEST)
                        }
                        val spaceType = SpaceType.valueOf(spaceTypeParam.uppercase())
                        val body = it.body()
                        val bs = SpotChecker.getBitsetFromLongListStr(body)
                        val holes = RangeHelper.checkHolesForType(spaceType)
                        if (holes.isEmpty()) {
                            it.html("0"); return@post
                        }
                        val res = holes.filter { !bs.get(it) }.joinToString("\n")
                        it.html(res)
                    }
                    get("/{spaceType}/mask") {
                        val spaceTypeParam = it.pathParam("spaceType")
                        if (spaceTypeParam.uppercase() !in SpaceType.entries.map { it.name }) {
                            it.redirect("/holes/blog/mask")
                            return@get
                        }
                        val spaceType = SpaceType.valueOf(spaceTypeParam.uppercase())
                        val holes = RangeHelper.checkHolesForType(spaceType)
                        if (holes.isEmpty()) {
                            it.html("0"); return@get
                        }
                        val bs = BitSet(holes.max()).apply { holes.forEach { set(it) } }
                        val res = bs.toLongArray().joinToString("\n")
                        it.html(res)
                    }
                }
                get("/health") {
                    val holes = SpaceType.entries.associateWith {
                        val res = RangeHelper.checkHolesForType(it)
                        runCatching {
                            val holesMaskFile = File(System.getProperty("user.home"))
                                .resolve("source/bgm-archive-holes/${it.name.lowercase()}.txt")
                            val masked = SpotChecker.getBitsetFromLongPlaintextFile(holesMaskFile)
                            res.filter { !masked.get(it) }
                        }.getOrDefault(res)
                    }
                    val blogHealth = holes[SpaceType.BLOG]!!.isEmpty()

                    // In case we have other fields counted in isAvailable
                    @Suppress("UnnecessaryVariable", "RedundantSuppression")
                    val resIsHealthy = blogHealth
                    it.prettyJson(object {
                        val isAvailable = resIsHealthy
                        val holes = holes.filter { it.value.isNotEmpty() }
                    }, printLog = !resIsHealthy)
                }
                path("/history") {
                    get("/status") { it.redirect("/status", HttpStatus.PERMANENT_REDIRECT) }
                    path("/{spaceType}") {
                        get("/latest_topic_list", LatestTopicListWrapper)
                        get("/latest-topic-list", LatestTopicListWrapper)
                        path("/{topicId}") {
                            get(FileHistoryWrapper)
                            get("/link", LinkHandlerWrapper)
                            path("/{timestamp}") {
                                get(FileOnCommitWrapper())
                                get("/html", FileOnCommitWrapper(isHtml = true))
                            }
                        }

                    }
                }
                path("/info") {
                    before("/*") {
                        if (!it.isLocalhost()) {
                            it.status(HttpStatus.FORBIDDEN)
                            throw HttpResponseException(HttpStatus.FORBIDDEN)
                        }
                    }
                    get("/meta") {
                        it.result(
                            GitHelper.GSON.toJson(
                                Dao.bgmDao.getAllMetaData().associate { it.k to it.v }
                            )
                        )
                    }
                    path("/repo") {
                        get("/html") {
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
                    get("/cache", CacheHook)
                }
                path("/forum-enhance") {
                    post("/query", ForumEnhanceHandler)
                    get("/deleted_post/{type}/{topicId}/{postId}", FehDeletedPostHandler)
                    get("/deleted-post/{type}/{topicId}/{postId}", FehDeletedPostHandler)
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

