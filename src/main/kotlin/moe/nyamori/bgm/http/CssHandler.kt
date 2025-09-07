package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import org.slf4j.LoggerFactory
import java.util.*

open class CssHandler(
    val path: String,
    private val resourcePath: String,
    private val revList: TreeSet<Int>,
) : Handler {
    private val LOGGER = LoggerFactory.getLogger(this::class.java.name)
    override fun handle(ctx: Context) {
        val revParam = ctx.queryParamMap().keys.filter { it.matches("r\\d+".toRegex()) }
        if (revParam.isEmpty()) {
            redirectToOrigin(ctx)
            return
        }
        if (revParam.size > 1) {
            LOGGER.warn("requesting multiple revisions of css: $revParam, picking the first one ${revParam.first()}")
        }
        val readout = revParam.first().substring(1).toInt()
        val selected = readout.let {
            if (it < revList.first) return@let revList.first
            if (it > revList.last) {
                redirectToOrigin(ctx)
                return
            }
            return@let revList.ceiling(it)
        }
        ctx.header("x-css-proxy", "bgm-archive-kt")
        ctx.header("x-css-handler", "${this::class.java.name}")
        ctx.header("x-css-request-rev", "$readout")
        ctx.header("x-css-selected-rev", "$selected")
        LOGGER.info("requesting css rev $readout , responding with css $selected")
        CssHandler::class.java.getResourceAsStream("$resourcePath/$selected")?.let {
            ctx.writeSeekableStream(it, "text/css; charset=utf-8")
        }
    }

    private fun redirectToOrigin(ctx: Context) {
        ctx.redirect(
            "https://bgm.tv" + (
                    ctx.queryString()
                        ?.let { qs -> "${ctx.path()}?$qs" }
                        ?: ctx.path()
                    )
        )
    }
}

object MinGCssHandler : CssHandler("/min/g=css", "/css/min_g", MIN_G_CSS_LIST)
object CssMobileHandler : CssHandler("/css/mobile.css", "/css/css_mobile", CSS_MOBILE_LIST)
object CssDistBangumiMinHandler : CssHandler(
    "/css/dist/bangumi.min.css",
    "/css/bangumi_dist_min",
    CSS_BANGUMI_DIST_LIST
)

private val MIN_G_CSS_LIST = listOf(
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
).let { TreeSet(it) }
private val CSS_MOBILE_LIST = listOf(
    351, 473, 476, 551, 554
).let { TreeSet(it) }
private val CSS_BANGUMI_DIST_LIST = listOf(
    646, 648, 649, 650
).let { TreeSet(it) }