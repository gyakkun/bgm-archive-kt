package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.util.HttpHelper
import org.slf4j.LoggerFactory

object DbSetPersistIdHandler : Handler {
    val LOGGER = LoggerFactory.getLogger(DbSetPersistIdHandler.javaClass)
    override fun handle(ctx: Context) {
        val keyParam = ctx.queryParam("key")
        val commitId = ctx.queryParam("id")
        if (Config.disableDbPersist || keyParam != Config.dbPersistKey || commitId == null) {
            ctx.status(HttpStatus.BAD_REQUEST)
            return
        }
        val idx = ctx.queryParam("idx")?.toIntOrNull() ?: 0
        if (idx !in GitHelper.allJsonRepoListSingleton.indices) {
            ctx.status(HttpStatus.BAD_REQUEST)
            return
        }
        val repo = GitHelper.allJsonRepoListSingleton[idx]
        Thread {
            try {
                if (HttpHelper.tryLockDbMs(10_000)) {
                    val result = Dao.bgmDao.updatePrevPersistedCommitId(repo, commitId)
                    LOGGER.info("Set persist id $commitId result: $result")
                }
            } catch (ignore: Exception) {

            } finally {
                HttpHelper.tryUnlockDb()
            }
        }.start()
        ctx.status(HttpStatus.OK)
    }
}
