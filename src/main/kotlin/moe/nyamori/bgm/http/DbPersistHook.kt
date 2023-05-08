package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.JsonToDbProcessor
import moe.nyamori.bgm.util.HttpHelper
import java.util.concurrent.TimeUnit

object DbPersistHook : Handler {
    override fun handle(ctx: Context) {
        val keyParam = ctx.queryParam("key")
        if (Config.BGM_ARCHIVE_DISABLE_DB_PERSIST || keyParam != Config.BGM_ARCHIVE_DB_PERSIST_KEY) {
            ctx.status(400)
            return
        }
        Thread {
            try {
                if (HttpHelper.DB_WRITE_LOCK.tryLock(10, TimeUnit.SECONDS)) {
                    JsonToDbProcessor.job()
                }
            } catch (ignore: Exception) {

            } finally {
                if (HttpHelper.DB_WRITE_LOCK.isHeldByCurrentThread) {
                    HttpHelper.DB_WRITE_LOCK.unlock()
                }
            }
        }.start()
        ctx.status(200)
    }
}
