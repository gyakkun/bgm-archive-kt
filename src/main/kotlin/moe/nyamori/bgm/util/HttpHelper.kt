package moe.nyamori.bgm.util

import io.javalin.http.Context
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.model.SpaceType
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

@Suppress("NOTHING_TO_INLINE")
object HttpHelper {
    val GIT_RELATED_LOCK = ReentrantLock()
    val DB_WRITE_LOCK = ReentrantLock()
    val DB_READ_SEMAPHORE = Semaphore(5)
    fun checkAndExtractSpaceTypeInContext(ctx: Context): SpaceType {
        val spaceTypeFromPath = ctx.pathParam("spaceType")
        if (SpaceType.entries.toTypedArray().none {
                it.name.equals(spaceTypeFromPath, ignoreCase = true)
            }
        ) {
            throw IllegalArgumentException("$spaceTypeFromPath is not a supported topic type")
        }
        return SpaceType.valueOf(spaceTypeFromPath.uppercase())
    }

    inline fun tryLockDbMs(milliseconds: Long, isRead: Boolean = false): Boolean {
        if (Config.BGM_ARCHIVE_DB_IS_ENABLE_WAL && isRead) return true
        return DB_WRITE_LOCK.tryLock(milliseconds, TimeUnit.MILLISECONDS)
    }

    inline fun tryUnlockDb(): Unit {
//        if (Config.BGM_ARCHIVE_DB_IS_ENABLE_WAL && !DB_WRITE_LOCK.isHeldByCurrentThread) return
//        if (DB_WRITE_LOCK.isHeldByCurrentThread) return DB_WRITE_LOCK.unlock()
        if (DB_WRITE_LOCK.isHeldByCurrentThread) {
            DB_WRITE_LOCK.unlock()
        }
    }
}