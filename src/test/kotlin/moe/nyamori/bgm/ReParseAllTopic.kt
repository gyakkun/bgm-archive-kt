package moe.nyamori.bgm

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.RepoType
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.GitHelper.couplingJsonRepo
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.TopicParserEntrance
import org.eclipse.jgit.lib.Repository
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

    val jsonRepoList = mutableListOf<Repository>()
    val archiveRepoList = mutableListOf<Repository>()
    val ng = mutableSetOf<Pair<SpaceType, Int>>()

    init {
        // remember to init config first
        jsonRepoList.addAll(
            Config.repoList
                .filter { it.type == RepoType.JSON && !it.repo.isBare }
                .map { it.repo }
        )
        archiveRepoList.addAll(
            Config.repoList
                .filter { it.type == RepoType.HTML && !it.repo.isBare }
                .map { it.repo }
        )
    }

    fun walkAllArchiveFolders() {
        archiveRepoList.forEach outer@{
            val archiveRepo = it
            val archiveFolder = File(it.absolutePathWithoutDotGit())
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
                        val jsonFileLoc = File(
                            archiveRepo.couplingJsonRepo()!!.absolutePathWithoutDotGit()
                        ).resolve(jsonPath)
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