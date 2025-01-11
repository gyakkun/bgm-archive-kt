package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.json.JavalinJackson
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.GitHelper.folderName
import moe.nyamori.bgm.git.GitRepoHolder
import moe.nyamori.bgm.http.HumanReadable.toHumanReadableBytes
import moe.nyamori.bgm.util.blockAndPrintProcessResults
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import kotlin.math.absoluteValue
import kotlin.math.ln
import kotlin.math.pow

class GitRepoStatusHandler(
    private val gitRepoHolder: GitRepoHolder
) : Handler {
    override fun handle(ctx: Context) {
        val res = object {
            val gitRepositories = gitRepoHolder.allRepoInDisplayOrder
                .associate {
                    it.repo.folderName() to run {
                        val gitProcess = Runtime.getRuntime()
                            .exec("git --no-pager count-objects -vH", null, File(it.repo.absolutePathWithoutDotGit()))
                        gitProcess.blockAndPrintProcessResults(printAtStdErr = false).map {
                            it.split(":")
                        }.associate { (first, second) ->
                            first.trim() to second.trim()
                        }
                    }
                }
        }
        ctx.prettyJson(res)
    }
}

object HumanReadable {
    // https://intellipaat.com/community/9991/how-to-convert-byte-size-into-human-readable-format-in-java
    fun Long.toHumanReadableBytes(si: Boolean = false): String {
        val absVal = this.absoluteValue.toDouble()
        val isNegative = this < 0
        val unit = if (si) 1000 else 1024
        if (absVal < unit) return "$absVal B"
        val exp = (ln(absVal) / ln(unit.toDouble())).toInt()
        val pre = (if (si) "KMGTPE" else "KMGTPE")[exp - 1].toString() + (if (si) "" else "i")
        return (if (isNegative) "-" else "") + String.format(
            "%.1f %sB",
            absVal / unit.toDouble().pow(exp.toDouble()),
            pre
        )
    }

    fun Long.commaFormatted() = "%,d".format(this)
    fun Duration.toHumanReadable() = this.toString()
        .replace("PT", "")
        .replace("\\.\\d+".toRegex(), "")
        .replace("[a-zA-Z]".toRegex()) { it.value + " " }
        .replace("\\d+(?=H)".toRegex()) {
            it.value
                .toIntOrNull()
                ?.let {
                    val d = it / 24
                    val h = it % 24
                    "${d}D $h".takeIf { d > 0 }
                }
                ?: it.value
        }
        .removeSuffix(" ")
        .lowercase()
}


fun main() {
    System.err.println((Long.MAX_VALUE).toHumanReadableBytes())
    System.err.println((-Long.MAX_VALUE).toHumanReadableBytes())
    System.err.println((Long.MIN_VALUE).toHumanReadableBytes())
}

fun Context.prettyJson(res: Any?, printLog: Boolean = true) {
    val jm = this.jsonMapper()
    if (jm is JavalinJackson) {
        this.json(
            jm.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(res)
                .also { if (printLog) LoggerFactory.getLogger(object {}.javaClass).warn("\n" + it) })
    } else this.json(res ?: "null")
}