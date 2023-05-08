package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.CommitToJsonProcessor
import moe.nyamori.bgm.util.HttpHelper.GIT_RELATED_LOCK
import java.util.concurrent.TimeUnit

object CommitHook : Handler {
    override fun handle(ctx: Context) {
        if (Config.BGM_ARCHIVE_DISABLE_HOOK) {
            ctx.status(400)
            return
        }
        Thread {
            try {
                if (GIT_RELATED_LOCK.tryLock(10, TimeUnit.SECONDS)) {
                    CommitToJsonProcessor.job()
                }
            } catch (ignore: Exception) {

            } finally {
                if (GIT_RELATED_LOCK.isHeldByCurrentThread) {
                    GIT_RELATED_LOCK.unlock()
                }
            }
        }.start()
        ctx.status(200)
    }
}