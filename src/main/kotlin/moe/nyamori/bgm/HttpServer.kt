package moe.nyamori.bgm

import io.javalin.Javalin
import io.javalin.http.Context
import moe.nyamori.bgm.git.CommitToJsonProcessor
import java.lang.Exception
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class HttpServer {
    companion object {
        private val lock = ReentrantLock()

        @JvmStatic
        fun main(args: Array<String>) {

            val app = Javalin.create { config ->
                config.compression.brotliAndGzip()
                /*config.plugins.enableCors { cors ->

                }*/
            }
                .get("/after-commit-hook", commitHookHandler)
                .start(5926)
            Runtime.getRuntime().addShutdownHook(Thread {
                app.stop()
            })
        }

        private val commitHookHandler: (ctx: Context) -> Unit = { ctx ->
            Thread {
                try {
                    if (lock.tryLock(10, TimeUnit.SECONDS)) {
                        CommitToJsonProcessor.job()
                    }
                } catch (ex: Exception) {

                } finally {
                    if (lock.isHeldByCurrentThread) {
                        lock.unlock()
                    }
                }
            }.start()
            ctx.result("OK")
        }
    }
}