package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.HttpHelper.checkAndExtractSpaceTypeInContext

object FileHistoryWrapper : Handler {

    private val fileHistoryJsonHandlerMap = SpaceType.entries.associateWith { FileHistory(it) }

    override fun handle(ctx: Context) {
        val spaceType = checkAndExtractSpaceTypeInContext(ctx)
        fileHistoryJsonHandlerMap[spaceType]!!.handle(ctx)
    }
}
