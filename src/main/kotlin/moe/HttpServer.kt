package moe

import io.javalin.Javalin
import moe.nyamori.bgm.git.CommitToJsonProcessor

class HttpServer {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val app = Javalin.create { config ->
                // TODO
            }
                .get("/hook") { ctx ->
                    ctx.statusCode()
                    ctx.result("OK")
                    Thread { CommitToJsonProcessor.job() }
                }
                .start(5926)
            Runtime.getRuntime().addShutdownHook(Thread {
                app.stop()
            })
        }
    }
}