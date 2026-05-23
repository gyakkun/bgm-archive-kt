package moe.nyamori.bgm.git

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ibm.icu.text.CharsetDetector
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.prevProcessedCommitRevIdFileName
import moe.nyamori.bgm.config.RepoType
import moe.nyamori.bgm.config.getCouplingArchiveRepo
import moe.nyamori.bgm.config.getCouplingJsonRepo
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.util.GitCommitIdHelper.timestampHint
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.nanoseconds

data class GitCommitDto(val sha1: String, val fullMessage: String) {
    val shortMessage: String get() = fullMessage.lines().firstOrNull() ?: ""
}


interface IJsonRepoListProvider {
    fun get(): List<Repository>
}

object GitHelper {
    private val log = LoggerFactory.getLogger(GitHelper.javaClass)
    val GSON: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    val allArchiveRepoListSingleton: List<Repository> get() = Config.repoList.filter {
        it.type == RepoType.HTML
    }.map { it.repo }

    private val defaultProvider = object : IJsonRepoListProvider {
        override fun get(): List<Repository> = Config.repoList.filter {
            it.type == RepoType.JSON
        }.map { it.repo }
    }

    private var provider: IJsonRepoListProvider = defaultProvider

    // For test only
    fun setTestProvider(testProvider: IJsonRepoListProvider) {
        provider = testProvider
    }

    // For test only
    fun resetProvider() {
        provider = defaultProvider
    }

    val allJsonRepoListSingleton: List<Repository>
        get() = provider.get()


    val allRepoInDisplayOrder by lazy {
        allArchiveRepoListSingleton
            .sortedBy { -1L * it.getCommitById(it.getLastCommitSha1StrExtGit()).timestampHint() }
            .filter { it.hasCouplingJsonRepo() }
            .map { listOf(it, it.couplingJsonRepo()!!) }
            .flatten()
            .let { cps ->
                cps + (allArchiveRepoListSingleton + allJsonRepoListSingleton).filter { it !in cps }
            }
    }

    fun Repository.getWalkBetweenPrevProcessedArchiveCommitAndLatestArchiveCommitInReverseOrder(): RevWalk {
        require(hasCouplingJsonRepo()) {
            "It should be an archive repo with coupling json repo!"
        }
        val prevProcessedCommit = getPrevProcessedArchiveCommit()
        val latestArchiveCommit = getLatestCommit()
        return this.getWalkBetweenCommitInReverseOrder(
            latestArchiveCommit,
            prevProcessedCommit,
            stepInAdvance = false
        )
    }

    /**
     * @param stepInAdvance Walk from top to bottom are inclusive
     */
    fun Repository.getWalkBetweenCommitInReverseOrder(
        topCommit: IGitCommit,
        bottomCommit: IGitCommit,
        stepInAdvance: Boolean = true
    ): RevWalk {
        this.let {
            val walk = RevWalk(it)
            walk.markStart(it.parseCommit(org.eclipse.jgit.lib.ObjectId.fromString(topCommit.sha1)))
            walk.markUninteresting(it.parseCommit(org.eclipse.jgit.lib.ObjectId.fromString(bottomCommit.sha1)))
            walk.sort(RevSort.REVERSE, true) // // from bottom to top
            // the walk will include (bottom-1) commit
            // step next in advance
            if (stepInAdvance) walk.next()
            return walk
        }
    }

    fun Repository.getCommitById(id: String, useJgit: Boolean = Config.preferJgit): IGitCommit {
        if (useJgit) {
            return JGitCommitAdapter(this.parseCommit(ObjectId.fromString(id)))
        }
        val cmd = arrayOf("git", "--no-pager", "log", "-1", "--format=%H%x00%ct%x00%at%x00%B", id)
        val gitProcess = ProcessBuilder(*cmd)
            .directory(File(this.absolutePathWithoutDotGit()))
            .start()
        val output = gitProcess.inputStream.bufferedReader().readText()
        if (gitProcess.waitFor() != 0) {
            val err = gitProcess.errorStream.bufferedReader().readText()
            throw IllegalStateException("Failed to get commit by id $id: $err")
        }
        val parts = output.split('\u0000', limit = 4)
        if (parts.size != 4) throw IllegalStateException("Unexpected git log output for commit $id: $output")
        val sha1 = parts[0]
        val commitTimeEpochMs = parts[1].trim().toLong() * 1000L
        val authorTimeEpochMs = parts[2].trim().toLong() * 1000L
        val fullMessage = parts[3].trimEnd('\n')
        return GitExtCommit(sha1, fullMessage, commitTimeEpochMs, authorTimeEpochMs)
    }

