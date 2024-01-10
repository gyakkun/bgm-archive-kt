package moe.nyamori.bgm

import io.muserver.Http2ConfigBuilder.http2Enabled
import io.muserver.Method
import io.muserver.MuServerBuilder
import io.muserver.murp.ReverseProxyBuilder
import io.muserver.murp.ReverseProxyBuilder.reverseProxy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream


fun main() {
    // `Murp response intercepter NPE`.reprod()
    `Murp response intercepter NPE`.server
}

private object `Murp response intercepter NPE` {
    private val total = AtomicInteger(0)
    private val nullCounter = AtomicInteger(0)

    fun reprod() {
        IntStream.range(0, 100).parallel().forEach { test() }
        assert(total.get() == nullCounter.get())
        assert(nullCounter.get() == 100)
        System.err.println("Total=${total.get()}, nullCounter=${nullCounter.get()}")
    }

    fun test() {
        val req =
            HttpRequest.newBuilder().uri(URI.create("http://localhost:1023/anywhere-${UUID.randomUUID()}")).build()
        val res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString())
        server.stop()
    }

    val server = MuServerBuilder.muServer()
        .withHttpPort(1023)
        .addHandler { _, res ->
            res.headers().add("Server", "muserver")
            false
        }
        .addHandler(Method.GET, "/blackhole") { req, res, pathparm ->
            res.write("You are finally here")
            res.status(200)
        }
        .addHandler(reverseProxy()
            .withUriMapper {
                URI.create("http://localhost:1023/blackhole")
            }
            .withRequestInterceptor { _, _ ->
                total.incrementAndGet()
            }
            .withResponseInterceptor { _, _, targetResponse, _ ->
                check(targetResponse != null) {
                    nullCounter.incrementAndGet()
                    "Target response is null!"
                }
            }
        )
        .withHttp2Config(http2Enabled())
        .start()
}
