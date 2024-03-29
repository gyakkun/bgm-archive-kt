package moe.nyamori.bgm.util

import io.javalin.http.Context
import moe.nyamori.bgm.model.SpaceType
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock

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
}