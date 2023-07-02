package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.HttpHelper.checkAndExtractSpaceTypeInContext

object FileHistoryWrapper : Handler {

    private val fileHistoryJsonHandlerMap = mapOf<SpaceType, Handler>(
        SpaceType.GROUP to FileHistory(SpaceType.GROUP),
        SpaceType.SUBJECT to FileHistory(SpaceType.SUBJECT),
        SpaceType.BLOG to FileHistory(SpaceType.BLOG),
        SpaceType.EP to FileHistory(SpaceType.EP),
        SpaceType.PERSON to FileHistory(SpaceType.PERSON),
        SpaceType.CHARACTER to FileHistory(SpaceType.CHARACTER),
    )

    override fun handle(ctx: Context) {
        val spaceType = checkAndExtractSpaceTypeInContext(ctx)
        fileHistoryJsonHandlerMap[spaceType]!!.handle(ctx)
    }
}
