package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.HttpHelper.checkAndExtractSpaceTypeInContext

class FileHistoryWrapper(
    filehistoryLookup: FileHistoryLookup,
) : Handler {

    private val fileHistoryJsonHandlerMap = SpaceType.entries.associateWith { FileHistory(it, filehistoryLookup) }

    override fun handle(ctx: Context) {
        val spaceType = checkAndExtractSpaceTypeInContext(ctx)
        fileHistoryJsonHandlerMap[spaceType]!!.handle(ctx)
    }
}
