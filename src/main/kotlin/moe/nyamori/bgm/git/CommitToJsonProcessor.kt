package moe.nyamori.bgm.git

import io.javalin.http.sse.NEW_LINE
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_SPOT_CHECKER_TIMEOUT_THRESHOLD_MS
import moe.nyamori.bgm.config.RepoDto
import moe.nyamori.bgm.config.getCouplingJsonRepo
import moe.nyamori.bgm.config.hasCouplingJsonRepo
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.GitHelper.findChangedFilePaths
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.git.GitHelper.getPrevProcessedArchiveCommitRef
import moe.nyamori.bgm.git.GitHelper.getWalkBetweenPrevProcessedArchiveCommitAndLatestArchiveCommitInReverseOrder
import moe.nyamori.bgm.git.GitHelper.simpleName
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.TopicParserEntrance
import moe.nyamori.bgm.util.GitCommitIdHelper.sha1Str
import moe.nyamori.bgm.util.blockAndPrintProcessResults
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
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
        val reposToProcess = mutableListOf<RepoDto>()
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

        var shouldSpotCheck = true
        var firstCommitSpaceType: SpaceType? = null
        var repoCounter = 0
        reposToProcess.forEach { archiveRepo ->
            repoCounter++
            val walk =
                archiveRepo.getWalkBetweenPrevProcessedArchiveCommitAndLatestArchiveCommitInReverseOrder()
            val jsonRepo = archiveRepo.getCouplingJsonRepo()!!
            var prev = walk.next() // used in the iteration (now = next() ... prev = now)
            val prevProcessedArchiveCommitRef = archiveRepo.getPrevProcessedArchiveCommitRef()

            // FIXME: Workaround the inclusive walk boundary
            while (prev != null) {
                if (prevProcessedArchiveCommitRef == prev) {
                    break
                }
                prev = walk.next()
            }

            log.info("The previously processed commit for repo - ${archiveRepo.repo.simpleName()}: $prev")
            run breakable@{
                walk.forEachIndexed { commitIdx, curCommit ->
                    var timing = System.currentTimeMillis()
                    try {
                        val noGoodIdTreeSet = TreeSet<Int>()
                        if (curCommit == prev) {
                            log.error("The first commit being iterated twice! Repo - ${archiveRepo.repo.simpleName()}")
                            return@breakable
                        }

                        if (curCommit.fullMessage.startsWith("META") || curCommit.fullMessage.startsWith("init")) {
                            log.warn("Iterating meta or init commit for repo ${archiveRepo.repo.simpleName()}: $curCommit")
                            return@forEachIndexed // continue
                        }

                        val commitMsgSplitArr = curCommit.fullMessage.split(" ")
                        val commitSpaceType = if (commitMsgSplitArr.isNotEmpty()) {
                            when (commitMsgSplitArr[0]) {
                                in SpaceType.entries.map { it.toString().uppercase() } -> SpaceType.valueOf(
                                    commitMsgSplitArr[0]
                                )

                                else -> {
                                    throw IllegalStateException("${commitMsgSplitArr[0]} is not a valid topic type!")
                                }
                            }
                        } else {
                            throw IllegalStateException("Not a valid commit message! - ${curCommit.shortMessage}")
                        }
                        if (firstCommitSpaceType == null) firstCommitSpaceType = commitSpaceType
                        val changedHtmlFilePathList = archiveRepo.repo.findChangedFilePaths(prev, curCommit)

                        for (pathIdx in changedHtmlFilePathList.indices) {
                            val path = changedHtmlFilePathList[pathIdx]
                            try {
                                if (path.endsWith(NO_GOOD_FILE_NAME)) {
                                    handleNoGoodFile(
                                        Path.of(archiveRepo.repo.absolutePathWithoutDotGit()).resolve(path),
                                        archiveRepo.getFileContentAsStringInACommit(curCommit.sha1Str(), path),
                                        noGoodIdTreeSet
                                    )
                                    continue
                                }
                                if (!path.endsWith("html")) continue
                                log.warn(
                                    "Cur commit id: ${
                                        curCommit.name().substring(0, 8)
                                    }, path: ${archiveRepo.repo.simpleName()}/$path , message:  ${curCommit.shortMessage}"
                                )
                                val pathSplitArr = path.split("/")
                                val htmlSpaceType = if (pathSplitArr.isNotEmpty()) {
                                    when (pathSplitArr[0]) {
                                        in SpaceType.entries.map { it.toString().lowercase() } ->
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
                                    log.error("At commit -  ${curCommit.name}, repo - ${archiveRepo.repo.simpleName()}, path = $path")
                                }

                                val fileContentInStr =
                                    archiveRepo.getFileContentAsStringInACommit(curCommit.sha1Str(), path)
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
                                    log.error("$timing ms is costed to process ${archiveRepo.repo.simpleName()}/$path in commit $curCommit!")
                                }

                                if (isSuccess) {
                                    noGoodIdTreeSet.remove(topicId)
                                    log.info("Parsing $htmlSpaceType-$topicId succeeded, repo - ${archiveRepo.repo.simpleName()}")
                                    if (resultTopicEntity?.display == true) {
                                        log.info("topic id $htmlSpaceType-$topicId, title: ${resultTopicEntity.title}")
                                    }
                                    val json = GitHelper.GSON.toJson(resultTopicEntity)
                                    writeJsonFile(jsonRepo, path, json)
                                } else {
                                    log.error("Parsing $htmlSpaceType-$topicId failed, repo - ${archiveRepo.repo.simpleName()}")
                                }
                            } catch (ex: Exception) {
                                log.error(
                                    "Exception occurs when handling ${archiveRepo.repo.simpleName()}/$path, commit - ${
                                        curCommit.name.substring(
                                            0,
                                            8
                                        )
                                    } - ${curCommit.shortMessage}", ex
                                )
                            }
                        }
                        writeJsonRepoLastCommitId(curCommit, jsonRepo)
                        commitJsonRepo(jsonRepo, commitSpaceType, curCommit, changedHtmlFilePathList)
                        prev = curCommit
                        writeNoGoodFile(archiveRepo, noGoodIdTreeSet, commitSpaceType)
                        if (noGoodIdTreeSet.isNotEmpty()) {
                            log.error(
                                "NG LIST during repo - ${archiveRepo.repo.simpleName()}, commit - ${
                                    curCommit.name.substring(
                                        0,
                                        8
                                    )
                                } -  ${curCommit.shortMessage}:\n $noGoodIdTreeSet"
                            )
                        }
                        if (commitIdx % 100 == 0) {
                            Git(jsonRepo.repo).gc()
                        }
                        timing = System.currentTimeMillis() - timing
                        if (timing >= BGM_ARCHIVE_SPOT_CHECKER_TIMEOUT_THRESHOLD_MS) {
                            log.error(
                                "Process commit taking longer than expected (threshold:${BGM_ARCHIVE_SPOT_CHECKER_TIMEOUT_THRESHOLD_MS}ms). Skipping generating spot check file. Cur commit: ${
                                    curCommit.sha1Str()
                                }"
                            )
                            shouldSpotCheck = false
                        }
                        if (commitIdx != 0) {
                            log.warn(
                                "Not processing the first commit. Not generating spot check file for safety. Cur commit: ${
                                    curCommit.sha1Str()
                                }"
                            )
                            shouldSpotCheck = false
                        }
                        if (repoCounter != 1) {
                            log.warn("Processing more than one archive repo. Skipping spot check.")
                            shouldSpotCheck = false
                        }
                    } catch (ex: Exception) {
                        log.error(
                            "Ex occurs when processing repo - ${archiveRepo.repo.simpleName()},  commit - ${
                                curCommit.name.substring(
                                    0,
                                    8
                                )
                            } - ${curCommit.shortMessage}", ex
                        )
                    }
                }
                runCatching {
                    if (shouldSpotCheck && firstCommitSpaceType != null) {
                        SpotChecker.genSpotCheckListFile(archiveRepo, firstCommitSpaceType!!)
                    }
                }.onFailure {
                    if (shouldSpotCheck && firstCommitSpaceType != null) {
                        log.error("Failed at spot check. SpaceType=${firstCommitSpaceType!!}")
                    }
                }
            }
        }
    }

    private fun handleNoGoodFile(path: Path, content: String, noGoodIdTreeSet: TreeSet<Int>) = runCatching {
        val curNgFileLines = path.toFile().readLines().mapNotNull { it.toIntOrNull() }
        val lineInCommit = content.lines().mapNotNull { it.toIntOrNull() }
        val fullSet = (curNgFileLines + lineInCommit).toSet()
        val toAdd = fullSet.filter { it in lineInCommit && it !in curNgFileLines }
        noGoodIdTreeSet.addAll(toAdd)
    }.onFailure { log.error("Failed to handle no good file: ", it) }

    private fun writeJsonRepoLastCommitId(prevProcessedCommit: RevCommit, jsonRepo: RepoDto) {
        log.info("Writing last commit id: ${jsonRepo.repo.simpleName()}/$prevProcessedCommit")
        val lastCommitIdFile =
            File(jsonRepo.repo.absolutePathWithoutDotGit()).resolve(BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME)
        val lastCommitIdStr = prevProcessedCommit.sha1Str()
        FileWriter(lastCommitIdFile).use {
            it.write(lastCommitIdStr)
            it.flush()
        }
    }

    private fun writeNoGoodFile(archiveRepo: RepoDto, noGoodIdTreeSet: TreeSet<Int>, commitSpaceType: SpaceType) {
        val noGoodFile = File(archiveRepo.repo.absolutePathWithoutDotGit())
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
        jsonRepo: RepoDto,
        commitSpaceType: SpaceType,
        archiveCommit: RevCommit,
        changedHtmlFilePathList: List<String>
    ) {
        var timing = System.currentTimeMillis()
        if (Config.BGM_ARCHIVE_PREFER_JGIT) {
            jgitCommitJsonRepo(jsonRepo, changedHtmlFilePathList, archiveCommit)
        } else {
            if (Config.BGM_ARCHIVE_PREFER_GIT_BATCH_ADD) {
                commandLineCommitJsonRepoAddFileInBatch(jsonRepo, archiveCommit)
            } else {
                commandLineCommitJsonRepoAddFileSeparately(jsonRepo, archiveCommit, changedHtmlFilePathList)
            }
        }
        timing = System.currentTimeMillis() - timing
        if (Config.BGM_ARCHIVE_IS_REMOVE_JSON_AFTER_PROCESS) {
            if (jsonRepo.repo.isBare) {
                log.error(
                    "Json repo {} is a bare git repo. Will not remove json files.",
                    jsonRepo.repo.absolutePathWithoutDotGit()
                )
            } else {
                log.warn("Removing json files for commit : {}", archiveCommit.shortMessage)
                removeJsonFiles(jsonRepo, changedHtmlFilePathList)
                log.warn("Done removing json files for commit : {}", archiveCommit.shortMessage)
            }
        }
        log.info("Timing: $timing for git add/commit ${archiveCommit.fullMessage}")
    }

    private fun removeJsonFiles(jsonRepo: RepoDto, changedHtmlFilePathList: List<String>) {
        val jsonRepoDir = File(jsonRepo.repo.absolutePathWithoutDotGit())
        val jsonFileList = changedHtmlFilePathList.mapNotNull { path ->
            if (!path.endsWith("html")) return@mapNotNull null
            val absolutePathFile = jsonRepoDir.resolve(path.replace("html", "json"))
            if (!absolutePathFile.exists() || absolutePathFile.isDirectory) {
                log.debug("Failed to find file ${absolutePathFile.absolutePath}")
                return@mapNotNull null
            }
            return@mapNotNull absolutePathFile
        }
        jsonFileList.forEach {
            val res = it.delete()
            if (!res) log.error("Failed to delete json file after process commit: {}", it)
        }
    }

    private fun commandLineCommitJsonRepoAddFileSeparately(
        jsonRepo: RepoDto,
        archiveCommit: RevCommit,
        changedHtmlFilePathList: List<String>
    ) {
        val commitMsg = archiveCommit.fullMessage
        val jsonRepoDir = File(jsonRepo.repo.absolutePathWithoutDotGit())
        val commitMsgFile =
            jsonRepoDir.resolve(".git").resolve("tmp-" + UUID.randomUUID())
        FileWriter(commitMsgFile).use {
            it.write(commitMsg)
            it.flush()
        }
        val jsonFileListToGitAdd = StringBuffer(" ")
        // There's a trailing space on each file to add
        changedHtmlFilePathList.forEach { path ->
            if (!path.endsWith("html")) return@forEach
            val absolutePathFile = jsonRepoDir.resolve(path.replace("html", "json"))
            if (!absolutePathFile.exists() || absolutePathFile.isDirectory) {
                log.error("Failed to find file ${absolutePathFile.absolutePath}")
                return@forEach
            }
            jsonFileListToGitAdd.append("${absolutePathFile.absolutePath} ")
        }
        BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME.apply {
            val absolutePath = jsonRepoDir.resolve(this)
            jsonFileListToGitAdd.append("${absolutePath.absolutePath} ")
        }
        val gitAddCommand = "git add ${jsonFileListToGitAdd.toString()}"
        log.debug("File list to git add: ${jsonFileListToGitAdd.toString()}")
        log.info("About to git add")
        log.debug("Json repo dir: ${jsonRepoDir.absolutePath}")
        log.debug("Git command: $gitAddCommand")
        val addPathProcess = Runtime.getRuntime()
            .exec(gitAddCommand, null, jsonRepoDir)
        addPathProcess.blockAndPrintProcessResults(cmd = gitAddCommand)
        log.info("Complete git add")
        log.info("About to git commit")
        val gitCommitCmd = "git commit --quiet -F " + commitMsgFile.absolutePath
        val gitProcess = Runtime.getRuntime()
            .exec(gitCommitCmd, null, jsonRepoDir)
        gitProcess.blockAndPrintProcessResults(cmd = gitCommitCmd)
        log.info("Complete git commit")
        commitMsgFile.delete()
    }

    private fun commandLineCommitJsonRepoAddFileInBatch(jsonRepo: RepoDto, archiveCommit: RevCommit) {
        val commitMsg = archiveCommit.fullMessage
        val jsonRepoDir = File(jsonRepo.repo.absolutePathWithoutDotGit())
        val commitMsgFile =
            jsonRepoDir.resolve(".git").resolve("tmp-" + UUID.randomUUID())
        FileWriter(commitMsgFile).use {
            it.write(commitMsg)
            it.flush()
        }
        log.info("About to git add")
        val gitAddAsterisk = "git add *"
        var gitProcess = Runtime.getRuntime()
            .exec(gitAddAsterisk, null, jsonRepoDir)
        gitProcess.blockAndPrintProcessResults(cmd = gitAddAsterisk)
        log.info("Complete git add")
        log.info("About to git commit")
        val gitCommitCmd = "git commit --quiet -F " + commitMsgFile.absolutePath
        gitProcess = Runtime.getRuntime()
            .exec(gitCommitCmd, null, jsonRepoDir)
        gitProcess.blockAndPrintProcessResults(cmd = gitCommitCmd)
        log.info("Complete git commit")
        commitMsgFile.delete()
    }

    private fun jgitCommitJsonRepo(
        jsonRepo: RepoDto,
        changedFilePathList: List<String>,
        archiveCommit: RevCommit
    ) {
        Git(jsonRepo.repo).use { git ->
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


    private fun writeJsonFile(jsonRepo: RepoDto, path: String, json: String) {
        val jsonPath = path.replace("html", "json")
        val jsonFileLoc = File(jsonRepo.repo.absolutePathWithoutDotGit()).resolve(jsonPath)
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