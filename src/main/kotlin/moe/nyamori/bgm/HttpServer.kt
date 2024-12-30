package moe.nyamori.bgm

import com.hsbc.cranker.connector.CrankerConnectorBuilder
import com.hsbc.cranker.connector.CrankerConnectorBuilder.*
import com.hsbc.cranker.connector.RouterEventListener
import com.hsbc.cranker.connector.RouterRegistration
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.*
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_HEALTH_STATUS_500_TIMEOUT_THRESHOLD_MS
import moe.nyamori.bgm.config.ConfigDto
import moe.nyamori.bgm.config.checkAndGetConfigDto
import moe.nyamori.bgm.db.DSProvider
import moe.nyamori.bgm.db.DaoHolder
import moe.nyamori.bgm.db.JsonToDbProcessor
import moe.nyamori.bgm.git.FileHistoryLookup
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
import moe.nyamori.bgm.util.TopicJsonHelper
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

object HttpServer {
    private val LOGGER = LoggerFactory.getLogger(HttpServer::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        // bakt - bgm-archive-kt
        val baktConfig = checkAndGetConfigDto()
        Config.setConfigDtoDelegate(baktConfig)
        val dsProvider = DSProvider(
            baktConfig.jdbcUrl,
            baktConfig.jdbcUsername,
            baktConfig.jdbcPassword,
            baktConfig.hikariMinIdle,
            baktConfig.hikariMaxConn,
            baktConfig.dbIsEnableWal
        )
        val daoHolder = DaoHolder(dsProvider)
        daoHolder.runFlyway()
        writeDbPersistKeyIfNecessary(baktConfig)
        val bgmDao = daoHolder.bgmDao

        val rangeHelper = RangeHelper(bgmDao)
        val fileHistoryLookup = FileHistoryLookup(bgmDao)


        val app = Javalin.create { jc ->
            jc.useVirtualThreads = true
            jc.http.brotliAndGzipCompression()
            // Global json pretty
            // config.jsonMapper(JavalinJackson().updateMapper{
            //     it.enable(SerializationFeature.INDENT_OUTPUT)
            // })
            jc.bundledPlugins.enableCors { cors ->
                cors.addRule {
                    it.allowHost("bgm.tv", "bangumi.tv", "chii.in")
                    it.allowHost("*.bgm.tv", "*.bangumi.tv", "*.chii.in")
                }
            }
            jc.requestLogger.http { ctx, timingMs ->
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
            jc.router.apiBuilder {
                get("/status", GitRepoStatusHandler)
                get("/status/git", GitRepoStatusHandler)
                get("/status/jvm", JvmStatusHandler)
                get("/status/db", DbStatusHandler(dsProvider))
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
                        val holes = rangeHelper.checkHolesForType(spaceType);
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
                        val holes = rangeHelper.checkHolesForType(spaceType)
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
                        val holes = rangeHelper.checkHolesForType(spaceType)
                        if (holes.isEmpty()) {
                            it.html("0\n"); return@get
                        }
                        val bs = BitSet(holes.max()).apply { holes.forEach { set(it) } }
                        val res = bs.toLongArray().joinToString("\n", postfix = "\n")
                        it.html(res)
                    }
                }
                get("/health", healthHandler(rangeHelper))
                head("/health", healthHandler(rangeHelper, true))
                path("/history") {
                    get("/status", GitRepoStatusHandler)
                    path("/{spaceType}") {
                        get("/latest_topic_list", LatestTopicListWrapper)
                        get("/latest-topic-list", LatestTopicListWrapper)
                        path("/{topicId}") {
                            get(FileHistoryWrapper(fileHistoryLookup))
                            get("/link", LinkHandlerWrapper)
                            path("/{timestamp}") {
                                get(FileOnCommitWrapper(isHtml = false, fileHistoryLookup))
                                get("/html", FileOnCommitWrapper(isHtml = true, fileHistoryLookup))
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
                                daoHolder.bgmDao.getAllMetaData().associate { it.k to it.v }
                            )
                        )
                    }
                    path("/repo") {
                        get("/html") {
                            it.result(
                                GitHelper.GSON.toJson(
                                    GitHelper.allArchiveRepoListSingleton.mapIndexed { idx, repoDto ->
                                        Pair(
                                            idx,
                                            StringHashingHelper.stringHash(repoDto.repo.absolutePathWithoutDotGit())
                                        ) to repoDto.repo.absolutePathWithoutDotGit()
                                    }.toMap()
                                )
                            )
                        }
                        get("/json") {
                            it.result(
                                GitHelper.GSON.toJson(
                                    GitHelper.allJsonRepoListSingleton.mapIndexed { idx, repoDto ->
                                        Pair(
                                            idx,
                                            StringHashingHelper.stringHash(repoDto.repo.absolutePathWithoutDotGit())
                                        ) to repoDto.repo.absolutePathWithoutDotGit()
                                    }.toMap()
                                )
                            )
                        }
                    }
                }
                path("/hook") {
                    path("/db") {
                        get("/persist", DbPersistHook(JsonToDbProcessor(bgmDao, TopicJsonHelper(bgmDao))))
                        get("/reset", DbSetPersistIdHandler(bgmDao))
                        get("/purge", DbPurgeAllMetaHandler(bgmDao))
                    }

                    get("/commit", CommitHook)
                    get("/cache", CacheHook(bgmDao))
                }
                path("/forum-enhance") {
                    post("/query", ForumEnhanceHandler(bgmDao))
                    val fehDeletedPostHandler = FehDeletedPostHandler(fileHistoryLookup)
                    get("/deleted_post/{type}/{topicId}/{postId}", fehDeletedPostHandler)
                    get("/deleted-post/{type}/{topicId}/{postId}", fehDeletedPostHandler)
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
        }.start(baktConfig.httpHost, baktConfig.httpPort)

        if (baktConfig.enableCrankerConnector) {
            LOGGER.info("Starting cranker connector")
            val crankerConnector = connector()
                .withHttpClient(CrankerConnectorBuilder.createHttpClient(true).build())
                .withRoute("*") // catch all
                .withPreferredProtocols(listOf(CRANKER_PROTOCOL_3, CRANKER_PROTOCOL_1))
                .withComponentName(baktConfig.crankerComponent)
                .withRouterUris { listOf(URI.create(baktConfig.crankerRegUrl)) }
                .withSlidingWindowSize(baktConfig.crankerSlidingWin)
                .withTarget(URI.create("http://${baktConfig.httpHost}:${baktConfig.httpPort}"))
                .withRouterRegistrationListener(object : RouterEventListener {
                    override fun onRegistrationChanged(data: RouterEventListener.ChangeData) {
                        LOGGER.info(
                            "on cranker reg changed: added={} , removed={}",
                            data.added().map { it.registrationUri() },
                            data.removed().map { it.registrationUri() }
                        )
                    }

                    override fun onSocketConnectionError(router: RouterRegistration, exception: Throwable) {
                        LOGGER.error("err cranker socket conn: {} , ", router.registrationUri(), exception)
                    }
                })
                .start()
            LOGGER.info("Cranker connector started")
            Runtime.getRuntime().addShutdownHook(Thread { crankerConnector.stop(1000, TimeUnit.MILLISECONDS) })
        }

        Runtime.getRuntime().addShutdownHook(
            Thread
            {
                app.stop()
            })
    }

    private fun writeDbPersistKeyIfNecessary(baktConfig: ConfigDto) {
        baktConfig.dbPersistKey
            .also { key ->
                runCatching {
                    System.err.println("############ DB PERSIST KEY: $key ############")
                    val folder = Path(baktConfig.homeFolderAbsolutePath).resolve("bgm-archive-db").toFile()
                    if (!folder.exists()) folder.mkdirs()
                    val keyfile = folder.resolve("db-persist-key")
                    if (baktConfig.disableDbPersistKey) {
                        System.err.println("Will not write db persist keyfile due to env config.")
                        return@runCatching
                    }
                    if (!keyfile.exists()) keyfile.createNewFile()
                    FileWriter(keyfile).use { fw ->
                        fw.write(key)
                        fw.flush()
                    }
                }.onFailure {
                    System.err.println("Error when writing key file!")
                    it.printStackTrace()
                }
            }
    }


    @Volatile
    private var lastDownTimestamp: Long = Long.MAX_VALUE

    private fun healthHandler(rangeHelper: RangeHelper, isHead: Boolean = false) = Handler { ctx ->
        val holes = SpaceType.entries.associateWith {
            val res = rangeHelper.checkHolesForType(it)
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
            .map { it.repo.folderName() to it.getLatestCommitRef() }
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
        val should500 =
            !isAvailable && ((System.currentTimeMillis() - lastDownTimestamp) > BGM_HEALTH_STATUS_500_TIMEOUT_THRESHOLD_MS)
        lastDownTimestamp = if (isAvailable) Long.MAX_VALUE else Math.min(System.currentTimeMillis(), lastDownTimestamp)
        ctx.status(if (should500) 500 else 200)
        if (isHead) return@Handler
        @Suppress("unused")
        ctx.prettyJson(object {
            var isAvailable = isAvailable
            val holes = holes.filter { it.value.isNotEmpty() }
            val lastCommits = lastCommits
            val lastDownTimestamp = HttpServer.lastDownTimestamp.let {
                if (it == Long.MAX_VALUE) "long time ago" else Instant.ofEpochMilli(it).toString()
            }
        }, printLog = !isAvailable)
    }

    private fun ip(ctx: Context) = ctx.header("X-Forwarded-For")?.split(",")?.get(0) ?: ctx.ip()
    private fun Context.isLocalhost() = ip(this).let { it == "localhost" || it == "127.0.0.1" }
}