    fun Repository.getFirstCommitIdStr(useJgit: Boolean = Config.preferJgit): String {
        if (!useJgit) return getFirstCommitIdStrExt()
        this.let { repo ->
            val headObj = this.resolve(HEAD)
            val headCommit = repo.parseCommit(headObj)
            val tmpWalk = RevWalk(repo)
            tmpWalk.markStart(headCommit)
            return JGitCommitAdapter(tmpWalk.last()).sha1
        }
    }

    fun Repository.getFirstCommitIdStrExt(): String {
        val cmd = "git rev-list --max-parents=0 HEAD"
        val gitProcess = Runtime.getRuntime().exec(cmd, null, File(this.absolutePathWithoutDotGit()))
        val msgList = gitProcess.blockAndPrintProcessResults(cmd = cmd, toLines = true, printAtStdErr = false, logCmd = false)
        return msgList.lastOrNull { it.isNotBlank() }?.trim() ?: throw IllegalStateException("Cannot find first commit id")
    }

    fun Repository.getGivenCommitByIdStrOrFirstCommit(commitIdStr: String, useJgit: Boolean = Config.preferJgit): IGitCommit {
        return runCatching {
            if (commitIdStr.isBlank()) throw IllegalArgumentException("Commit id should not be blank! Repo: $this")
            this.getCommitById(commitIdStr, useJgit)
        }.onFailure {
            log.error("Error when getting commit by id in repo-$this, id-$commitIdStr", it)
        }.getOrDefault(this.getCommitById(this.getFirstCommitIdStr(useJgit), useJgit))
    }

    fun Repository.getGivenCommitIdStrOrFirstCommit(commitIdStr: String, useJgit: Boolean = Config.preferJgit): String {
        if (commitIdStr.isBlank()) return this.getFirstCommitIdStr(useJgit)
        // If native git, we just return the str (assuming it's valid). We could validate it with `git cat-file -t` but we trust the DB mostly.
        if (!useJgit) return commitIdStr
        return this.getGivenCommitByIdStrOrFirstCommit(commitIdStr, useJgit).sha1
    }

    fun getPrevPersistedJsonCommit(jsonRepo: Repository, useJgit: Boolean = Config.preferJgit): IGitCommit {
        return jsonRepo.getGivenCommitByIdStrOrFirstCommit(Dao.bgmDao.getPrevPersistedCommitId(jsonRepo), useJgit)
    }

    fun getPrevPersistedJsonCommitSha1Str(jsonRepo: Repository, useJgit: Boolean = Config.preferJgit): String {
        return jsonRepo.getGivenCommitIdStrOrFirstCommit(Dao.bgmDao.getPrevPersistedCommitId(jsonRepo), useJgit)
    }

    fun Repository.getPrevProcessedArchiveCommit(useJgit: Boolean = Config.preferJgit): IGitCommit {
        require(this.hasCouplingJsonRepo())
        return getGivenCommitByIdStrOrFirstCommit(
            getPrevProcessedArchiveCommitRevIdStr(couplingJsonRepo()!!),
            useJgit
        )
    }

    fun Repository.getPrevProcessedArchiveCommitSha1Str(useJgit: Boolean = Config.preferJgit): String {
        require(this.hasCouplingJsonRepo())
        return getGivenCommitIdStrOrFirstCommit(
            getPrevProcessedArchiveCommitRevIdStr(couplingJsonRepo()!!),
            useJgit
        )
    }

