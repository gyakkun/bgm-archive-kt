package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.HttpHelper.checkAndExtractSpaceTypeInContext

class FileOnCommitWrapper(val isHtml: Boolean = false) : Handler {
    private val fileOnCommitJsonHandlerMap =
        SpaceType.entries.associateWith { FileOnCommit(it) }

    private val fileOnCommitHtmlHandlerMap =
        SpaceType.entries.associateWith { FileOnCommit(it, isHtml = true) }

    override fun handle(ctx: Context) {
        val spaceType = checkAndExtractSpaceTypeInContext(ctx)
        if (isHtml) {
            fileOnCommitHtmlHandlerMap[spaceType]!!.handle(ctx)
        } else {
            fileOnCommitJsonHandlerMap[spaceType]!!.handle(ctx)
        }
    }
}