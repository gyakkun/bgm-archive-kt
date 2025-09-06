package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import org.slf4j.LoggerFactory
import java.util.*

open class CssHandler(
    private val path: String,
    private val resourcePath: String,
    private val revList: TreeSet<Int>,
) : Handler {
    private val LOGGER = LoggerFactory.getLogger(CssHandler::class.java)
    override fun handle(ctx: Context) {
        val revParam = ctx.queryParamMap().keys.filter { it.matches("r\\d+".toRegex()) }
        var theVer = revParam.last().substring(1).toInt()
        if (revParam.size > 1) {
            LOGGER.warn("requesting multiple revisions of css: $revParam, picking the first one ${revParam.first()}")
        }
        if (!revParam.isEmpty()) theVer = revParam.last().substring(1).toInt().let {
            if (it < revList.first) return@let revList.first
            if (it > revList.last) {
                ctx.redirect(
                    "https://bgm.tv" + (
                            ctx.queryString()
                                ?.let { qs -> "${ctx.path()}?$qs" }
                                ?: ctx.path()
                            )
                )
            }
            return@let revList.ceiling(it)
        }
        ctx.header("x-css-provider", "bgm-archive-kt")
        ctx.header("x-css-rev", "$theVer")
        LOGGER.info("requesting css rev $revParam , responding with css $theVer")
        CssHandler::class.java.getResourceAsStream("/$resourcePath/$theVer")?.let {
            ctx.writeSeekableStream(it, "text/css; charset=utf-8")
        }
    }
}

object BgmCssHandler : CssHandler("/min/g=css", "bgmcss", BGM_CSS_LIST)
object MobileCssHandler : CssHandler("/css/mobile.css", "mobilecss", MOBILE_CSS_LIST)

private val BGM_CSS_LIST = listOf(
    551,
    552,
    554,
    555,
    556,
    557,
    559,
    562,
    563,
    567,
    569,
    570,
    571,
    573,
    579,
    580,
    581,
    583,
    584,
    585,
    589,
    591,
    594,
    595,
    596,
    597,
    599,
    601,
    605,
    608,
    610,
    613,
    614,
    616,
    618,
    621,
    625,
    631,
    635,
    637,
    639,
    640,
    641,
    649,
).let { TreeSet(it) }
private val MOBILE_CSS_LIST = listOf(
    351, 473, 476, 551,554
).let { TreeSet(it) }