package moe.nyamori.bgm.util

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.text.Charsets.UTF_8

private val log = LoggerFactory.getLogger("ExternalProcessHelper")

fun Process.blockAndPrintProcessResults(
    cmd: String? = null,
    toLines: Boolean = true,
    printAtStdErr: Boolean = true
): List<String> {
    cmd?.let { log.info("Running external process: $it") }
    val result = CopyOnWriteArrayList<String?>()
    val latch = CountDownLatch(2)
    // Here actually block the process
    listOf(this.errorStream, this.inputStream).forEach { out ->
        thread {
            out.use { outUsing ->
                InputStreamReader(outUsing, UTF_8).use { isr ->
                    if (!toLines) result.add(isr.readText())
                    else
                        BufferedReader(isr).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                if (printAtStdErr) System.err.println(line)
                                result.add(line)
                            }
                        }
                }
            }
            latch.countDown()
        }
    }
    this.waitFor()
    latch.await(60, TimeUnit.SECONDS)
    return result.filterNotNull()
}