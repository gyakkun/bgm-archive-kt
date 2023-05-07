package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.util.HttpHelper.checkAndExtractSpaceTypeInContext

object LinkHandlerWrapper : Handler {
    override fun handle(ctx: Context) {
        checkAndExtractSpaceTypeInContext(ctx)
        LinkHandler.handle(ctx)
    }
}
