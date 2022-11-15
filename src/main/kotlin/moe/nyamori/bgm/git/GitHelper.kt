package moe.nyamori.bgm.git

import ch.qos.logback.core.CoreConstants.EMPTY_STRING
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry.DEV_NULL
import org.eclipse.jgit.lib.Constants.DOT_GIT
import org.eclipse.jgit.lib.Constants.HEAD
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import java.nio.charset.StandardCharsets

object GitHelper {

    val GSON: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

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
            val prevProcessedCommitRevIdStr = getPrevProcessedCommitRevId()
            val prevProcessedCommitRevId = if (prevProcessedCommitRevIdStr.isBlank()) {
                val headObj = archiveRepo.resolve(HEAD)
                val headCommit = archiveRepo.parseCommit(headObj)
                val tmpWalk = RevWalk(archiveRepo)
                tmpWalk.markStart(headCommit)
                tmpWalk.last().id
            } else {
                archiveRepo.resolve(prevProcessedCommitRevIdStr)
            }
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
        if (!prevProcessedCommitRevIdFile.exists()) return EMPTY_STRING
        val rawFileStr = FileUtil.getFileContent(prevProcessedCommitRevIdFile)!!
        return rawFileStr.trim()
    }

    fun getArchiveRepo(): Repository {
        return FileRepositoryBuilder()
            .setGitDir(File(Config.BGM_ARCHIVE_GIT_REPO_DIR))
            .readEnvironment() // scan environment GIT_* variables
            .findGitDir() // scan up the file system tree
            .build()
    }

    fun getJsonRepo(): Repository {
        return FileRepositoryBuilder()
            .setGitDir(File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR))
            .readEnvironment() // scan environment GIT_* variables
            .findGitDir() // scan up the file system tree
            .build()
    }

    fun getFileContentInACommit(repo: Repository, commit: RevCommit, path: String): String {
        TreeWalk.forPath(repo, path, commit.tree).use { treeWalk ->
            val blobId: ObjectId = treeWalk.getObjectId(0)
            repo.newObjectReader().use { objectReader ->
                val objectLoader: ObjectLoader = objectReader.open(blobId)
                val bytes = objectLoader.bytes
                return String(bytes, StandardCharsets.UTF_8)
            }
        }
    }

    fun findChangedFilePaths(repo: Repository, prevCommit: RevCommit, currentCommit: RevCommit): List<String> {
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
}