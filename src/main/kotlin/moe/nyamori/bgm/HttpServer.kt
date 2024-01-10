package moe.nyamori.bgm

import com.hsbc.cranker.connector.CrankerConnectorBuilder
import com.hsbc.cranker.connector.CrankerConnectorBuilder.createHttpClient
import com.hsbc.cranker.connector.ProxyEventListener
import com.hsbc.cranker.connector.RouterEventListener
import com.hsbc.cranker.connector.RouterRegistration
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.muserver.MuServerBuilder.muServer
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.http.*
import moe.nyamori.bgm.util.StringHashingHelper
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.http.HttpRequest
import java.util.concurrent.TimeUnit

object HttpServer {
    private val LOGGER = LoggerFactory.getLogger(HttpServer::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val app = Javalin.create { config ->
            config.compression.brotliAndGzip()
            config.plugins.enableCors { cors ->
                cors.add {
                    it.allowHost("bgm.tv", "bangumi.tv", "chii.in")
                    it.allowHost("*.bgm.tv", "*.bangumi.tv", "*.chii.in")
                }
            }
        }
            .routes {
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
            }
            .after {
                if (it.res().contentType == ContentType.APPLICATION_JSON.mimeType) {
                    it.contentType(ContentType.APPLICATION_JSON.mimeType + "; charset=utf-8")
                }
            }
         .start(Config.BGM_ARCHIVE_ADDRESS, Config.BGM_ARCHIVE_PORT)
//        val muserver = muServer()
//            .addHandler { req, res ->
//                res.write("<h1>hi there</h1>")
//                false
//            }
//            .withInterface(Config.BGM_ARCHIVE_ADDRESS)
//            .withHttpPort(Config.BGM_ARCHIVE_PORT)
//            .start()
        val cli = createHttpClient(false)
            // .proxy(object : ProxySelector() {
            //     override fun select(uri: URI): List<Proxy> {
            //         return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 1183)))
            //     }
//
            //     override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
            //         //
            //     }
            // })
            .build()
        val crankerConnector = CrankerConnectorBuilder.connector()
            .withRoute("bgm")
            .withTarget(URI.create("http://${Config.BGM_ARCHIVE_ADDRESS}:${Config.BGM_ARCHIVE_PORT}"))
            // .withRouterLookupByDNS(URI.create("wss://crr.nyamori.moe"))
            .withRouterUris { listOf(URI.create("wss://crr.nyamori.moe")) }
            .withHttpClient(cli)
            .withComponentName("bgm-archive-kt")
            .withSlidingWindowSize(8)
            .withRouterRegistrationListener(object : RouterEventListener {
                override fun onRegistrationChanged(data: RouterEventListener.ChangeData?) {
                    LOGGER.warn("[onRegistrationChanged] {}", data)
                }

                override fun onRouterDnsLookupError(error: Throwable?) {
                    LOGGER.warn("[onRouterDnsLookupError] ", error)
                }

                override fun onSocketConnectionError(router: RouterRegistration?, exception: Throwable?) {
                    LOGGER.warn("[onSocketConnectionError] {}", router, exception)
                }
            })
             .withProxyEventListener(object : ProxyEventListener {
                 override fun beforeProxyToTarget(
                     request: HttpRequest,
                     requestBuilder: HttpRequest.Builder
                 ): HttpRequest {
                     return requestBuilder.uri(
                         URI.create(
                             request.uri().toString().replaceFirst("/bgm", "", true)
                                 .also { LOGGER.info("newuri {}", it) }
                         )
                     ).build()
                 }
             })
            .start()

        Runtime.getRuntime().addShutdownHook(Thread
        {
            crankerConnector.stop(1000, TimeUnit.MILLISECONDS)
            app.stop()
        })
    }

    private fun ip(ctx: Context) = ctx.header("X-Forwarded-For")?.split(",")?.get(0) ?: ctx.ip()
    private fun Context.isLocalhost() = ip(this).let { it == "localhost" || it == "127.0.0.1" }
}

