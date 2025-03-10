package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.JsonToDbProcessor
import moe.nyamori.bgm.util.HttpHelper

object DbPersistHook : Handler {
    override fun handle(ctx: Context) {
        val keyParam = ctx.queryParam("key")
        if (Config.disableDbPersist || keyParam != Config.dbPersistKey) {
            ctx.status(HttpStatus.BAD_REQUEST)
            return
        }
        val isAll = "all" == ctx.queryParam("id")
        val id = ctx.queryParam("id")?.toIntOrNull() ?: -1
        Thread {
            try {
                if (HttpHelper.tryLockDbMs(10_000)) {
                    JsonToDbProcessor.job(isAll, id)
                }
            } catch (ignore: Exception) {

            } finally {
                HttpHelper.tryUnlockDb()
            }
        }.start()
        ctx.status(HttpStatus.OK)
    }
}
