package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.IBgmDao
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.util.CommitHistoryCacheHelper.buildCache
import moe.nyamori.bgm.util.HttpHelper
import org.slf4j.LoggerFactory

class CacheHook(
    private val bgmDao: IBgmDao
) : Handler {
    private val LOGGER = LoggerFactory.getLogger(CacheHook::class.java)
    override fun handle(ctx: Context) {
        val keyParam = ctx.queryParam("key")
        if (Config.BGM_ARCHIVE_DISABLE_DB_PERSIST || keyParam != Config.BGM_ARCHIVE_DB_PERSIST_KEY) {
            ctx.status(HttpStatus.BAD_REQUEST)
            return
        }
        Thread {
            try {
                if (HttpHelper.tryLockDbMs(10_000)) {
                    listOf(GitHelper.allJsonRepoListSingleton, GitHelper.allArchiveRepoListSingleton).flatten()
                        .forEach { it.buildCache(bgmDao) }
                }
            } catch (th: Throwable) {
                if (th is IllegalStateException) {
                    LOGGER.error("Not holding lock before building cache: ", th)
                }
            } finally {
                HttpHelper.tryUnlockDb()
            }
        }.start()
        ctx.status(HttpStatus.OK)


    }
}