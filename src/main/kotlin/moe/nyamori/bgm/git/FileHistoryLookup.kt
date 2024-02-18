package moe.nyamori.bgm.git

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Scheduler
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper.allArchiveRepoListSingleton
import moe.nyamori.bgm.git.GitHelper.allJsonRepoListSingleton
import moe.nyamori.bgm.git.GitHelper.folderName
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.git.GitHelper.getRevCommitById
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.GitCommitIdHelper.isJsonRepo
import moe.nyamori.bgm.util.GitCommitIdHelper.sha1Str
import moe.nyamori.bgm.util.GitCommitIdHelper.timestampHint
import moe.nyamori.bgm.util.StringHashingHelper.hashedAbsolutePathWithoutGitId
import moe.nyamori.bgm.util.blockAndPrintProcessResults
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants.DOT_GIT
import org.eclipse.jgit.lib.Repository
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.util.*


object FileHistoryLookup {
    private val log = LoggerFactory.getLogger(FileHistoryLookup::class.java)

    @JvmStatic
    fun main(args: Array<String>) {

    }

    data class CommitHashAndTimestampAndMsg(
        val repoFolder: String,
        val hash: String,
        val commitTimeEpochMs: Long,
        val authorTimeEpochMs: Long,
        val msg: String
    )

    private val repoPathToRevCommitCache: LoadingCache<Pair<Repository, String>, TreeMap<Long, CommitHashAndTimestampAndMsg>> =
        Caffeine.newBuilder()
            .maximumSize(100)
            .scheduler(Scheduler.systemScheduler())
            .expireAfterAccess(Duration.ofMinutes(12))
            .expireAfterWrite(Duration.ofMinutes(12))
            .build { (repo, relPath) ->
                getTimestampCommitMapFromRevCommitList(repo, repo.getRevCommitList(relPath))
            }
    // private val repoPathToRevCommitCache = object {
    //     fun get(p: Pair<Repository, String>): TreeMap<Long, CommitHashAndTimestampAndMsg> {
    //         val (repo, relPath) = p
    //         return getTimestampCommitMapFromRevCommitList(repo, repo.getRevCommitList(relPath))
    //     }
    // }

    fun getJsonTimestampList(relativePathToRepoFolder: String): List<Long> =
        allJsonRepoListSingleton.parallelStream().map { repo ->
            repoPathToRevCommitCache.get(repo to relativePathToRepoFolder)
                        .keys
        }.flatMap { it.stream() }.sorted().toList()

    fun getArchiveTimestampList(relativePathToRepoFolder: String): List<Long> =
        allArchiveRepoListSingleton.parallelStream().map outerMap@{ repo ->
            val interm: TreeMap<Long, CommitHashAndTimestampAndMsg> =
                repoPathToRevCommitCache.get(repo to relativePathToRepoFolder)
            if (repo.isJsonRepo()) return@outerMap interm.keys
            return@outerMap runCatching {
                return@outerMap extractExactTimestampFromMetaTs(relativePathToRepoFolder, interm, repo)
            }.onFailure {
                log.error("Try to extract exact timestamp for html $relativePathToRepoFolder but got: ", it)
            }.getOrDefault(interm.keys)
            // .keys
        }.flatMap { it.stream() }.sorted().toList()

