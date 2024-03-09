package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.json.JavalinJackson
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.GitHelper.folderName
import moe.nyamori.bgm.http.HumanReadable.KiB
import moe.nyamori.bgm.http.HumanReadable.MiB
import moe.nyamori.bgm.http.HumanReadable.PiB
import moe.nyamori.bgm.http.HumanReadable.toHumanReadableBytes
import moe.nyamori.bgm.util.blockAndPrintProcessResults
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.*
import kotlin.math.absoluteValue

object GitRepoStatusHandler : Handler {
    override fun handle(ctx: Context) {
        val res = object {
            val gitRepositories = GitHelper.allRepoInDisplayOrder
                .associate {
                    it.folderName() to run {
                        val gitProcess = Runtime.getRuntime()
                            .exec("git --no-pager count-objects -vH", null, File(it.absolutePathWithoutDotGit()))
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
    const val KiB = 1L shl 10
    const val MiB = 1L shl 20
    const val GiB = 1L shl 30
    const val TiB = 1L shl 40
    const val PiB = 1L shl 50
    const val EiB = 1L shl 60
    val UnitTable = TreeMap(
        mapOf(
            1L to "Bytes",
            KiB to "KiB",
            MiB to "MiB",
            GiB to "GiB",
            TiB to "TiB",
            PiB to "PiB",
            EiB to "EiB"
        )
    )

    fun Int.toHumanReadableBytes(bound: Long = -1L, commaEvery3Digit: Boolean = false) =
        this.toLong().toHumanReadableBytes(bound, commaEvery3Digit)

    fun Long.toHumanReadableBytes1() =
        if (this >= 1024L * 1024 * 1024 * 1024 /*TiB*/) "${this / 1024 / 1024 / 1024 / 1024} TiB"
        else if (this >= 1024L * 1024 * 1024/*GiB*/) "${this / 1024 / 1024} GiB"
        else if (this >= 1024L * 1024/*MiB*/) "${this / 1024 / 1024} MiB"
        else if (this >= 1024L/*KiB*/) "${this / 1024} KiB"
        else "$this Bytes"

    fun Long.toHumanReadableBytes(bound: Long = -1L, commaEvery3Digit: Boolean = false) = run {
        val sign = if (this >= 0L) "" else "-"
        val theBound = if (bound < 0) {
            UnitTable.lastEntry()
        } else UnitTable.ceilingEntry(bound) ?: UnitTable.lastEntry()
        val subTable = UnitTable.subMap(1L, true, theBound.key, true)
        val abs = if (this == Long.MIN_VALUE) Long.MAX_VALUE else this.absoluteValue
        val entry = subTable.floorEntry(abs) ?: subTable.firstEntry()
        if (this == Long.MIN_VALUE && entry.key == 1L) {
            return if (commaEvery3Digit)
                "${this.commaFormatted()} ${entry.value}"
            else
                "$this ${entry.value}"
        }
        if (this == Long.MIN_VALUE && entry.key == EiB) return "-8 EiB"
        val divider = entry.key
        val unit = entry.value
        if (commaEvery3Digit)
            "$sign${(abs / divider).commaFormatted()} $unit"
        else
            "$sign${abs / divider} $unit"
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
    System.err.println(0.toHumanReadableBytes())
    System.err.println((KiB - 1).toHumanReadableBytes())
    System.err.println(KiB.toHumanReadableBytes())
    System.err.println((KiB + 1).toHumanReadableBytes())
    System.err.println((MiB - 1).toHumanReadableBytes())
    System.err.println((MiB).toHumanReadableBytes())
    System.err.println((MiB + 1).toHumanReadableBytes())
    System.err.println((PiB - 1).toHumanReadableBytes())
    System.err.println((PiB).toHumanReadableBytes())
    System.err.println((PiB + PiB).toHumanReadableBytes())
    System.err.println((Long.MAX_VALUE).toHumanReadableBytes())
    System.err.println((-Long.MAX_VALUE).toHumanReadableBytes())
    System.err.println((Long.MIN_VALUE).toHumanReadableBytes())
    System.err.println((Long.MIN_VALUE).toHumanReadableBytes(KiB, true))
    System.err.println((Long.MIN_VALUE).toHumanReadableBytes(1, commaEvery3Digit = true))
    System.err.println(105000000000.toHumanReadableBytes(MiB, true))
}

fun Context.prettyJson(res: Any?, printLog: Boolean = true) {
    val jm = this.jsonMapper()
    if (jm is JavalinJackson) {
        this.json(
            jm.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(res)
                .also { if (printLog) LoggerFactory.getLogger(object {}.javaClass).warn("\n" + it) })
    } else this.json(res ?: "null")
}