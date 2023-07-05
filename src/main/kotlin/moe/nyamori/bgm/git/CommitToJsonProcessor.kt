package moe.nyamori.bgm.git

import io.javalin.http.sse.NEW_LINE
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.GitHelper.couplingJsonRepo
import moe.nyamori.bgm.git.GitHelper.findChangedFilePaths
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.git.GitHelper.getPrevProcessedArchiveCommitRef
import moe.nyamori.bgm.git.GitHelper.getWalkBetweenPrevProcessedArchiveCommitAndLatestArchiveCommitInReverseOrder
import moe.nyamori.bgm.git.GitHelper.hasCouplingJsonRepo
import moe.nyamori.bgm.git.GitHelper.simpleName
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


object CommitToJsonProcessor {
    private val log = LoggerFactory.getLogger(CommitToJsonProcessor.javaClass)
    private const val NO_GOOD_FILE_NAME = "ng.txt"


    @JvmStatic
    fun main(arg: Array<String>) {
        job()
    }

    fun job(
        isAll: Boolean = false,
        idx: Int = 0
    ) {
        val reposToProcess = mutableListOf<Repository>()
        if (isAll) {
            GitHelper.allArchiveRepoListSingleton
                .filter { it.hasCouplingJsonRepo() }
                .map { reposToProcess.add(it) }
        } else {
            if (idx in GitHelper.allArchiveRepoListSingleton.indices
                && GitHelper.allArchiveRepoListSingleton[idx].hasCouplingJsonRepo()
            ) {
                reposToProcess.add(GitHelper.allArchiveRepoListSingleton[idx])
            }
        }

        reposToProcess.forEach { archiveRepo ->
            val walk =
                archiveRepo.getWalkBetweenPrevProcessedArchiveCommitAndLatestArchiveCommitInReverseOrder()
            val jsonRepo = archiveRepo.couplingJsonRepo()!!
            var prev = walk.next() // used in the iteration (now = next() ... prev = now)
            val prevProcessedArchiveCommitRef = archiveRepo.getPrevProcessedArchiveCommitRef()

            // FIXME: Workaround the inclusive walk boundary
            while (prev != null) {
                if (prevProcessedArchiveCommitRef == prev) {
                    break
                }
                prev = walk.next()
            }

            log.info("The previously processed commit for repo - ${archiveRepo.simpleName()}: $prev")
            run breakable@{
                walk.forEachIndexed { commitIdx, curCommit ->
                    try {
                        val noGoodIdTreeSet = TreeSet<Int>()
                        if (curCommit == prev) {
                            log.error("The first commit being iterated twice! Repo - ${archiveRepo.simpleName()}")
                            return@breakable
                        }

                        if (curCommit.fullMessage.startsWith("META") || curCommit.fullMessage.startsWith("init")) {
                            log.warn("Iterating meta or init commit for repo ${archiveRepo.simpleName()}: $curCommit")
                            return@forEachIndexed // continue
                        }

                        val commitMsgSplitArr = curCommit.fullMessage.split(" ")
                        val commitSpaceType = if (commitMsgSplitArr.isNotEmpty()) {
                            when (commitMsgSplitArr[0]) {
                                in SpaceType.values().map { it.toString().uppercase() } -> SpaceType.valueOf(
                                    commitMsgSplitArr[0]
                                )

                                else -> {
                                    throw IllegalStateException("${commitMsgSplitArr[0]} is not a valid topic type!")
                                }
                            }
                        } else {
                            throw IllegalStateException("Not a valid commit message! - ${curCommit.shortMessage}")
                        }

                        val changedFilePathList = archiveRepo.findChangedFilePaths(prev, curCommit)

                        for (pathIdx in changedFilePathList.indices) {
                            val path = changedFilePathList[pathIdx]
                            try {
                                if (!path.endsWith("html")) continue
                                log.warn(
                                    "Cur commit id: ${
                                        curCommit.name().substring(0, 8)
                                    }, path: ${archiveRepo.simpleName()}/$path , message:  ${curCommit.shortMessage}"
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
                                    log.error("At commit -  ${curCommit.name}, repo - ${archiveRepo.simpleName()}, path = $path")
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
                                    log.error("$timing ms is costed to process ${archiveRepo.simpleName()}/$path in commit $curCommit!")
                                }

                                if (isSuccess) {
                                    noGoodIdTreeSet.remove(topicId)
                                    log.info("Parsing $htmlSpaceType-$topicId succeeded, repo - ${archiveRepo.simpleName()}")
                                    if (resultTopicEntity?.display == true) {
                                        log.info("topic id $htmlSpaceType-$topicId, title: ${resultTopicEntity.title}")
                                    }
                                    val json = GitHelper.GSON.toJson(resultTopicEntity)
                                    writeJsonFile(jsonRepo, path, json)
                                } else {
                                    log.error("Parsing $htmlSpaceType-$topicId failed, repo - ${archiveRepo.simpleName()}")
                                }
                            } catch (ex: Exception) {
                                log.error(
                                    "Exception occurs when handling ${archiveRepo.simpleName()}/$path, commit - ${
                                        curCommit.name.substring(
                                            0,
                                            8
                                        )
                                    } - ${curCommit.shortMessage}", ex
                                )
                            }
                        }
                        writeJsonRepoLastCommitId(curCommit, jsonRepo)
                        commitJsonRepo(jsonRepo, commitSpaceType, curCommit, changedFilePathList)
                        prev = curCommit
                        writeNoGoodFile(archiveRepo, noGoodIdTreeSet, commitSpaceType)
                        if (noGoodIdTreeSet.isNotEmpty()) {
                            log.error(
                                "NG LIST during repo - ${archiveRepo.simpleName()}, commit - ${
                                    curCommit.name.substring(
                                        0,
                                        8
                                    )
                                } -  ${curCommit.shortMessage}:\n $noGoodIdTreeSet"
                            )
                        }
                        if (commitIdx % 100 == 0) {
                            Git(jsonRepo).gc()
                        }
                        SpotChecker.genSpotCheckListFile(archiveRepo, commitSpaceType)
                    } catch (ex: Exception) {
                        log.error(
                            "Ex occurs when processing repo - ${archiveRepo.simpleName()},  commit - ${
                                curCommit.name.substring(
                                    0,
                                    8
                                )
                            } - ${curCommit.shortMessage}", ex
                        )
                    }
                }
            }
        }
    }

    private fun writeJsonRepoLastCommitId(prevProcessedCommit: RevCommit, jsonRepo: Repository) {
        log.info("Writing last commit id: ${jsonRepo.simpleName()}/$prevProcessedCommit")
        val lastCommitIdFile =
            File(jsonRepo.absolutePathWithoutDotGit()).resolve(BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME)
        val lastCommitIdStr = ObjectId.toString(prevProcessedCommit.id)
        FileWriter(lastCommitIdFile).use {
            it.write(lastCommitIdStr)
            it.flush()
        }
    }

    private fun writeNoGoodFile(archiveRepo: Repository, noGoodIdTreeSet: TreeSet<Int>, commitSpaceType: SpaceType) {
        val noGoodFile = File(archiveRepo.absolutePathWithoutDotGit())
            .resolve("${commitSpaceType.name.lowercase()}/$NO_GOOD_FILE_NAME")
        if (!noGoodFile.exists()) noGoodFile.createNewFile()
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
            commandLineCommitJsonRepoAddFileInBatch(jsonRepo, archiveCommit)
            // commandLineCommitJsonRepoAddFileSeparately(archiveCommit, changedFilePathList)
        }
        timing = System.currentTimeMillis() - timing
        log.info("Timing: $timing for git add/commit ${archiveCommit.fullMessage}")
    }

    private fun commandLineCommitJsonRepoAddFileSeparately(
        archiveRepo: Repository,
        archiveCommit: RevCommit,
        changedFilePathList: List<String>
    ) {
        val commitMsg = archiveCommit.fullMessage
        val jsonRepoDir = File(archiveRepo.absolutePathWithoutDotGit())
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

    private fun commandLineCommitJsonRepoAddFileInBatch(jsonRepo: Repository, archiveCommit: RevCommit) {
        val commitMsg = archiveCommit.fullMessage
        val jsonRepoDir = File(jsonRepo.absolutePathWithoutDotGit())
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

    private fun writeJsonFile(jsonRepo: Repository, path: String, json: String) {
        val jsonPath = path.replace("html", "json")
        val jsonFileLoc = File(jsonRepo.absolutePathWithoutDotGit()).resolve(jsonPath)
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