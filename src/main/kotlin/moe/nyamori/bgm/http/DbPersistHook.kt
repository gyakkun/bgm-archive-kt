package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.JsonToDbProcessor
import moe.nyamori.bgm.util.HttpHelper

class DbPersistHook(
    private val jsonToDbProcessor: JsonToDbProcessor
) : Handler {
    override fun handle(ctx: Context) {
        val keyParam = ctx.queryParam("key")
        if (Config.BGM_ARCHIVE_DISABLE_DB_PERSIST || keyParam != Config.BGM_ARCHIVE_DB_PERSIST_KEY) {
            ctx.status(HttpStatus.BAD_REQUEST)
            return
        }
        val isAll = "all" == ctx.queryParam("idx")
        val idx = ctx.queryParam("idx")?.toIntOrNull() ?: 0
        Thread {
            try {
                if (HttpHelper.tryLockDbMs(10_000)) {
                    jsonToDbProcessor.job(isAll, idx)
                }
            } catch (ignore: Exception) {

            } finally {
                HttpHelper.tryUnlockDb()
            }
        }.start()
        ctx.status(HttpStatus.OK)
    }
}
