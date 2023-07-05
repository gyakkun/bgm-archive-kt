package moe.nyamori.bgm.git

import ch.qos.logback.core.CoreConstants.EMPTY_STRING
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ibm.icu.text.CharsetDetector
import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
import moe.nyamori.bgm.db.Dao
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
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.charset.StandardCharsets

object GitHelper {
    private val log = LoggerFactory.getLogger(GitHelper.javaClass)
    val GSON: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    val jsonRepoSingleton by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        getJsonRepo()
    }

    val archiveRepoSingleton by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        getArchiveRepo()
    }

    val jsonStaticRepoListSingleton by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        if (Config.BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST.isBlank()) return@lazy listOf<Repository>()
        else {
            Config.BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST.split(",")
                .map { getRepoByPath(it.trim()) }
                .toList()
        }
    }

    val archiveStaticRepoListSingleton by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        if (Config.BGM_ARCHIVE_GIT_STATIC_REPO_DIR_LIST.isBlank()) return@lazy listOf<Repository>()
        else {
            Config.BGM_ARCHIVE_GIT_STATIC_REPO_DIR_LIST.split(",")
                .map { getRepoByPath(it.trim()) }
                .toList()
        }
    }

    val allArchiveRepoListSingleton by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        mutableListOf<Repository>().apply {
            add(archiveRepoSingleton)
            addAll(archiveStaticRepoListSingleton)
        }
    }

    val allJsonRepoListSingleton by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        mutableListOf<Repository>().apply {
            add(jsonRepoSingleton)
            addAll(jsonStaticRepoListSingleton)
        }
    }

    fun Repository.getWalkBetweenPrevProcessedArchiveCommitAndLatestArchiveCommitInReverseOrder(): RevWalk {
        require(hasCouplingJsonRepo()) {
            "It should be an archive repo with coupling json repo!"
        }
        val prevProcessedArchiveCommit = getPrevProcessedArchiveCommitRef()
        val latestArchiveCommit = getLatestCommitRef()
        return this.getWalkBetweenCommitInReverseOrder(
            latestArchiveCommit,
            prevProcessedArchiveCommit,
            stepInAdvance = false
        )
    }

    fun Repository.getWalkBetweenCommitInReverseOrder(
        topCommit: RevCommit,
        bottomCommit: RevCommit,
        stepInAdvance: Boolean = true
    ): RevWalk {
        this.use {
            val walk = RevWalk(it)
            walk.markStart(topCommit)
            walk.markUninteresting(bottomCommit)
            walk.sort(RevSort.REVERSE, true) // // from bottom to top
            // the walk will include (bottom-1) commit
            // step next in advance
            if (stepInAdvance) walk.next()
            return walk
        }
    }

    fun Repository.getRevCommitById(id: String): RevCommit {
        this.use { repo ->
            val revWalk = RevWalk(repo)
            val revId = repo.resolve(id)
            val revCommit = revWalk.parseCommit(revId)
            return revCommit
        }
    }

    fun Repository.getFirstCommitIdStr(): String {
        this.use { repo ->
            val headObj = this.resolve(HEAD)
            val headCommit = repo.parseCommit(headObj)
            val tmpWalk = RevWalk(repo)
            tmpWalk.markStart(headCommit)
            return ObjectId.toString(tmpWalk.last().id)
        }
    }

    fun Repository.getGivenCommitByIdStrOrFirstCommit(commitIdStr: String): RevCommit {
        return runCatching {
            if (commitIdStr.isBlank()) throw IllegalArgumentException("Commit id should not be blank! Repo: $this")
            this.getRevCommitById(commitIdStr)
        }.onFailure {
            log.error("Error when getting commit by id in repo-$this, id-$commitIdStr", it)
        }.getOrDefault(this.getRevCommitById(this.getFirstCommitIdStr()))
    }

    fun getPrevPersistedJsonCommitRef(): RevCommit {
        return jsonRepoSingleton.getGivenCommitByIdStrOrFirstCommit(Dao.bgmDao().getPrevPersistedCommitId())
    }

    fun getPrevProcessedArchiveCommitRef(): RevCommit {
        return archiveRepoSingleton.getGivenCommitByIdStrOrFirstCommit(getPrevProcessedArchiveCommitRevIdStr())
    }

    fun getLatestArchiveCommitRef(): RevCommit {
        return archiveRepoSingleton.getLatestCommitRef()
    }

    fun Repository.getLatestCommitRef(): RevCommit {
        this.use { repo ->
            val revWalk = RevWalk(repo)
            val latestHeadCommitRevId = repo.resolve(HEAD)
            val latestHeadCommit = revWalk.parseCommit(latestHeadCommitRevId)
            return latestHeadCommit
        }
    }

    fun getPrevProcessedArchiveCommitRevIdStr(): String {
        if (jsonRepoSingleton.isBare) {
            return jsonRepoSingleton.getFileContentAsStringInACommit(
                jsonRepoSingleton.getLatestCommitRef(),
                BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
            ).trim()
        } else {
            val prevProcessedCommitRevIdFile =
                File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR).resolve(BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME)
            if (!prevProcessedCommitRevIdFile.exists()) return EMPTY_STRING
            val rawFileStr = FileUtil.getFileContent(prevProcessedCommitRevIdFile)!!
            return rawFileStr.trim()
        }
    }


    private fun getRepoByPath(path: String): Repository {
        var repo = File(path)
        if (repo.resolve(DOT_GIT).let { it.exists() && it.isDirectory }) {
            repo = repo.resolve(DOT_GIT)
        }
        return FileRepositoryBuilder()
            .setGitDir(repo)
            .readEnvironment() // scan environment GIT_* variables
            .findGitDir() // scan up the file system tree
            .build()
    }

    private fun getJsonRepo(): Repository {
        return getRepoByPath(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR)
    }

    private fun getArchiveRepo(): Repository {
        return getRepoByPath(Config.BGM_ARCHIVE_GIT_REPO_DIR)
    }

    fun Repository.getFileContentAsStringInACommit(commit: RevCommit, path: String): String {
        TreeWalk.forPath(this, path, commit.tree).use { treeWalk ->
            val blobId: ObjectId = treeWalk.getObjectId(0)
            this.newObjectReader().use { objectReader ->
                val objectLoader: ObjectLoader = objectReader.open(blobId)
                val bytes = objectLoader.bytes
                val cd = CharsetDetector()
                cd.setText(bytes)
                cd.enableInputFilter(true)
                val cm = cd.detect()
                val charsetName: String = if (cm == null) {
                    StandardCharsets.UTF_8.name()
                } else {
                    if (cm.name != StandardCharsets.UTF_8.name()) {
                        log.warn("Select charset ${cm.name} for $path at commit ${commit.shortMessage}")
                    }
                    cm.name
                }
                val selectedCharset = charset(charsetName)
                return String(bytes, selectedCharset)
            }
        }
    }

    fun Repository.findChangedFilePaths(prevCommit: RevCommit, currentCommit: RevCommit): List<String> {
        val prevTree = prevCommit.tree
        val curTree = currentCommit.tree
        val result = ArrayList<String>()
        this.newObjectReader().use { reader ->
            val oldTreeIter = CanonicalTreeParser()
            oldTreeIter.reset(reader, prevTree)
            val newTreeIter = CanonicalTreeParser()
            newTreeIter.reset(reader, curTree)
            Git(this).use { git ->
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

    fun Repository.absolutePathWithoutDotGit() =
        this.directory.path.split(File.separator).filter { it != DOT_GIT }.joinToString(File.separator)

    fun Repository.simpleName() =
        this.directory.path.split(File.separator).last { it != DOT_GIT }

    fun Repository.hasCouplingJsonRepo() = couplingJsonRepo() != null

    fun Repository.couplingJsonRepo() =
        allJsonRepoListSingleton.firstOrNull { jsonRepo ->
            absolutePathWithoutDotGit() + "-json" == jsonRepo.absolutePathWithoutDotGit()
        }

    fun Repository.hasCouplingArchiveRepo() = couplingArchiveRepo() != null

    fun Repository.couplingArchiveRepo() = allArchiveRepoListSingleton.firstOrNull { archiveRepo ->
        absolutePathWithoutDotGit() == archiveRepo.absolutePathWithoutDotGit() + "-json"
    }
}