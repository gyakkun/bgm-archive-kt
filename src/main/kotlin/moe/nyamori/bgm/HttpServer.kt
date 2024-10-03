package moe.nyamori.bgm

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.*
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.GitHelper.folderName
import moe.nyamori.bgm.git.GitHelper.getLatestCommitRef
import moe.nyamori.bgm.git.SpotChecker
import moe.nyamori.bgm.http.*
import moe.nyamori.bgm.http.HumanReadable.toHumanReadable
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.GitCommitIdHelper.timestampHint
import moe.nyamori.bgm.util.RangeHelper
import moe.nyamori.bgm.util.StringHashingHelper
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.*

object HttpServer {
    private val LOGGER = LoggerFactory.getLogger(HttpServer::class.java)
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
            config.requestLogger.http { ctx, timingMs ->
                val reqSalt: Long = ctx.attribute("reqSalt") ?: return@http
                if (timingMs >= 3000) LOGGER.error(
                    "[{}] Timing {}ms. Super long for req: {}", reqSalt, timingMs.toLong(), ctx.fullUrl()
                )
                else if (timingMs >= 1000) LOGGER.warn(
                    "[{}] Timing {}ms. Pretty long for req: {}", reqSalt, timingMs.toLong(), ctx.fullUrl()
                )
                else { /* NOP */
                }
                if (ctx.status().isError() && ctx.status() != HttpStatus.IM_A_TEAPOT) {
                    LOGGER.error("[{}] Req failed with code {}", reqSalt, ctx.statusCode())
                }
            }
            config.router.apiBuilder {
                get("/status", GitRepoStatusHandler)
                get("/status/git", GitRepoStatusHandler)
                get("/status/jvm", JvmStatusHandler)
                get("/status/db", DbStatusHandler)
                get("/holes") { it.redirect("/holes/blog") }
                path("/holes") {
                    get("/{spaceType}") { // get all holes without filtering
                        val spaceTypeParam = it.pathParam("spaceType")
                        val maxLineQueryParam = it.queryParam("maxLine")
                        val maxLine = maxLineQueryParam?.toIntOrNull() ?: 100
                        if (spaceTypeParam.uppercase() !in SpaceType.entries.map { it.name }) {
                            it.redirect("/holes/blog")
                            return@get
                        }
                        val spaceType = SpaceType.valueOf(spaceTypeParam.uppercase())
                        val holes = RangeHelper.checkHolesForType(spaceType);
                        if (holes.isEmpty()) {
                            it.html(""); return@get
                        }
                        it.html(
                            holes.take(maxLine).joinToString("\n", postfix = "\n")
                        )
                    }
                    post("/{spaceType}") { // Accept a bitset
                        val spaceTypeParam = it.pathParam("spaceType")
                        val maxLineQueryParam = it.queryParam("maxLine")
                        val maxLine = maxLineQueryParam?.toIntOrNull() ?: 100
                        if (spaceTypeParam.uppercase() !in SpaceType.entries.map { it.name }) {
                            throw HttpResponseException(HttpStatus.BAD_REQUEST)
                        }
                        val spaceType = SpaceType.valueOf(spaceTypeParam.uppercase())
                        val body = it.body()
                        val bs = SpotChecker.getBitsetFromLongListStr(body)
                        val holes = RangeHelper.checkHolesForType(spaceType)
                        if (holes.isEmpty()) {
                            it.html(""); return@post
                        }
                        val res = holes.filter { !bs.get(it) }.take(maxLine).joinToString("\n", postfix = "\n")
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
                            it.html("0\n"); return@get
                        }
                        val bs = BitSet(holes.max()).apply { holes.forEach { set(it) } }
                        val res = bs.toLongArray().joinToString("\n", postfix = "\n")
                        it.html(res)
                    }
                }
                get("/health", healthHandler())
                head("/health", healthHandler(true))
                path("/history") {
                    get("/status", GitRepoStatusHandler)
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
                    it.redirect(
                        "https://bgm.tv" + (
                                it.queryString()
                                    ?.let { qs -> "${it.path()}?$qs" }
                                    ?: it.path()
                                )
                    )
                }
                after {
                    if (it.res().contentType == ContentType.APPLICATION_JSON.mimeType) {
                        it.contentType(ContentType.APPLICATION_JSON.mimeType + "; charset=utf-8")
                    }
                }
            }
        }.beforeMatched {
            if (it.matchedPath() == "/*") return@beforeMatched
            if (it.isLocalhost()) return@beforeMatched
            val crawlers =
                listOf("bingbot", "googlebot", "yandexbot", "applebot", "duckduckbot", "spider", "company")
            if (true == it.userAgent()
                    ?.let { ua ->
                        crawlers.any { crwl -> ua.contains(crwl, true) }
                    }
            ) {
                throw ImATeapotResponse()
            }
            val reqSalt = System.currentTimeMillis() and 2047
            it.attribute("reqSalt", reqSalt)
            LOGGER.info("[{}] Req: {} {} {}", reqSalt, ip(it), it.method(), it.fullUrl())
        }.start(Config.BGM_ARCHIVE_ADDRESS, Config.BGM_ARCHIVE_PORT)

        Runtime.getRuntime().addShutdownHook(Thread
        {
            app.stop()
        })
    }

    private fun healthHandler(isHead: Boolean = false) = Handler { ctx ->
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
        var isAvailable = blogHealth
        val now = Instant.now()
        val lastCommits = GitHelper.allRepoInDisplayOrder
            .map { it.folderName() to it.getLatestCommitRef() }
            .associate { (folderName, commit) ->
                @Suppress("unused")
                folderName to object {
                    val commitMsg = commit.shortMessage.trim()

                    @Transient
                    private val _commitTime = Instant.ofEpochMilli(commit.timestampHint())
                    val commitTime = _commitTime.toString()
                    val elapsed = Duration.between(
                        _commitTime,
                        now
                    ).also {
                        if ("old" !in folderName && it.minusMinutes(15L).isPositive) {
                            isAvailable = false
                            ctx.status(500)
                        }
                    }.toHumanReadable()
                }
            }
        ctx.status(if (isAvailable) 200 else 500)
        if (isHead) return@Handler
        ctx.prettyJson(object {
            var isAvailable = isAvailable
            val holes = holes.filter { it.value.isNotEmpty() }
            val lastCommits = lastCommits
        }, printLog = !isAvailable)
    }

    private fun ip(ctx: Context) = ctx.header("X-Forwarded-For")?.split(",")?.get(0) ?: ctx.ip()
    private fun Context.isLocalhost() = ip(this).let { it == "localhost" || it == "127.0.0.1" }
}

