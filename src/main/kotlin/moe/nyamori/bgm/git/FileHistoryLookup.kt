package moe.nyamori.bgm.git

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import kotlinx.coroutines.*
import moe.nyamori.bgm.git.GitHelper.allArchiveRepoListSingleton
import moe.nyamori.bgm.git.GitHelper.allJsonRepoListSingleton
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.util.blockAndPrintProcessResults
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants.DOT_GIT
import org.eclipse.jgit.lib.ObjectId
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

    data class CommitHashAndTimestampAndMsg(val hash: String, val timestamp: Long, val msg: String)

    private val repoPathToRevCommitCache: LoadingCache<Pair<Repository, String>, Map<Long, CommitHashAndTimestampAndMsg>> =
        Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build { (repo, relPath) ->
                getTimestampCommitMapFromRevCommitList(repo.getRevCommitList(relPath))
            }


    fun getJsonTimestampList(relativePathToRepoFolder: String): List<Long> = runBlocking {
        withContext(Dispatchers.IO) {
            allJsonRepoListSingleton.map {
                async {
                    repoPathToRevCommitCache.get(Pair(it, relativePathToRepoFolder))
                        .keys
                }
            }.awaitAll().flatten().sorted()
        }
    }

    fun getArchiveTimestampList(relativePathToRepoFolder: String): List<Long> = runBlocking {
        withContext(Dispatchers.IO) {
            allArchiveRepoListSingleton.map {
                async {
                    repoPathToRevCommitCache.get(Pair(it, relativePathToRepoFolder))
                        .keys
                }
            }.awaitAll().flatten().sorted()
        }
    }

    fun Repository.getRevCommitList(relativePathToRepoFolder: String): List<CommitHashAndTimestampAndMsg> =
        runCatching {
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
            val timing = System.currentTimeMillis()
            val cmd = "git --no-pager log --pretty=\\\"%H|%ct|%s\\\" -- $relativePathToRepoFolder"
            val gitProcess = Runtime.getRuntime()
                .exec(cmd, null, gitRepoDir)
            val cmdOut = gitProcess.blockAndPrintProcessResults(cmd = cmd, toLines = true, printAtStdErr = false)
            log.info("$this External git get log timing: ${System.currentTimeMillis() - timing}ms. RelPath: $relativePathToRepoFolder")
            cmdOut
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
                    val timestamp =
                        it.substring(firstVerticalBarIdx + 1, secVerticalBarIdx).toLongOrNull() ?: return@map null
                    CommitHashAndTimestampAndMsg(
                        it.substring(0, firstVerticalBarIdx),
                        timestamp,
                        it.substring(secVerticalBarIdx + 1)
                    )
                }.filterNotNull()
        }.onFailure {
            log.error(
                "$this Failed to get rev commit list at $relativePathToRepoFolder by calling external git process: ",
                it
            )
        }.getOrElse {
            log.warn("Fall back to jgit get commit history for $relativePathToRepoFolder at $this")
            Git(this).use { git ->
                val commitList = git.log()
                    .addPath(relativePathToRepoFolder)
                    .call()
                commitList.map {
                    CommitHashAndTimestampAndMsg(
                        ObjectId.toString(it.id),
                        it.commitTime.toLong(), // Sec
                        it.fullMessage
                    )
                }
            }
        }

    private fun getTimestampCommitMapFromRevCommitList(revCommitIdList: List<CommitHashAndTimestampAndMsg>): Map<Long, CommitHashAndTimestampAndMsg/*commit id*/> {
        val result = TreeMap<Long, CommitHashAndTimestampAndMsg>()
        revCommitIdList.forEach {
            if (it.msg.startsWith("META") || it.msg.startsWith("init")) return@forEach
            val ts = it.msg.split("|").last().trim().toLongOrNull() ?: return@forEach
            result[ts] = it
        }
        // Workaround for historical files added in META commit
        if (result.isEmpty() && revCommitIdList.isNotEmpty()) {
            result[revCommitIdList.first().timestamp * 1000] = revCommitIdList.first()
        }
        return result
    }

    fun getCommitAtTimestampByPath(
        repo: Repository,
        relativePathToRepoFolder: String,
        timestamp: Long
    ): CommitHashAndTimestampAndMsg {
        val m: Map<Long, CommitHashAndTimestampAndMsg> =
            repoPathToRevCommitCache.get(Pair(repo, relativePathToRepoFolder))
        if (!m.containsKey(timestamp)) throw IllegalArgumentException("Timestamp $timestamp not in commit history")
        return m[timestamp]!!
    }

    fun getArchiveFileContentAsStringAtTimestamp(timestamp: Long, relativePath: String): String {
        allArchiveRepoListSingleton.forEach {
            val timestampRevCommitMap = repoPathToRevCommitCache.get(Pair(it, relativePath))
            if (timestampRevCommitMap[timestamp] != null) {
                return it.getFileContentAsStringInACommit(timestampRevCommitMap[timestamp]!!.hash, relativePath)
            }
        }

        throw IllegalStateException(
            "Should get file content for $relativePath at timestamp=$timestamp(${
                Timestamp(
                    timestamp
                ).toInstant()
            }) in archive and static repos but got nothing!"
        )
    }

    fun getJsonFileContentAsStringAtTimestamp(timestamp: Long, relativePath: String): String {
        allJsonRepoListSingleton.forEach {
            val timestampRevCommitMap = repoPathToRevCommitCache.get(Pair(it, relativePath))
            if (timestampRevCommitMap[timestamp] != null) {
                return it.getFileContentAsStringInACommit(timestampRevCommitMap[timestamp]!!.hash, relativePath)
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