    private fun extractExactTimestampFromMetaTs(
        relativePathToRepoFolder: String,
        interm: TreeMap<Long, CommitHashAndTimestampAndMsg>,
        repo: Repository
    ): List<Long> {
        val spaceType = relativePathToRepoFolder.split("/").first()
        val jsonChatamTsHintSet = run {
            if (!relativePathToRepoFolder.endsWith("html")) null
            else {
                val jsonRelPath = relativePathToRepoFolder.replace("html", "json")
                val jsonTsCache = allJsonRepoListSingleton.map { repo ->
                    repoPathToRevCommitCache.get(repo to jsonRelPath).values.map { it.timestampHint() }
                }.flatten().toSet()
                jsonTsCache
            }
        }
        if (spaceType.uppercase() !in SpaceType.entries.map { it.name })
            throw IllegalStateException("Not a valid path for space-typed file: $relativePathToRepoFolder")
        val topicIdStr = relativePathToRepoFolder.split("/").last().split(".").first()
        if (topicIdStr.toIntOrNull() == null)
            throw IllegalStateException("Not a valid topic path: $relativePathToRepoFolder")
        val tsList = interm
            .values
            .filter { jsonChatamTsHintSet?.contains(it.timestampHint()) ?: true }
            .map innerMap@{ commitHashAndTimestampAndMsg ->
            val metaTs = repo.getFileContentAsStringInACommit(
                commitHashAndTimestampAndMsg.hash,
                "$spaceType/meta_ts.txt",
                forceJgit = true
            )
            if (metaTs.trim().isBlank()) return@innerMap commitHashAndTimestampAndMsg.timestampHint()
            val exactTs: Long = metaTs.lines()
                .filter { it.isNotBlank() }
                .map { it.split(":") }
                .filter {
                    if (it.size != 2) {
                        log.warn(
                            "Invalid meta_ts line: $repo - commit - ${
                                commitHashAndTimestampAndMsg.hash
                            } - line - $it"
                        )
                        return@filter false
                    } else return@filter true
                }
                .associate { it[0] to it[1] }
                .get(topicIdStr)?.toLongOrNull() ?: throw IllegalStateException(
                "Topic id not found in this meta_ts file: $repo - commit - ${
                    commitHashAndTimestampAndMsg.hash
                } - relative path - $relativePathToRepoFolder - type - $spaceType - topicId - $topicIdStr"
            )
            return@innerMap exactTs
        }
        return tsList
    }

    val notLogRelPathSuffix = setOf("meta_ts.txt")

    fun Repository.getRevCommitList(relativePathToRepoFolder: String): List<CommitHashAndTimestampAndMsg> =
        runCatching {
            val repoIdCommitIdList = Dao.bgmDao.queryRepoCommitForCacheByFileRelativePath(relativePathToRepoFolder)
            repoIdCommitIdList.filter { it.repoId == this.hashedAbsolutePathWithoutGitId().toLong() }
                .map { this.getRevCommitById(it.commitId) }
                .map {
                    CommitHashAndTimestampAndMsg(
                        this.folderName(),
                        it.sha1Str(),
                        it.committerIdent.whenAsInstant.toEpochMilli(),
                        it.authorIdent.whenAsInstant.toEpochMilli(),
                        it.fullMessage
                    )
                }
        }.onFailure { }.getOrNull() ?:
        runCatching {
            val timing = System.currentTimeMillis()
            val res = if (Config.BGM_ARCHIVE_PREFER_JGIT) {
                this.getRevCommitListJgit(relativePathToRepoFolder)
            } else {
                this.getRevCommitListExtGit(relativePathToRepoFolder)
            }
            if (notLogRelPathSuffix.none { relativePathToRepoFolder.endsWith(it) }) {
                val elapsed = System.currentTimeMillis() - timing
                if (elapsed >= 100) {
                    log.warn(
                        "$this ${
                            if (Config.BGM_ARCHIVE_PREFER_JGIT) "jgit" else "external git"
                        } get log timing: ${elapsed}ms. RelPath: $relativePathToRepoFolder"
                    )
                }
            }
            return res
        }.onFailure {
            log.error(
                "$this Failed to get rev commit list at $relativePathToRepoFolder by calling external git process: ",
                it
            )
        }.getOrElse {
            log.warn("Fall back to jgit get commit history for $relativePathToRepoFolder at $this")
            return this.getRevCommitListJgit(relativePathToRepoFolder)
        }

    private fun Repository.getRevCommitListJgit(relativePathToRepoFolder: String): List<CommitHashAndTimestampAndMsg> {
        return Git(this).use { git ->
            val commitList = git.log()
                .addPath(relativePathToRepoFolder)
                .call()
            commitList.map {
                CommitHashAndTimestampAndMsg(
                    this.folderName(),
                    it.sha1Str(),
                    it.committerIdent.whenAsInstant.toEpochMilli(),
                    it.authorIdent.whenAsInstant.toEpochMilli(),
                    it.fullMessage
                )
            }
        }
    }

    private fun Repository.getRevCommitListExtGit(relativePathToRepoFolder: String): List<CommitHashAndTimestampAndMsg> {
        var gitRepoDir = this.directory
        if (gitRepoDir.isFile) throw IllegalStateException("Git repo directory should not be a file!")
        if (gitRepoDir.name == DOT_GIT) {
            log.debug(
                "{} is a bare repository?={}. Locating parent work tree folder: {}",
                this,
                this.isBare,
                gitRepoDir.parentFile
            )
            gitRepoDir = gitRepoDir.parentFile
        } else {
            log.warn("$this is a bare repository. Will use it as-is to find commit list to a file ")
        }
        val cmd = "git --no-pager log --pretty=\\\"%H|%ct|%at|%s\\\" -- $relativePathToRepoFolder"
        val gitProcess = Runtime.getRuntime()
            .exec(cmd, null, gitRepoDir)
        val cmdOut =
            gitProcess.blockAndPrintProcessResults(cmd = cmd, toLines = true, printAtStdErr = false, logCmd = false)
        return cmdOut
            .map { it.replace("\\", "").replace("\"", "") }
            .map {
                val firstVerticalBarIdx = it.indexOfFirst { it == '|' }
                if (firstVerticalBarIdx < 0) {
                    log.error("No vertical bar (|) found in prettied msg: $it")
                    return@map null
                }
                val secVerticalBarIdx = it.indexOf('|', firstVerticalBarIdx + 1)
                if (secVerticalBarIdx < 0) {
                    log.error("The second vertical bar (|) not found in prettied msg: $it")
                    return@map null
                }
                // Sec
                val commitTimeEpochSec =
                    it.substring(firstVerticalBarIdx + 1, secVerticalBarIdx).toLongOrNull() ?: return@map null
                val thirdVerticalBarIdx = it.indexOf('|', secVerticalBarIdx + 1)
                if (thirdVerticalBarIdx < 0) {
                    log.error("The third vertical bar (|) not found in prettied msg: $it")
                    return@map null
                }
                val authorTimeEpochSec =
                    it.substring(secVerticalBarIdx + 1, thirdVerticalBarIdx).toLongOrNull() ?: return@map null
                CommitHashAndTimestampAndMsg(
                    this.folderName(),
                    it.substring(0, firstVerticalBarIdx),
                    commitTimeEpochSec * 1000,
                    authorTimeEpochSec * 1000,
                    it.substring(thirdVerticalBarIdx + 1)
                )
            }.filterNotNull()
    }


    private fun getTimestampCommitMapFromRevCommitList(
        repo: Repository,
        revCommitIdList: List<CommitHashAndTimestampAndMsg>
    ): TreeMap<Long, CommitHashAndTimestampAndMsg/*commit id*/> {
        val result = TreeMap<Long, CommitHashAndTimestampAndMsg>()
        revCommitIdList.forEach {
            if (it.msg.startsWith("META") || it.msg.startsWith("init")) return@forEach
            val ts = it.msg.split("|").last().trim().toLongOrNull() ?: return@forEach
            result[ts] = it
        }
        // Workaround for historical files added in META commit
        if (result.isEmpty() && revCommitIdList.isNotEmpty()) {
            result[revCommitIdList.first().timestampHint()] = revCommitIdList.first()
        }
        return result
    }

    fun getCommitAtTimestampByPath(
        repo: Repository,
        relativePathToRepoFolder: String,
        timestamp: Long
    ): CommitHashAndTimestampAndMsg {
        val m: Map<Long, CommitHashAndTimestampAndMsg> =
            repoPathToRevCommitCache.get(repo to relativePathToRepoFolder)
        if (!m.containsKey(timestamp)) throw IllegalArgumentException("Timestamp $timestamp not in commit history")
        return m[timestamp]!!
    }

    fun getArchiveFileContentAsStringAtTimestamp(timestamp: Long, relativePath: String): String {
        // Align with json timestamp
        allArchiveRepoListSingleton.forEach { repo ->
            val timestampRevCommitTreeMap = repoPathToRevCommitCache.get(repo to relativePath)
            if (timestampRevCommitTreeMap[timestamp] != null) {
                return repo.getFileContentAsStringInACommit(timestampRevCommitTreeMap[timestamp]!!.hash, relativePath)
            }
        }

        // Here probably the given timestamp is from a /link page, where the timestamp is an **exact** one
        // extracted from meta_ts.txt
        // For a same scrapy, Json timestamp is always larger than the html exact timestamp
        // So we use `ceilingEntry` here to determine the most near json timestamp by a given exact html timestamp
        return allArchiveRepoListSingleton.mapNotNull { repo ->
            val timestampRevCommitTreeMap = repoPathToRevCommitCache.get(repo to relativePath)
            val ent = timestampRevCommitTreeMap.ceilingEntry(timestamp)
            if (ent == null) null
            else ent.value to repo
        }
            .minByOrNull { (chatam, _) -> chatam.timestampHint() - timestamp }
            ?.let { (chatam, repo) ->
                val diffMs = chatam.timestampHint() - timestamp
                val diffSec = diffMs / 1000
                log.info(
                    "Queried timestamp of $relativePath is ${
                        Timestamp(timestamp / 1000 * 1000).toInstant()
                    }, res is ${
                        Timestamp(chatam.timestampHint() / 1000 * 1000).toInstant()
                    } at [${chatam.repoFolder}@${
                        chatam.hash.substring(
                            0,
                            8
                        )
                    }], diff(res-q) = ${diffSec / 60}m${diffSec % 60}s"
                )
                repo.getFileContentAsStringInACommit(chatam.hash, relativePath)
            }
            ?:
        throw IllegalStateException(
            "Should get file content for $relativePath at timestamp=$timestamp(${
                Timestamp(
                    timestamp
                ).toInstant()
            }) in archive and static repos but got nothing!"
        )
    }

    fun getJsonFileContentAsStringAtTimestamp(timestamp: Long, relativePath: String): String {
        allJsonRepoListSingleton.forEach { repo ->
            val timestampRevCommitMap = repoPathToRevCommitCache.get(repo to relativePath)
            if (timestampRevCommitMap[timestamp] != null) {
                return repo.getFileContentAsStringInACommit(timestampRevCommitMap[timestamp]!!.hash, relativePath)
            }
        }

        throw IllegalStateException(
            "Should get file content for $relativePath at timestamp=$timestamp(${
                Timestamp(
                    timestamp
                ).toInstant()
            }) in json and static repos but got nothing!"
        )
    }
}