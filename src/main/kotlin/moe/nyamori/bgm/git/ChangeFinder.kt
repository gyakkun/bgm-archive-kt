package moe.nyamori.bgm.git

import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.TopicParser
import org.eclipse.jgit.api.Git
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
import java.io.File
import java.io.IOException
import java.lang.IllegalStateException
import java.nio.charset.StandardCharsets
import java.util.TreeSet


object ChangeFinder {
    private val log = LoggerFactory.getLogger(ChangeFinder.javaClass)

    @JvmStatic
    fun main(arg: Array<String>) {
        val noGoodIdTreeSet = TreeSet<Int>()
        val walk =
            Helper.getWalkBetweenPrevProcessedCommitAndLatestCommitInReverseOrder()
        val archiveRepo = Helper.getArchiveRepo()
        var prevProcessedCommit = walk.next()
        log.info("The previously processed commit: $prevProcessedCommit")
        run breakable@{
            walk.forEachIndexed { idx, curCommit ->
                if (curCommit == prevProcessedCommit) {
                    log.error("The first commit being iterated twice!")
                    return@breakable
                }

                if (curCommit.fullMessage.startsWith("META") || curCommit.fullMessage.startsWith("init")) {
                    log.warn("Iterating meta or init commit: $curCommit")
                    return@forEachIndexed
                }

                val commitSpaceType = if (curCommit.fullMessage.startsWith("SUBJECT")) {
                    SpaceType.SUBJECT
                } else if (curCommit.fullMessage.startsWith("GROUP")) {
                    SpaceType.GROUP
                } else {
                    throw IllegalStateException("Not a group or subject topic!")
                }

                val changedFilePathList = findChangeFiles(archiveRepo, prevProcessedCommit, curCommit)
                for (innerIdx in changedFilePathList.indices) {
                    val path = changedFilePathList[innerIdx]
                    try {
                        if (!path.endsWith("html")) continue
                        log.warn("Cur commit: $curCommit, path: $path")
                        val htmlSpaceType: SpaceType =
                            if (path.startsWith("group") && curCommit.fullMessage.startsWith("GROUP")) {
                                SpaceType.GROUP
                            } else if (path.startsWith("subject") && curCommit.fullMessage.startsWith("SUBJECT")) {
                                SpaceType.SUBJECT
                            } else {
                                throw IllegalStateException("Not a subject or group topic!")
                            }

                        if (htmlSpaceType != commitSpaceType) {
                            throw IllegalStateException("Html space type not consistent with commit space type!")
                        }

                        val fileContentInStr = getFileContentInACommit(archiveRepo, curCommit, path)
                        val topicId = path.split("/").last().replace(".html", "").toInt()

                        val (resultTopicEntity, isSuccess) = TopicParser.parseTopic(
                            fileContentInStr,
                            topicId,
                            htmlSpaceType
                        )

                        if (isSuccess) {
                            log.info("Parsing $topicId succeeded")
                            if (resultTopicEntity?.display != null && resultTopicEntity.display == true) {
                                log.info("topic id $topicId, title: ${resultTopicEntity.title}")
                            }
                        } else {
                            log.error("Parsing $topicId failed")
                            noGoodIdTreeSet.add(topicId)
                        }
                    } catch (ex: Exception) {
                        log.error("Exception occurs when handling $path", ex)
                    }
                }
                prevProcessedCommit = curCommit
                if (idx > 10) return@breakable
            }
        }
        log.error("NG LIST $noGoodIdTreeSet")
    }

    fun findChangeFiles(repo: Repository, prevCommit: RevCommit, currentCommit: RevCommit): List<String> {
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
                        result.add(it.newPath)
                    }
            }
        }
        return result
    }

    @Throws(IOException::class)
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