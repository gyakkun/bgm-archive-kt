package moe.nyamori.bgm

import com.google.gson.GsonBuilder
import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.TopicParserEntrance
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong
import kotlin.streams.asStream

class GeneralTest {
    companion object {
        val LOGGER = LoggerFactory.getLogger(GeneralTest.javaClass)
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
//            val converter: FlexmarkHtmlConverter = FlexmarkHtmlConverter.builder().build()
            val archiveFolder = File(Config.BGM_ARCHIVE_GIT_REPO_DIR)


            val ts = ConcurrentSkipListSet<Pair<Long, File>>(Comparator.comparingLong { -it.first })
            val ng = ConcurrentHashMap.newKeySet<File>()
            val ngCounter = AtomicLong()
            val totalCounter = AtomicLong()
            val _404Counter = AtomicLong()
            archiveFolder.walk().asStream().parallel().forEach allMatch@{ htmlFile ->
                if (htmlFile.isDirectory) return@allMatch
                if (htmlFile.extension != "html") return@allMatch
                totalCounter.incrementAndGet()
                var timing = System.currentTimeMillis()
                val fileName = htmlFile.nameWithoutExtension
                val fileStr = FileUtil.getFileContent(htmlFile)!!
                val topicId = fileName.toInt()
                val (topic, result) = TopicParserEntrance.parseTopic(fileStr, topicId, SpaceType.GROUP)
                if (!result) {
                    ng.add(htmlFile)
                    ngCounter.incrementAndGet()
//                    LOGGER.error("NG: ${htmlFile.absolutePath}")
                    return@allMatch
                } else {
                    if (topic == null) {
                        _404Counter.incrementAndGet()
                        return@allMatch
                    }
                    timing = System.currentTimeMillis() - timing
                    // LOGGER.info("$timing ms : ${htmlFile.absolutePath}")
                    ts.add(Pair(timing, htmlFile))
                    if (!topic!!.display!!) return@allMatch
//                    val toJson = gson.toJson(topic)
//                    val jsonFile = File(
//                        htmlFile.absolutePath.replace("bgm-archive", "bgm-archive-json")
//                            .replace(".html", ".json")
//                    )
//                    jsonFile.parentFile.mkdirs()
//                    val fileWriter =
//                        FileWriter(
//                            jsonFile
//                        )
//                    fileWriter.write(toJson)
//                    fileWriter.flush()
//                    fileWriter.close()
                    return@allMatch
                }
            }

            LOGGER.info("NG COUNT: ${ngCounter.get()}")
            LOGGER.info("404 COUNT: ${_404Counter.get()}")
            LOGGER.info("TOT COUNT: ${totalCounter.get()}")
            LOGGER.info("Top 10 time consumed: ${ts.toList().subList(0, 10)}")
            LOGGER.info("NG: ${ng.map { i -> i.nameWithoutExtension }.sorted().toList()}")


        }
    }
}