package moe.nyamori.bgm

import com.google.gson.GsonBuilder
import com.hsbc.cranker.connector.CrankerConnectorBuilder
import com.hsbc.cranker.connector.CrankerConnectorBuilder.*
import com.hsbc.cranker.connector.RouterEventListener
import com.hsbc.cranker.connector.RouterRegistration
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.*
import moe.nyamori.bgm.config.*
import moe.nyamori.bgm.config.Config.bgmHealthStatus500TimeoutThresholdMs
import moe.nyamori.bgm.db.DSProvider
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.GitHelper.folderName
import moe.nyamori.bgm.git.GitHelper.getLatestCommitRef
import moe.nyamori.bgm.git.SpotChecker
import moe.nyamori.bgm.http.*
import moe.nyamori.bgm.util.toHumanReadable
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.GitCommitIdHelper.timestampHint
import moe.nyamori.bgm.util.RangeHelper
import moe.nyamori.bgm.util.getSelfVersion
import moe.nyamori.bgm.util.prettyMsTs
import org.eclipse.jetty.http.HttpGenerator
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.lang.management.ManagementFactory
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

object HttpServer {
    private val LOGGER = LoggerFactory.getLogger(HttpServer::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        // env var E_BGM_ARCHIVE_CONFIG_PATH
        // or
        // sys prop config.path
        val cfg = checkAndGetConfigDto()
        setConfigDelegate(cfg)
        writeDbPersistKeyIfNecessary(cfg)
        writeConfigToConfigFolder(cfg)
        customizeHttpMsg()
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
                            it.html("\n"); return@get
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
                            it.html("\n"); return@post
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
                                            repo.toRepoDtoOrThrow().id
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
                                            repo.toRepoDtoOrThrow().id
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
        }.start(Config.httpHost, Config.httpPort)

