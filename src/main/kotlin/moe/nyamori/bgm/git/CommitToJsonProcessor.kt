package moe.nyamori.bgm.git

import com.google.gson.GsonBuilder
import com.vladsch.flexmark.util.misc.FileUtil
import io.javalin.http.sse.NEW_LINE
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.TopicParser
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry.DEV_NULL
import org.eclipse.jgit.lib.Constants.*
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*


object CommitToJsonProcessor {
    private val log = LoggerFactory.getLogger(CommitToJsonProcessor.javaClass)

    @JvmStatic
    fun main(arg: Array<String>) {
        job()
    }

    fun job() {
        val walk =
            Helper.getWalkBetweenPrevProcessedCommitAndLatestCommitInReverseOrder()
        val archiveRepo = Helper.getArchiveRepo()
        val jsonRepo = Helper.getJsonRepo()
        var prevCommit = walk.next()
        val lastProcessedCommit = Helper.getPrevProcessedCommitRef()

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

                    val changedFilePathList = findChangedFiles(archiveRepo, prevCommit, curCommit)

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
                                    throw IllegalStateException("Not a subject or group topic! Path: $path, commit full msg: ${curCommit.fullMessage}")
                                }

                            if (htmlSpaceType != commitSpaceType) {
                                throw IllegalStateException("Html space type not consistent with commit space type!")
                            }

                            val fileContentInStr = getFileContentInACommit(archiveRepo, curCommit, path)
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
                                val json = Helper.GSON.toJson(resultTopicEntity)
                                writeFile(path, json)
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
                    bfw.write(i)
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
        if (Config.BGM_ARCHIVE_PREFER_JGIT) {
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
        } else {
            val commitMsg = archiveCommit.fullMessage
            val jsonRepoDir = File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR)
            val commitMsgFile =
                jsonRepoDir.resolve(".git").resolve("tmp" + UUID.randomUUID())
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
    }

    fun printResults(process: Process) {
        // Here actually block the process
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            System.err.println(line)
        }
    }

    private fun writeFile(path: String, json: String) {
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

    fun findChangedFiles(repo: Repository, prevCommit: RevCommit, currentCommit: RevCommit): List<String> {
        val prevTree = prevCommit.tree
        val curTree = currentCommit.tree
        val result = ArrayList<String>()
        repo.newObjectReader().use { reader ->
            val oldTreeIter = CanonicalTreeParser()
            oldTreeIter.reset(reader, prevTree)
            val newTreeIter = CanonicalTreeParser()
            newTreeIter.reset(reader, curTree)
            Git(repo).use { git ->
                git.diff()
                    .setNewTree(newTreeIter)
                    .setOldTree(oldTreeIter)
                    .call()
                    .forEach {
                        if (it.newPath == DEV_NULL) return@forEach
                        result.add(it.newPath)
                    }
            }
        }
        return result
    }

    private fun getFileContentInACommit(repo: Repository, commit: RevCommit, path: String): String {
        TreeWalk.forPath(repo, path, commit.tree).use { treeWalk ->
            val blobId: ObjectId = treeWalk.getObjectId(0)
            repo.newObjectReader().use { objectReader ->
                val objectLoader: ObjectLoader = objectReader.open(blobId)
                val bytes = objectLoader.bytes
                return String(bytes, StandardCharsets.UTF_8)
            }
        }
    }

    object Helper {

        val GSON = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        fun getWalkBetweenPrevProcessedCommitAndLatestCommitInReverseOrder(): RevWalk {
            val prevProcessedCommit = getPrevProcessedCommitRef()
            val latestCommit = getLatestCommitRef()
            getArchiveRepo().use { archiveRepo ->
                val revWalk = RevWalk(archiveRepo)
                revWalk.markStart(latestCommit)
                revWalk.markUninteresting(prevProcessedCommit)
                revWalk.sort(RevSort.REVERSE, true)
                return revWalk
            }
        }

        fun getPrevProcessedCommitRef(): RevCommit {
            getArchiveRepo().use { archiveRepo ->
                val revWalk = RevWalk(archiveRepo)
                val prevProcessedCommitRevId = archiveRepo.resolve(getPrevProcessedCommitRevId())
                val prevProcessedCommit = revWalk.parseCommit(prevProcessedCommitRevId)
                return prevProcessedCommit
            }
        }

        fun getLatestCommitRef(): RevCommit {
            getArchiveRepo().use { archiveRepo ->
                val revWalk = RevWalk(archiveRepo)
                val latestHeadCommitRevId = archiveRepo.resolve(HEAD)
                val latestHeadCommit = revWalk.parseCommit(latestHeadCommitRevId)
                return latestHeadCommit
            }
        }

        fun getPrevProcessedCommitRevId(): String {
            val prevProcessedCommitRevIdFile =
                File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR).resolve(BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME)
            val rawFileStr = FileUtil.getFileContent(prevProcessedCommitRevIdFile)!!
            return rawFileStr.trim()
        }

        fun getArchiveRepo(): Repository {
            return FileRepositoryBuilder()
                .setGitDir(File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve(DOT_GIT))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()
        }

        fun getJsonRepo(): Repository {
            return FileRepositoryBuilder()
                .setGitDir(File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR).resolve(DOT_GIT))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()
        }
    }
}