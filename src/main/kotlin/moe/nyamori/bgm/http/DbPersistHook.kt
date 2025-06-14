package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.JsonToDbProcessor
import moe.nyamori.bgm.util.HttpHelper
import org.slf4j.LoggerFactory

object DbPersistHook : Handler {
    private val LOGGER = LoggerFactory.getLogger(DbPersistHook::class.java)

    override fun handle(ctx: Context) {
        val keyParam = ctx.queryParam("key")
        if (Config.disableDbPersist || keyParam != Config.dbPersistKey) {
            ctx.status(HttpStatus.BAD_REQUEST)
            return
        }
        val qid = ctx.queryParam("id")
        val isAll = "all" == qid
        val id = qid?.toIntOrNull() ?: -1
        Thread {
            try {
                if (HttpHelper.tryLockDbMs(10_000)) {
                    JsonToDbProcessor.job(isAll, id)
                }
            } catch (ex: Exception) {
                LOGGER.error("ex when processing db persist hook: query id = {} , ex: ", qid, ex)
            } finally {
                HttpHelper.tryUnlockDb()
            }
        }.start()
        ctx.status(HttpStatus.OK)
    }
}
