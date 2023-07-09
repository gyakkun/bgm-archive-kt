package moe.nyamori.bgm

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.TopicParserEntrance
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlin.streams.asStream

object ReParseAllTopic {
    private val LOGGER = LoggerFactory.getLogger(ReParseAllTopic::class.java)

    @JvmStatic
    fun main(argv: Array<String>) {
        walkAllArchiveFolders()
    }

    val jsonRepoFolders = ArrayList<String>()
    val archiveRepoFolders = ArrayList<String>()
    val ng = mutableSetOf<Pair<SpaceType, Int>>()

    init {
        Config.BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST.split(",")
            .map {
                if (it.isNotBlank()) jsonRepoFolders.add(it.trim())
            }
        Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR.let { jsonRepoFolders.add(it) }

        Config.BGM_ARCHIVE_GIT_STATIC_REPO_DIR_LIST.split(",")
            .map {
                if (it.isNotBlank()) archiveRepoFolders.add(it.trim())
            }
        Config.BGM_ARCHIVE_GIT_REPO_DIR.let { archiveRepoFolders.add(it) }
    }

    fun walkAllArchiveFolders() {
        archiveRepoFolders.forEach outer@{
            val archiveFolder = File(it)
            if (!archiveFolder.exists()) return@outer
            archiveFolder.walkBottomUp().asStream()/*.parallel()*/.forEach inner@{ file ->
                runCatching {
                    if (file.isDirectory) return@inner
                    if (file.extension != "html") return@inner
                    if (file.absolutePath.hashCode() and 255 == 255) {
                        LOGGER.info("Processing ${file.absolutePath}")
                    }
                    val spaceType = file.absolutePath.let { filepath ->
                        if (filepath.contains("group")) SpaceType.GROUP
                        else if (filepath.contains("subject")) SpaceType.SUBJECT
                        else if (filepath.contains("blog")) SpaceType.BLOG
                        else throw IllegalStateException("No space type matched!")
                    }
                    val topicId = file.nameWithoutExtension.toInt()
                    ng.add(Pair(spaceType, topicId))
                    val (topic, success) = TopicParserEntrance.parseTopic(
                        file.readText(Charsets.UTF_8),
                        topicId,
                        spaceType
                    )
                    if (success) {
                        ng.remove(Pair(spaceType, topicId))
                        val jsonPath = file.absolutePath
                            .replace("bgm-archive", "bgm-archive-json")
                            .replace("html", "json")
                        val jsonFileLoc = File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR).resolve(jsonPath)
                        val json = GitHelper.GSON.toJson(topic)
                        jsonFileLoc.parentFile.mkdirs()
                        FileOutputStream(jsonFileLoc).use { fos ->
                            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { osw ->
                                BufferedWriter(osw).use { bw ->
                                    bw.write(json)
                                    bw.flush()
                                }
                            }
                        }
                    }
                }.onFailure { ex ->
                    LOGGER.error("Ex when handling ${file.absolutePath} ", ex)
                }
            }
        }
        LOGGER.error("NG LIST: $ng")
    }
}