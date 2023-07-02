package moe.nyamori.bgm.git

import io.javalin.http.sse.NEW_LINE
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
import moe.nyamori.bgm.git.GitHelper.findChangedFilePaths
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.TopicParserEntrance
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*
import kotlin.collections.ArrayList


object CommitToJsonProcessor {
    private val log = LoggerFactory.getLogger(CommitToJsonProcessor.javaClass)
    private const val NO_GOOD_FILE_NAME = "ng.txt"


    @JvmStatic
    fun main(arg: Array<String>) {
        job()
    }

    fun job() {
        val walk =
            GitHelper.getWalkBetweenPrevProcessedArchiveCommitAndLatestArchiveCommitInReverseOrder()
        val archiveRepo = GitHelper.archiveRepoSingleton
        val jsonRepo = GitHelper.jsonRepoSingleton
        var prev = walk.next() // used in the iteration (now = next() ... prev = now)
        val prevProcessedArchiveCommitRef = GitHelper.getPrevProcessedArchiveCommitRef()

        // FIXME: Workaround the inclusive walk boundary
        while (prev != null) {
            if (prevProcessedArchiveCommitRef == prev) {
                break
            }
            prev = walk.next()
        }

        log.info("The previously processed commit: $prev")
        run breakable@{
            walk.forEachIndexed { commitIdx, curCommit ->
                try {
                    val noGoodIdTreeSet = TreeSet<Int>()
                    if (curCommit == prev) {
                        log.error("The first commit being iterated twice!")
                        return@breakable
                    }

                    if (curCommit.fullMessage.startsWith("META") || curCommit.fullMessage.startsWith("init")) {
                        log.warn("Iterating meta or init commit: $curCommit")
                        return@forEachIndexed // continue
                    }

                    val commitMsgSplitArr = curCommit.fullMessage.split(" ")
                    val commitSpaceType = if (commitMsgSplitArr.isNotEmpty()) {
                        when (commitMsgSplitArr[0]) {
                            in SpaceType.values().map { it.toString().uppercase() } -> SpaceType.valueOf(
                                commitMsgSplitArr[0]
                            )

                            else -> {
                                throw IllegalStateException("Not a group or subject topic!")
                            }
                        }
                    } else {
                        throw IllegalStateException("Not a group or subject topic!")
                    }

                    val changedFilePathList = archiveRepo.findChangedFilePaths(prev, curCommit)

                    for (pathIdx in changedFilePathList.indices) {
                        val path = changedFilePathList[pathIdx]
                        try {
                            if (!path.endsWith("html")) continue
                            log.warn(
                                "Cur commit id: ${
                                    curCommit.name().substring(0, 8)
                                }, path: $path , message:  ${curCommit.shortMessage}"
                            )
                            val pathSplitArr = path.split("/")
                            val htmlSpaceType = if (pathSplitArr.isNotEmpty()) {
                                when (pathSplitArr[0]) {
                                    in SpaceType.values().map { it.toString().lowercase() } ->
                                        SpaceType.valueOf(
                                            pathSplitArr.first().uppercase()
                                        )

                                    else -> {
                                        throw IllegalStateException("unknown path prefix: ${pathSplitArr[0]}")
                                    }
                                }
                            } else {
                                throw IllegalStateException("unknown path format: $path")
                            }

                            if (htmlSpaceType != commitSpaceType) {
                                log.error("Html space type not consistent with commit space type! htmlSpaceType=$htmlSpaceType, commitSpaceType=$commitSpaceType")
                            }

                            val fileContentInStr = archiveRepo.getFileContentAsStringInACommit(curCommit, path)
                            val topicId = path.split("/").last().replace(".html", "").toInt()

                            var timing = System.currentTimeMillis()
                            noGoodIdTreeSet.add(topicId) // Proactively add id to ng list, otherwise ex throws and id not being added
                            val (resultTopicEntity, isSuccess) = TopicParserEntrance.parseTopic(
                                fileContentInStr,
                                topicId,
                                htmlSpaceType
                            )
                            timing = System.currentTimeMillis() - timing
                            if (timing > 1000L) {
                                log.error("$timing ms is costed to process $path in commit $curCommit!")
                            }

                            if (isSuccess) {
                                noGoodIdTreeSet.remove(topicId)
                                log.info("Parsing $htmlSpaceType-$topicId succeeded")
                                if (resultTopicEntity?.display == true) {
                                    log.info("topic id $htmlSpaceType-$topicId, title: ${resultTopicEntity.title}")
                                }
                                val json = GitHelper.GSON.toJson(resultTopicEntity)
                                writeJsonFile(path, json)
                            } else {
                                log.error("Parsing $htmlSpaceType-$topicId failed")
                            }
                        } catch (ex: Exception) {
                            log.error("Exception occurs when handling $path", ex)
                        }
                    }
                    writeJsonRepoLastCommitId(curCommit, jsonRepo)
                    commitJsonRepo(jsonRepo, commitSpaceType, curCommit, changedFilePathList)
                    prev = curCommit
                    writeNoGoodFile(noGoodIdTreeSet, commitSpaceType)
                    if (noGoodIdTreeSet.isNotEmpty()) {
                        log.error("NG LIST during $curCommit:\n $noGoodIdTreeSet")
                    }
                    if (commitIdx % 100 == 0) {
                        Git(jsonRepo).gc()
                    }
                    SpotChecker.genSpotCheckListFile(commitSpaceType)
                } catch (ex: Exception) {
                    log.error("Ex occurs when processing commit: $curCommit", ex)
                }
            }
        }
    }

    private fun writeJsonRepoLastCommitId(prevProcessedCommit: RevCommit, jsonRepo: Repository) {
        log.info("Writing last commit id: $prevProcessedCommit")
        val lastCommitIdFileLoc =
            Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR + "/" + BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
        val lastCommitIdFile = File(lastCommitIdFileLoc)
        val lastCommitIdStr = ObjectId.toString(prevProcessedCommit.id)
        FileWriter(lastCommitIdFile).use {
            it.write(lastCommitIdStr)
            it.flush()
        }
    }

    private fun writeNoGoodFile(noGoodIdTreeSet: TreeSet<Int>, commitSpaceType: SpaceType) {
        val noGoodFilePath = "${Config.BGM_ARCHIVE_GIT_REPO_DIR}/${commitSpaceType.name.lowercase()}/$NO_GOOD_FILE_NAME"
        val noGoodFile = File(noGoodFilePath)
        FileWriter(noGoodFile, true).use { fw ->
            BufferedWriter(fw).use { bfw ->
                for (i in noGoodIdTreeSet) {
                    bfw.write(i.toString())
                    bfw.write(NEW_LINE)
                }
                bfw.flush()
            }
        }
    }

    private fun commitJsonRepo(
        jsonRepo: Repository,
        commitSpaceType: SpaceType,
        archiveCommit: RevCommit,
        changedFilePathList: List<String>
    ) {
        var timing = System.currentTimeMillis()
        if (Config.BGM_ARCHIVE_PREFER_JGIT) {
            jgitCommitJsonRepo(jsonRepo, changedFilePathList, archiveCommit)
        } else {
            commandLineCommitJsonRepoAddFileInBatch(archiveCommit)
            // commandLineCommitJsonRepoAddFileSeparately(archiveCommit, changedFilePathList)
        }
        timing = System.currentTimeMillis() - timing
        log.info("Timing: $timing for git add/commit ${archiveCommit.fullMessage}")
    }

    private fun commandLineCommitJsonRepoAddFileSeparately(
        archiveCommit: RevCommit,
        changedFilePathList: List<String>
    ) {
        val commitMsg = archiveCommit.fullMessage
        val jsonRepoDir = File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR)
        val commitMsgFile =
            jsonRepoDir.resolve(".git").resolve("tmp-" + UUID.randomUUID())
        FileWriter(commitMsgFile).use {
            it.write(commitMsg)
            it.flush()
        }
        for (path in changedFilePathList) {
            val addPathProcess = Runtime.getRuntime()
                .exec("git add ${path.replace("html", "json")}", null, jsonRepoDir)
            addPathProcess.blockAndPrintProcessResults()
        }
        val addLastCommitIdProcess = Runtime.getRuntime().exec(
            "git add $BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME",
            null, jsonRepoDir
        )
        addLastCommitIdProcess.blockAndPrintProcessResults()
        val gitProcess = Runtime.getRuntime()
            .exec("git commit -F " + commitMsgFile.absolutePath, null, jsonRepoDir)
        gitProcess.blockAndPrintProcessResults()
        commitMsgFile.delete()
    }

    private fun commandLineCommitJsonRepoAddFileInBatch(archiveCommit: RevCommit) {
        val commitMsg = archiveCommit.fullMessage
        val jsonRepoDir = File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR)
        val commitMsgFile =
            jsonRepoDir.resolve(".git").resolve("tmp-" + UUID.randomUUID())
        FileWriter(commitMsgFile).use {
            it.write(commitMsg)
            it.flush()
        }
        log.info("About to git add")
        var gitProcess = Runtime.getRuntime()
            .exec("git add *", null, jsonRepoDir)
        gitProcess.blockAndPrintProcessResults()
        log.info("Complete git add")
        log.info("About to git commit")
        gitProcess = Runtime.getRuntime()
            .exec("git commit -F " + commitMsgFile.absolutePath, null, jsonRepoDir)
        gitProcess.blockAndPrintProcessResults()
        log.info("Complete git commit")
        commitMsgFile.delete()
    }

    private fun jgitCommitJsonRepo(
        jsonRepo: Repository,
        changedFilePathList: List<String>,
        archiveCommit: RevCommit
    ) {
        Git(jsonRepo).use { git ->
            log.info("About to git add")
            changedFilePathList.forEach { path ->
                git.add()
                    .addFilepattern(path.replace("html", "json"))
                    .call()
            }
            git.add()
                .addFilepattern(BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME)
                .call()
            log.info("Complete git add")
            log.info("About to git commit")
            git.commit()
                .setMessage(archiveCommit.fullMessage)
                .call()
            log.info("Complete git commit")
        }
    }

    fun Process.blockAndPrintProcessResults(): List<String> {
        val result = ArrayList<String?>()
        // Here actually block the process
        InputStreamReader(this.inputStream).use { isr ->
            BufferedReader(isr).use { reader ->
                var line: String?
                // reader.readLine()
                while (reader.readLine().also { line = it } != null) {
                    System.err.println(line)
                    result.add(line)
                }
            }
        }
        return result.filterNotNull()
    }

    private fun writeJsonFile(path: String, json: String) {
        val jsonPath = path.replace("html", "json")
        val jsonFileLoc = File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR).resolve(jsonPath)
        jsonFileLoc.parentFile.mkdirs()
        FileOutputStream(jsonFileLoc).use { fos ->
            OutputStreamWriter(fos, UTF_8).use { osw ->
                BufferedWriter(osw).use { bw ->
                    bw.write(json)
                    bw.flush()
                }
            }
        }
    }

}