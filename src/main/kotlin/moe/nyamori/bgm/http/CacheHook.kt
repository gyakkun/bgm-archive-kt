package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.simpleName
import moe.nyamori.bgm.util.CommitHistoryCacheHelper.buildCache
import moe.nyamori.bgm.util.HttpHelper
import org.slf4j.LoggerFactory

object CacheHook : Handler {
    private val LOGGER = LoggerFactory.getLogger(CacheHook::class.java)
    override fun handle(ctx: Context) {
        val keyParam = ctx.queryParam("key")
        if (Config.disableDbPersist || keyParam != Config.dbPersistKey) {
            ctx.status(HttpStatus.BAD_REQUEST)
            return
        }
        Thread {
            try {
                if (HttpHelper.tryLockDbMs(10_000)) {
                    listOf(GitHelper.allJsonRepoListSingleton, GitHelper.allArchiveRepoListSingleton).flatten()
                        .forEach {
                            LOGGER.info("Building cache for {}", it.simpleName())
                            it.buildCache()
                        }
                }
            } catch (th: Throwable) {
                if (th is IllegalStateException) {
                    LOGGER.error("Not holding lock before building cache: ", th)
                } else {
                    LOGGER.error("Something wrong: ", th)
                }
            } finally {
                HttpHelper.tryUnlockDb()
            }
        }.start()
        ctx.status(HttpStatus.OK)


    }
}