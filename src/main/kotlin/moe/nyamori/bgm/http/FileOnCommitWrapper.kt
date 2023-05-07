package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.HttpHelper.checkAndExtractSpaceTypeInContext

class FileOnCommitWrapper(val isHtml: Boolean = false) : Handler {
    private val fileOnCommitJsonHandlerMap = mapOf<SpaceType, Handler>(
        SpaceType.GROUP to FileOnCommit(SpaceType.GROUP),
        SpaceType.SUBJECT to FileOnCommit(SpaceType.SUBJECT),
        SpaceType.BLOG to FileOnCommit(SpaceType.BLOG)
    )

    private val fileOnCommitHtmlHandlerMap = mapOf<SpaceType, Handler>(
        SpaceType.GROUP to FileOnCommit(SpaceType.GROUP, isHtml = true),
        SpaceType.SUBJECT to FileOnCommit(SpaceType.SUBJECT, isHtml = true),
        SpaceType.BLOG to FileOnCommit(SpaceType.BLOG, isHtml = true)
    )

    override fun handle(ctx: Context) {
        val spaceType = checkAndExtractSpaceTypeInContext(ctx)
        if (isHtml) {
            fileOnCommitHtmlHandlerMap[spaceType]!!.handle(ctx)
        } else {
            fileOnCommitJsonHandlerMap[spaceType]!!.handle(ctx)
        }
    }
}