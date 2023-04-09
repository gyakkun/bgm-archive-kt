package moe.nyamori.bgm.git

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import java.lang.IllegalArgumentException
import java.time.Duration
import java.util.*
import kotlin.collections.ArrayList


object FileHistoryLookup {
    @JvmStatic
    fun main(args: Array<String>) {

    }

    private val repoPathToRevCommitCache: LoadingCache<Pair<Repository, String>, Map<Long, RevCommit>> =
        Caffeine.newBuilder()
            .maximumSize(5)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build { key ->
                getTimestampCommitMapFromRevCommitList(getRevCommitList(key.second, key.first))
            }


    fun getJsonTimestampList(relativePathToRepoFolder: String): List<Long> {
        return repoPathToRevCommitCache.get(Pair(GitHelper.jsonRepoSingleton, relativePathToRepoFolder)).keys.toList()
    }

    fun getArchiveTimestampList(relativePathToRepoFolder: String): List<Long> {
        return repoPathToRevCommitCache.get(
            Pair(
                GitHelper.archiveRepoSingleton, relativePathToRepoFolder
            )
        ).keys.toList()
    }


    fun getRevCommitList(relativePathToRepoFolder: String, repo: Repository): List<RevCommit> {
        val result = ArrayList<RevCommit>()
        Git(repo).use { git ->
            val commitList = git.log()
                .addPath(relativePathToRepoFolder)
                .call()
            commitList.forEach { result.add(it) }
        }
        return result
    }

    private fun getTimestampCommitMapFromRevCommitList(revCommitList: List<RevCommit>): Map<Long, RevCommit> {
        val result = TreeMap<Long, RevCommit>()
        revCommitList.forEach {
            if (it.fullMessage.startsWith("META") || it.fullMessage.startsWith("init")) return@forEach
            result[it.fullMessage.split("|").last().trim().toLong()] = it
        }
        // Workaround for historical files added in META commit
        if (result.isEmpty() && revCommitList.isNotEmpty()) {
            result[revCommitList.first().commitTime.toLong() * 1000] = revCommitList.first()
        }
        return result
    }

    fun getJsonCommitAtTimestamp(relativePathToRepoFolder: String, timestamp: Long): RevCommit {
        return getCommitAtTimestamp(GitHelper.jsonRepoSingleton, relativePathToRepoFolder, timestamp)
    }

    fun getArchiveCommitAtTimestamp(relativePathToRepoFolder: String, timestamp: Long): RevCommit {
        return getCommitAtTimestamp(GitHelper.archiveRepoSingleton, relativePathToRepoFolder, timestamp)
    }

    fun getCommitAtTimestamp(repo: Repository, relativePathToRepoFolder: String, timestamp: Long): RevCommit {
        val m: Map<Long, RevCommit> = repoPathToRevCommitCache.get(Pair(repo, relativePathToRepoFolder))
        if (!m.containsKey(timestamp)) throw IllegalArgumentException("Timestamp $timestamp not in commit history")
        return m[timestamp]!!
    }
}