    fun Repository.getLatestCommit(useJgit: Boolean = Config.preferJgit): IGitCommit {
        this.let { repo ->
            val latestHeadCommitRevId = repo.resolve(HEAD).name
            return repo.getCommitById(latestHeadCommitRevId, useJgit)
        }
    }

    fun Repository.getLatestCommitSha1StrExt(): String {
        return this.getLastCommitSha1StrExtGit()
    }

    fun getPrevProcessedArchiveCommitRevIdStr(jsonRepo: Repository): String {
        if (jsonRepo.isBare) {
            return jsonRepo.getFileContentAsStringInACommit(
                jsonRepo.getLatestCommit().sha1,
                prevProcessedCommitRevIdFileName
            ).trim()
        } else {
            return runCatching {
                jsonRepo.getFileContentAsStringInACommit(
                    jsonRepo.getLastCommitSha1StrExtGit(),
                    prevProcessedCommitRevIdFileName
                ).trim()
            }.onFailure {
                log.error("Failed to get last commit sha1 str using ext git: ", it)
            }.getOrElse {
                val prevProcessedCommitRevIdFile =
                    File(jsonRepo.absolutePathWithoutDotGit()).resolve(
                        prevProcessedCommitRevIdFileName
                    )
                if (!prevProcessedCommitRevIdFile.exists()) return@getOrElse ""
                val rawFileStr = prevProcessedCommitRevIdFile.readText(Charsets.UTF_8)
                return@getOrElse rawFileStr.trim()
            }
        }
    }

    private val pathRepoMap = ConcurrentHashMap<String, Repository>()

    // For test only
    fun clearRepoCache() {
        pathRepoMap.values.forEach { runCatching { it.close() } }
        pathRepoMap.clear()
    }

