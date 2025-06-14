package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.CommitToJsonProcessor
import moe.nyamori.bgm.util.HttpHelper.GIT_RELATED_LOCK
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

object CommitHook : Handler {
    private val LOGGER = LoggerFactory.getLogger(CommitHook::class.java)
    override fun handle(ctx: Context) {
        if (Config.disableCommitHook) {
            ctx.status(HttpStatus.BAD_REQUEST)
            return
        }
        val qid = ctx.queryParam("id")
        val isAll = "all" == qid
        val id = qid?.toIntOrNull() ?: -1
        Thread {
            try {
                if (GIT_RELATED_LOCK.tryLock(Config.gitRelatedLockTimeoutMs, TimeUnit.MILLISECONDS)) {
                    CommitToJsonProcessor.job(isAll, id)
                }
            } catch (ex: Exception) {
                LOGGER.error("ex when processing commit: query id = {} , ex: ", qid, ex)
            } finally {
                if (GIT_RELATED_LOCK.isHeldByCurrentThread) {
                    GIT_RELATED_LOCK.unlock()
                }
            }
        }.start()
        ctx.status(HttpStatus.OK)
    }
}