package moe.nyamori.bgm.util

import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets.UTF_8

fun Process.blockAndPrintProcessResults(
    toLines: Boolean = true,
    printAtStdErr: Boolean = true
): List<String> {
    val result = CopyOnWriteArrayList<String?>()
    val latch = CountDownLatch(2)
    // Here actually block the process
    val stderr = this.errorStream
    val stdout = this.inputStream
    listOf(stderr, stdout).map { out ->
        out.use { outUsing ->
            InputStreamReader(outUsing, UTF_8).use { isr ->
                if (!toLines) result.add(isr.readText())
                else result.addAll(isr.readText().lines())

                if(printAtStdErr) System.err.println(result)
            }
        }
        latch.countDown()
    }
    latch.await(2,TimeUnit.MINUTES)
    this.waitFor()
    return result.filterNotNull()
}