    fun getRepoByPath(path: String): Repository {
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

    val notLogRelPathSuffix = setOf("meta_ts.txt")

    fun Repository.getFileContentAsStringInACommit(
        commitId: String,
        relPath: String,
        useJgit: Boolean = Config.preferJgit
    ): String = runCatching {
        val timing = System.currentTimeMillis()
        val res = if (useJgit) {
            this.getFileContentAsStringInACommitJgit(commitId, relPath)
        } else {
            this.getFileContentAsStringInACommitExtGit(commitId, relPath)
        }
        if (notLogRelPathSuffix.none { relPath.endsWith(it) }) {
            val elapsed = System.currentTimeMillis() - timing
            if (elapsed >= 100) {
                log.warn(
                    "$this ${
                        if (Config.preferJgit) "jgit" else "external git"
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
    ): String = this.let outerUse@{ repo ->
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
                        if (cm.name != StandardCharsets.UTF_8.name()
                            && cm.name != "ISO-8859-2"
                            && cm.name != "ISO-8859-1"
                            && cm.name != "Shift_JIS"
                        ) {
                            log.warn("Select charset ${cm.name} for $relPath at commit ${revCommit.shortMessage}")
                        }
                        cm.name
                    }
                    val selectedCharset = charset(charsetName)
                    return@outerUse String(bytes, selectedCharset)
                }
            }
    }

    fun Repository.getLastCommitSha1StrExtGit(): String {
        val cmd = "git rev-parse HEAD"
        val gitProcess = Runtime.getRuntime()
            .exec(cmd, null, File(this.absolutePathWithoutDotGit()))
        val msgList =
            gitProcess.blockAndPrintProcessResults(cmd = cmd, toLines = false, printAtStdErr = false, logCmd = false)
        return msgList.filter { it.isNotBlank() }.joinToString(separator = "").trim()
    }

    private fun Repository.getFileContentAsStringInACommitExtGit(
        commitId: String,
        relativePathToRepoFolder: String
    ): String {
        val cmd = "git --no-pager show $commitId:$relativePathToRepoFolder"
        val gitProcess = Runtime.getRuntime()
            .exec(cmd, null, File(this.absolutePathWithoutDotGit()))
        val msgList = gitProcess.blockAndPrintProcessResults(cmd = cmd, toLines = false, printAtStdErr = false, logCmd = false)
        if (gitProcess.exitValue() != 0) {
            // JGit returns empty string if not found, we should do the same to maintain parity
            if (msgList.joinToString("\n").contains("does not exist in")) {
                return ""
            }
            throw RuntimeException("git show failed: ${msgList.joinToString("\n")}")
        }
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

    fun Repository.processHistory(
        prevProcessedSha1: String,
        latestSha1: String,
        useJgit: Boolean = Config.preferJgit,
        action: (commit: GitCommitDto, changedFiles: List<String>) -> Unit
    ) {
        if (useJgit) {
            val topCommit = this.getCommitById(latestSha1)
            val bottomCommit = this.getCommitById(prevProcessedSha1)
            val walk = this.getWalkBetweenCommitInReverseOrder(topCommit, bottomCommit, false)
            var prev = walk.next()
            while (prev != null) {
                if (bottomCommit.sha1 == prev.name) {
                    break
                }
                prev = walk.next()
            }
            walk.forEach { cur ->
                if (cur == prev) {
                    log.warn("Commit ${JGitCommitAdapter(cur).sha1} has been iterated twice! Repo: ${this.simpleName()}")
                    return@forEach
                }
                val files = this.findChangedFilePaths(prev, cur)
                action(GitCommitDto(JGitCommitAdapter(cur).sha1, cur.fullMessage), files)
                prev = cur
            }
        } else {
            val cmd = "git log --reverse --name-status --format=format:COMMIT_START%n%H%n%B%nFILES_START $prevProcessedSha1..$latestSha1"
            val process = Runtime.getRuntime().exec(cmd, null, File(this.absolutePathWithoutDotGit()))
            process.inputStream.bufferedReader().use { reader ->
                var line: String? = reader.readLine()
                var curSha1: String? = null
                val curMessage = StringBuilder()
                val curFiles = mutableListOf<String>()
                var state = 0 // 0: wait COMMIT_START, 1: read sha1, 2: read message, 3: read files
                while (line != null) {
                    when (state) {
                        0 -> {
                            if (line == "COMMIT_START") {
                                state = 1
                            }
                        }
                        1 -> {
                            curSha1 = line
                            state = 2
                        }
                        2 -> {
                            if (line == "FILES_START") {
                                state = 3
                            } else {
                                curMessage.append(line).append("\n")
                            }
                        }
                        3 -> {
                            if (line == "COMMIT_START") {
                                // Flush previous commit
                                action(GitCommitDto(curSha1!!, curMessage.toString().trimEnd()), curFiles.toList())
                                // Start new commit
                                curSha1 = null
                                curMessage.clear()
                                curFiles.clear()
                                state = 1
                            } else if (line.isNotBlank()) {
                                val parts = line.split("\t")
                                if (parts.size >= 2) {
                                    val status = parts[0]
                                    val path = parts[parts.size - 1]
                                    if (!status.startsWith("D")) {
                                        curFiles.add(path)
                                    }
                                }
                            }
                        }
                    }
                    line = reader.readLine()
                }
                if (state == 3 && curSha1 != null) {
                    action(GitCommitDto(curSha1, curMessage.toString().trimEnd()), curFiles.toList())
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val err = process.errorStream.bufferedReader().readText()
                log.error("git log failed with exit code $exitCode: $err")
                throw RuntimeException("git log failed: $err")
            }
        }
    }

    fun Repository.absolutePathWithoutDotGit() =
        this.directory.path.split(File.separator).filter { it != DOT_GIT }.joinToString(File.separator)

    fun Repository.folderName() = this.absolutePathWithoutDotGit().split(File.separator).last()

    fun Repository.simpleName() =
        this.directory.path.split(File.separator).last { it != DOT_GIT }

    fun Repository.hasCouplingJsonRepo() = couplingJsonRepo() != null

    fun Repository.couplingJsonRepo() = getCouplingJsonRepo()

    fun Repository.hasCouplingArchiveRepo() = couplingArchiveRepo() != null

    fun Repository.couplingArchiveRepo() = getCouplingArchiveRepo()

    fun Repository.hasCouplingRepo() = hasCouplingArchiveRepo() || hasCouplingJsonRepo()
}