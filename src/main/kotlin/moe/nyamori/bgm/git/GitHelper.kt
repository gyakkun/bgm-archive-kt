package moe.nyamori.bgm.git

import ch.qos.logback.core.CoreConstants.EMPTY_STRING
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ibm.icu.text.CharsetDetector
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.util.GitCommitIdHelper.sha1Str
import moe.nyamori.bgm.util.blockAndPrintProcessResults
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
import java.nio.charset.StandardCharsets

object GitHelper {
    private val log = LoggerFactory.getLogger(GitHelper.javaClass)
    val GSON: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    val defaultJsonRepoSingleton by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        getDefaultJsonRepo()
    }

    val defaultArchiveRepoSingleton by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        getDefaultArchiveRepo()
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
            add(defaultArchiveRepoSingleton)
            addAll(archiveStaticRepoListSingleton)
        }
    }

    val allJsonRepoListSingleton by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        mutableListOf<Repository>().apply {
            add(defaultJsonRepoSingleton)
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

    /**
     * @param stepInAdvance Walk from top to bottom are inclusive
     */
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
            // val revWalk = RevWalk(repo)
            // val revId = repo.resolve(id)
            // val revCommit = revWalk.parseCommit(revId)
            return repo.parseCommit(ObjectId.fromString(id))
        }
    }

    fun Repository.getFirstCommitIdStr(): String {
        this.use { repo ->
            val headObj = this.resolve(HEAD)
            val headCommit = repo.parseCommit(headObj)
            val tmpWalk = RevWalk(repo)
            tmpWalk.markStart(headCommit)
            return tmpWalk.last().sha1Str()
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

    fun getPrevPersistedJsonCommitRef(jsonRepo: Repository): RevCommit {
        return jsonRepo.getGivenCommitByIdStrOrFirstCommit(Dao.bgmDao.getPrevPersistedCommitId(jsonRepo))
    }

    fun Repository.getPrevProcessedArchiveCommitRef(): RevCommit {
        require(this.hasCouplingJsonRepo())
        return getGivenCommitByIdStrOrFirstCommit(
            getPrevProcessedArchiveCommitRevIdStr(couplingJsonRepo()!!)
        )
    }


    fun Repository.getLatestCommitRef(): RevCommit {
        this.use { repo ->
            val revWalk = RevWalk(repo)
            val latestHeadCommitRevId = repo.resolve(HEAD)
            val latestHeadCommit = revWalk.parseCommit(latestHeadCommitRevId)
            return latestHeadCommit
        }
    }

    fun getPrevProcessedArchiveCommitRevIdStr(jsonRepo: Repository): String {
        if (jsonRepo.isBare) {
            return jsonRepo.getFileContentAsStringInACommit(
                jsonRepo.getLatestCommitRef().sha1Str(),
                BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME
            ).trim()
        } else {
            val prevProcessedCommitRevIdFile =
                File(jsonRepo.absolutePathWithoutDotGit()).resolve(BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME)
            if (!prevProcessedCommitRevIdFile.exists()) return EMPTY_STRING
            val rawFileStr = prevProcessedCommitRevIdFile.readText(Charsets.UTF_8)
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

    private fun getDefaultJsonRepo(): Repository {
        return getRepoByPath(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR)
    }

    private fun getDefaultArchiveRepo(): Repository {
        return getRepoByPath(Config.BGM_ARCHIVE_GIT_REPO_DIR)
    }

    val notLogRelPathSuffix = setOf("meta_ts.txt")

    fun Repository.getFileContentAsStringInACommit(
        commitId: String,
        relPath: String,
        forceJgit: Boolean = false
    ): String = runCatching {
        val timing = System.currentTimeMillis()
        val res = if (Config.BGM_ARCHIVE_PREFER_JGIT || forceJgit) {
            this.getFileContentAsStringInACommitJgit(commitId, relPath)
        } else {
            this.getFileContentAsStringInACommitExtGit(commitId, relPath)
        }
        if (notLogRelPathSuffix.none { relPath.endsWith(it) }) {
            val elapsed = System.currentTimeMillis() - timing
            if (elapsed >= 100) {
                log.warn(
                    "$this ${
                        if (Config.BGM_ARCHIVE_PREFER_JGIT) "jgit" else "external git"
                    } get file content: ${elapsed}ms. RelPath: $relPath"
                )
            }
        }
        return res
    }.onFailure {
        log.error("$this Failed to get file content as string at $relPath: ", it)
    }.getOrElse {
        return this.getFileContentAsStringInACommitJgit(commitId, relPath)
    }

    private fun Repository.getFileContentAsStringInACommitJgit(
        commitId: String,
        relPath: String
    ): String = this.use outerUse@{ repo ->
        val revCommit = runCatching { repo.parseCommit(ObjectId.fromString(commitId)) }
            .onFailure {
                log.error(
                    "Seems the commit id not exist. repo=${repo.folderName()}, commit id=$commitId : ",
                    it
                )
            }
            .getOrNull()
        if (revCommit == null) {
            log.error("Failed to parse commit id: $commitId at $repo")
            return@outerUse ""
        }
        runCatching { TreeWalk.forPath(repo, relPath, revCommit.tree) }
            .onFailure {
                log.error(
                    "Looks like nothing found in commit for this path.Repo=${
                        repo.folderName()
                    }, commit=$commitId, path=$relPath"
                )
            }
            .getOrNull()
            .use innerUse1@{ treeWalk ->
                if (treeWalk == null) {
                    if (notLogRelPathSuffix.none { relPath.endsWith(it) }) {
                        log.error("null tree walk for $this - commit - $commitId - path - $relPath")
                    }
                    return@outerUse ""
                }
                val blobId: ObjectId = treeWalk.getObjectId(0) ?: return@outerUse ""
                repo.newObjectReader().use innerUse2@{ objectReader ->
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
                            log.warn("Select charset ${cm.name} for $relPath at commit ${revCommit.shortMessage}")
                        }
                        cm.name
                    }
                    val selectedCharset = charset(charsetName)
                    return@outerUse String(bytes, selectedCharset)
                }
            }
    }

    private fun Repository.getFileContentAsStringInACommitExtGit(
        commitId: String,
        relativePathToRepoFolder: String
    ): String {
        val cmd = "git --no-pager show $commitId:$relativePathToRepoFolder"
        val gitProcess = Runtime.getRuntime()
            .exec(cmd, null, File(this.absolutePathWithoutDotGit()))
        val msgList = gitProcess.blockAndPrintProcessResults(cmd = cmd, toLines = false, printAtStdErr = false, logCmd = false)
        if (msgList.size > 2) log.info("msgListLen = ${msgList.size}")
        if (msgList.last().isBlank()) return msgList.take(msgList.size - 1).joinToString("\n")
        return msgList.joinToString("\n")
    }


    fun Repository.findChangedFilePaths(prevCommit: RevCommit, currentCommit: RevCommit): List<String> {
        val prevTree = prevCommit.tree
        val curTree = currentCommit.tree
        val result = mutableListOf<String>()
        this.newObjectReader().use { reader ->
            val oldTreeIter = CanonicalTreeParser()
            oldTreeIter.reset(reader, prevTree)
            val newTreeIter = CanonicalTreeParser()
            newTreeIter.reset(reader, curTree)
            Git(this).use { git ->
                git.diff()
                    .setNewTree(newTreeIter)
                    .setOldTree(oldTreeIter)
                    .setShowNameOnly(true)
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

    fun Repository.folderName() = this.absolutePathWithoutDotGit().split(File.separator).last()

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

    fun Repository.hasCouplingRepo() = hasCouplingArchiveRepo() || hasCouplingJsonRepo()
}