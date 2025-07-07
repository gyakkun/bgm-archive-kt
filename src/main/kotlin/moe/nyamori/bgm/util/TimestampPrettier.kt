package moe.nyamori.bgm.util

import java.lang.management.ManagementFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun prettyMsTs(msTs: Long): String {
    val now = System.currentTimeMillis()
    val diffNow = msTs - now
    val v = msTs - ManagementFactory.getRuntimeMXBean().startTime // orig v
    return "${Timestamp(msTs).toInstant().toUtcP0800()} , NOW${
        Duration.ofMillis(diffNow).toHumanReadable()
    } , ST${Duration.ofMillis(v).toHumanReadable()}"
}

fun Instant.toUtcP0800(): OffsetDateTime = Instant.ofEpochSecond(epochSecond).atOffset(ZoneOffset.ofHours(8))

fun Duration.toHumanReadable(): String {
    val sign = if(this.isNegative) "-" else "+"
    return sign + this.abs().toString()
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