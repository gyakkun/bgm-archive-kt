package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.CommitToJsonProcessor
import moe.nyamori.bgm.util.HttpHelper.GIT_RELATED_LOCK
import java.util.concurrent.TimeUnit

class CommitHook(
    private val commitToJsonProcessor: CommitToJsonProcessor,
) : Handler {
    override fun handle(ctx: Context) {
        if (Config.BGM_ARCHIVE_DISABLE_HOOK) {
            ctx.status(HttpStatus.BAD_REQUEST)
            return
        }
        val isAll = "all" == ctx.queryParam("idx")
        val idx = ctx.queryParam("idx")?.toIntOrNull() ?: 0
        Thread {
            try {
                if (GIT_RELATED_LOCK.tryLock(10, TimeUnit.SECONDS)) {
                    commitToJsonProcessor.job(isAll, idx)
                }
            } catch (ignore: Exception) {

            } finally {
                if (GIT_RELATED_LOCK.isHeldByCurrentThread) {
                    GIT_RELATED_LOCK.unlock()
                }
            }
        }.start()
        ctx.status(HttpStatus.OK)
    }
}