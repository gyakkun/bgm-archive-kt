package moe.nyamori.bgm.git

import io.javalin.http.sse.NEW_LINE
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.TopicParser
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.slf4j.LoggerFactory
import java.io.*
import java.util.*


object CommitToJsonProcessor {
    private val log = LoggerFactory.getLogger(CommitToJsonProcessor.javaClass)

    @JvmStatic
    fun main(arg: Array<String>) {
        job()
    }

    fun job() {
        val walk =
            GitHelper.getWalkBetweenPrevProcessedCommitAndLatestCommitInReverseOrder()
        val archiveRepo = GitHelper.getArchiveRepo()
        val jsonRepo = GitHelper.getJsonRepo()
        var prevCommit = walk.next()
        val lastProcessedCommit = GitHelper.getPrevProcessedCommitRef()

        // FIXME: Workaround the inclusive walk boundary
        while (prevCommit != null) {
            if (lastProcessedCommit == prevCommit) {
                break
            }
            prevCommit = walk.next()
        }

        log.info("The previously processed commit: $prevCommit")
        run breakable@{
            walk.forEachIndexed { commitIdx, curCommit ->
                try {
                    val noGoodIdTreeSet = TreeSet<Int>()
                    if (curCommit == prevCommit) {
                        log.error("The first commit being iterated twice!")
                        return@breakable
                    }

                    if (curCommit.fullMessage.startsWith("META") || curCommit.fullMessage.startsWith("init")) {
                        log.warn("Iterating meta or init commit: $curCommit")
                        return@forEachIndexed // continue
                    }

                    val commitSpaceType = if (curCommit.fullMessage.startsWith("SUBJECT")) {
                        SpaceType.SUBJECT
                    } else if (curCommit.fullMessage.startsWith("GROUP")) {
                        SpaceType.GROUP
                    } else {
                        throw IllegalStateException("Not a group or subject topic!")
                    }

                    val changedFilePathList = GitHelper.findChangedFilePaths(archiveRepo, prevCommit, curCommit)

                    for (pathIdx in changedFilePathList.indices) {
                        val path = changedFilePathList[pathIdx]
                        try {
                            if (!path.endsWith("html")) continue
                            log.warn("Cur commit: $curCommit, path: $path")
                            val htmlSpaceType: SpaceType =
                                if (path.startsWith("group") && curCommit.fullMessage.startsWith("GROUP")) {
                                    SpaceType.GROUP
                                } else if (path.startsWith("subject") && curCommit.fullMessage.startsWith("SUBJECT")) {
                                    SpaceType.SUBJECT
                                } else {
                                    log.error("Not a subject or group topic! Path: $path, commit full msg: ${curCommit.fullMessage}")
                                    SpaceType.valueOf(path.split("/").first().uppercase())
                                }

                            if (htmlSpaceType != commitSpaceType) {
                                log.error("Html space type not consistent with commit space type!")
                            }

                            val fileContentInStr = GitHelper.getFileContentInACommit(archiveRepo, curCommit, path)
                            val topicId = path.split("/").last().replace(".html", "").toInt()

                            var timing = System.currentTimeMillis()
                            val (resultTopicEntity, isSuccess) = TopicParser.parseTopic(
                                fileContentInStr,
                                topicId,
                                htmlSpaceType
                            )
                            timing = System.currentTimeMillis() - timing
                            if (timing > 1000L) {
                                log.error("$timing ms is costed to process $path in commit $curCommit!")
                            }

                            if (isSuccess) {
                                log.info("Parsing $topicId succeeded")
                                if (resultTopicEntity?.display == true) {
                                    log.info("topic id $topicId, title: ${resultTopicEntity.title}")
                                }
                                val json = GitHelper.GSON.toJson(resultTopicEntity)
                                writeJsonFile(path, json)
                            } else {
                                log.error("Parsing $topicId failed")
                                noGoodIdTreeSet.add(topicId)
                            }
                        } catch (ex: Exception) {
                            log.error("Exception occurs when handling $path", ex)
                        }
                    }
                    writeJsonRepoLastCommitId(curCommit, jsonRepo)
                    commitJsonRepo(jsonRepo, commitSpaceType, curCommit, changedFilePathList)
                    prevCommit = curCommit
                    writeNoGoodFile(noGoodIdTreeSet, commitSpaceType)
                    if (noGoodIdTreeSet.isNotEmpty()) {
                        log.error("NG LIST during $curCommit:\n $noGoodIdTreeSet")
                    }
                    if (commitIdx % 100 == 0) {
                        Git(jsonRepo).gc()
                    }
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
        val noGoodFilePath = Config.BGM_ARCHIVE_GIT_REPO_DIR + "/" + commitSpaceType.name.lowercase() + "/ng.txt"
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
            printResults(addPathProcess)
        }
        val addLastCommitIdProcess = Runtime.getRuntime().exec(
            "git add $BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME",
            null, jsonRepoDir
        )
        printResults(addLastCommitIdProcess)
        val gitProcess = Runtime.getRuntime()
            .exec("git commit -F " + commitMsgFile.absolutePath, null, jsonRepoDir)
        printResults(gitProcess)
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
        var gitProcess = Runtime.getRuntime()
            .exec("git add *", null, jsonRepoDir)
        printResults(gitProcess)
        gitProcess = Runtime.getRuntime()
            .exec("git commit -F " + commitMsgFile.absolutePath, null, jsonRepoDir)
        printResults(gitProcess)
        commitMsgFile.delete()
    }

    private fun jgitCommitJsonRepo(
        jsonRepo: Repository,
        changedFilePathList: List<String>,
        archiveCommit: RevCommit
    ) {
        Git(jsonRepo).use { git ->
            changedFilePathList.forEach { path ->
                git.add()
                    .addFilepattern(path.replace("html", "json"))
                    .call()
            }
            git.add()
                .addFilepattern(BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME)
                .call()
            git.commit()
                .setMessage(archiveCommit.fullMessage)
                .call()
        }
    }

    fun printResults(process: Process) {
        // Here actually block the process
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            System.err.println(line)
        }
    }

    private fun writeJsonFile(path: String, json: String) {
        val jsonPath = path.replace("html", "json")
        val jsonFileLoc = File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR).resolve(jsonPath)
        jsonFileLoc.parentFile.mkdirs()
        FileWriter(jsonFileLoc).use { fw ->
            BufferedWriter(fw).use { bfw ->
                bfw.write(json)
                bfw.flush()
            }
        }
    }


}