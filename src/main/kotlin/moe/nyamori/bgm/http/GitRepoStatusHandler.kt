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
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.*
import kotlin.math.absoluteValue

object GitRepoStatusHandler : Handler {
    override fun handle(ctx: Context) {
        val res = object {
            val gitRepositories = listOf(GitHelper.allArchiveRepoListSingleton, GitHelper.allJsonRepoListSingleton)
                .flatten()
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

    fun Int.toHumanReadableBytes(
        bound: Long = -1L,
        commaEvery3Digit: Boolean = false,
        keepDecimal: Boolean = true
    ) = this.toLong().toHumanReadableBytes(bound, commaEvery3Digit, keepDecimal)

    fun Long.toHumanReadableBytes(
        bound: Long = -1L,
        commaEvery3Digit: Boolean = false,
        keepDecimal: Boolean = true
    ) = run {
        val sign = if (this >= 0L) "" else "-"
        val theBound = if (bound < 0) {
            UnitTable.lastEntry()
        } else UnitTable.ceilingEntry(bound) ?: UnitTable.lastEntry()
        val subTable = UnitTable.subMap(1L, true, theBound.key, true)
        val abs = if (this == Long.MIN_VALUE) Long.MAX_VALUE else this.absoluteValue
        val (amount, unit) = subTable.floorEntry(abs) ?: subTable.firstEntry()
        if (this == Long.MIN_VALUE && amount == 1L) {
            return if (commaEvery3Digit)
                "${this.commaFormatted()} $unit"
            else
                "$this $unit"
        }
        if (this == Long.MIN_VALUE && amount == EiB) return "-8 EiB"
        val absBd = abs.toBigDecimal()
        val amountBd = amount.toBigDecimal()
        val numberPart = (if (commaEvery3Digit && keepDecimal)
            "$sign${absBd.divide(amountBd, 99, RoundingMode.HALF_EVEN).commaFormatted()}"
        else if (commaEvery3Digit)
            "$sign${(abs / amount).commaFormatted()}"
        else if (keepDecimal)
            "$sign${absBd.divide(amountBd, 3, RoundingMode.FLOOR)}"
        else
            "$sign${abs / amount}")
            .dropLastWhile { it == '0' }
            .dropLastWhile { it == '.' }

        "$numberPart $unit"
    }

    fun Long.commaFormatted() = "%,d".format(this)
    fun Double.commaFormatted() = "%,.3f".format(this)
    fun BigDecimal.commaFormatted() = DecimalFormat("#,###.000").format(this)
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
    System.err.println(105000000000.toHumanReadableBytes(MiB, true, keepDecimal = true))
    System.err.println(105000000000.toHumanReadableBytes(MiB, false, keepDecimal = true))
}

fun Context.prettyJson(res: Any?, printLog: Boolean = true) {
    val jm = this.jsonMapper()
    if (jm is JavalinJackson) {
        this.json(
            jm.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(res)
                .also { if (printLog) LoggerFactory.getLogger(object {}.javaClass).warn("\n" + it) })
    } else this.json(res ?: "null")
}