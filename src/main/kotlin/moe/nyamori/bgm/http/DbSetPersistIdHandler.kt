package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.db.JsonToDbProcessor
import moe.nyamori.bgm.util.HttpHelper
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

object DbSetPersistIdHandler : Handler {
    val LOGGER = LoggerFactory.getLogger(DbSetPersistIdHandler.javaClass)
    override fun handle(ctx: Context) {
        val keyParam = ctx.queryParam("key")
        val commitId = ctx.queryParam("id")
        if (Config.BGM_ARCHIVE_DISABLE_DB_PERSIST || keyParam != Config.BGM_ARCHIVE_DB_PERSIST_KEY || commitId == null) {
            ctx.status(400)
            return
        }
        Thread {
            try {
                if (HttpHelper.DB_WRITE_LOCK.tryLock(10, TimeUnit.SECONDS)) {
                    val result = Dao.bgmDao().updatePrevPersistedCommitId(commitId)
                    LOGGER.info("Set persist id $commitId result: $result")
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
