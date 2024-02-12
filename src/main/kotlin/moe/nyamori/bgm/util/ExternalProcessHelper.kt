package moe.nyamori.bgm.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

fun Process.blockAndPrintProcessResults(
    toLines: Boolean = true,
    printAtStdErr: Boolean = true
): List<String> {
    val result = CopyOnWriteArrayList<String?>()
    // val latch = CountDownLatch(2)
    // Here actually block the process
    val stderr = this.errorStream
    val stdout = this.inputStream
    runBlocking {
        listOf(stderr, stdout).map { out ->
            async {
                out.use { outUsing ->
                    InputStreamReader(outUsing).use { isr ->
                        if (!toLines) result.add(isr.readText())
                        else
                            BufferedReader(isr).use { reader ->
                                var line: String?
                                // reader.readLine()
                                while (reader.readLine().also { line = it } != null) {
                                    if (printAtStdErr) System.err.println(line)
                                    result.add(line)
                                }
                            }
                    }
                }
                // latch.countDown()
            }
        }.awaitAll()
        // latch.await()
    }
    this.waitFor()
    return result.filterNotNull()
}