        if (Config.enableCrankerConnector) {
            LOGGER.info("Starting cranker connector")
            val crankerConnector = connector()
                .withHttpClient(CrankerConnectorBuilder.createHttpClient(true).build())
                .withRoute("*") // catch all
                .withPreferredProtocols(listOf(CRANKER_PROTOCOL_3, CRANKER_PROTOCOL_1))
                .withComponentName(Config.crankerComponent)
                .withRouterUris { listOf(URI.create(Config.crankerRegUrl)) }
                .withSlidingWindowSize(Config.crankerSlidingWin)
                .withTarget(URI.create("http://${Config.httpHost}:${Config.httpPort}"))
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

    private fun writeConfigToConfigFolder(cfg: ConfigDto) {
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val cfgJson = gson.toJson(cfg)
        val folder = File(cfg.homeFolderAbsolutePath)
            .resolve("bgm-archive-kt-config")
            .apply { mkdirs() }
        LOGGER.info("Final loaded config: {}", cfgJson)
        val cfgJsonFile = folder.resolve("config.output.json")
        if (cfgJsonFile.exists()) {
            LOGGER.error("config file exists: {}", cfgJsonFile.path)
            LOGGER.error("orig config file content: {}", cfgJsonFile.readText())
            LOGGER.error("will overwrite the config file.")
        }
        FileWriter(cfgJsonFile).use {
            it.write(cfgJson)
            it.flush()
        }
    }

    private fun writeDbPersistKeyIfNecessary(cfg: IConfig) {
        runCatching {
            System.err.println("############ DB PERSIST KEY: ${cfg.dbPersistKey} ############")
            val dbFolder = if (DSProvider.isSqlite) {
                File(DSProvider.sqliteFilePathOrNull!!).parentFile
            } else File(cfg.homeFolderAbsolutePath).resolve("bgm-archive-db")
            val keyfile = dbFolder.resolve("db-persist-key")
            if (cfg.disableDbPersistKey) {
                System.err.println("Will not write db persist keyfile due to env config.")
                return@runCatching
            }
            if (!keyfile.exists()) keyfile.createNewFile()
            FileWriter(keyfile).use { fw ->
                fw.write(cfg.dbPersistKey)
                fw.flush()
            }
        }.onFailure {
            System.err.println("Error when writing key file!")
            it.printStackTrace()
        }
    }

    @Volatile
    private var lastDownTimestamp: Long = Long.MAX_VALUE

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
        val syncHealth = java.util.concurrent.atomic.AtomicBoolean(true)
        val now = Instant.now()
        val lastCommits = GitHelper.allRepoInDisplayOrder
            .map { it to it.getLatestCommitRef() }
            .associate { (repo, commit) ->
                val folderName = repo.folderName()
                val dto = repo.toRepoDtoOrThrow()
                @Suppress("unused")
                folderName to object {
                    val commitMsg = commit.shortMessage.trim()

                    @Transient
                    private val _msTsHint = commit.timestampHint()
                    val commitTime = prettyMsTs(_msTsHint)
                    val elapsed = Duration.between(
                        Instant.ofEpochMilli(_msTsHint),
                        now
                    ).also {
                        if (("old" !in folderName) && !dto.isStatic
                            && it.minusMinutes(15L).isPositive
                        ) {
                            syncHealth.set(false)
                        }
                    }.toHumanReadable()
                }
            }
        val redForSoLong = ((System.currentTimeMillis() - lastDownTimestamp) > bgmHealthStatus500TimeoutThresholdMs)
        val shouldRedForBlogAndExceedingThreshold = !blogHealth && redForSoLong
        val isAvailable = blogHealth && syncHealth.get()
        lastDownTimestamp = if (isAvailable) Long.MAX_VALUE else Math.min(System.currentTimeMillis(), lastDownTimestamp)
        // 501 to "SYNC TIMEOUT",
        // 508 to "BLOG HOLES",
        // 510 to "OTHER HOLES",
        if (!syncHealth.get()) { // immediate
            ctx.status(501)
        } else if (shouldRedForBlogAndExceedingThreshold) {
            ctx.status(508)
        } else {
            ctx.status(200)
        }
        if (isHead) return@Handler
        @Suppress("unused")
        ctx.prettyJson(object {
            var isAvailable = isAvailable
            val version = getSelfVersion()
            val startTime = prettyMsTs(ManagementFactory.getRuntimeMXBean().startTime)
            val holes = holes.filter { it.value.isNotEmpty() }
            val lastCommits = lastCommits
            val lastDownTimestamp = HttpServer.lastDownTimestamp.let {
                if (it == Long.MAX_VALUE) "long time ago" else prettyMsTs(it)
            }
        }, printLog = !isAvailable)
    }

    private fun ip(ctx: Context) = ctx.header("X-Forwarded-For")?.split(",")?.get(0) ?: ctx.ip()
    private fun Context.isLocalhost() = ip(this).let {
        it == "localhost"
                || it == "127.0.0.1"
                || it == "[0:0:0:0:0:0:0:1]"
                || it == "0:0:0:0:0:0:0:1"
    }


    private fun customizeHttpMsg() = runCatching {
        val m = mapOf(
            501 to "SYNC TIMEOUT",
            508 to "BLOG HOLES",
            510 to "OTHER HOLES",
        )
        val ppf = HttpGenerator::class.java.getDeclaredField("__preprepared").apply { isAccessible = true }
        val pp = ppf.get(null) as Array<*>
        m.forEach { (code, msg) ->
            val res = pp[code]!!
            val rlf = res.javaClass.getDeclaredField("_responseLine").apply { isAccessible = true }
            val rsnf = res.javaClass.getDeclaredField("_reason").apply { isAccessible = true }
            val rsn = msg.toByteArray()
            val rl = "HTTP/1.1 $code $msg\r\n".toByteArray()
            rlf.set(res, rl)
            rsnf.set(res, rsn)
        }
    }.onFailure {
        LOGGER.error("Error while customizing http message: ", it)
    }
}

