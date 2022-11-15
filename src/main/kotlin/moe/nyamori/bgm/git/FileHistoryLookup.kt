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

    private val jsonRepoPathToRevCommitCache: LoadingCache<String, Map<Long, RevCommit>> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofHours(3))
        .refreshAfterWrite(Duration.ofHours(3))
        .build { relativePath ->
            getTimestampCommitMapFromRevCommitList(getRevCommitList(relativePath, GitHelper.getJsonRepo()))
        }

    private val archiveRepoPathToRevCommitCache: LoadingCache<String, Map<Long, RevCommit>> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofHours(3))
        .refreshAfterWrite(Duration.ofHours(3))
        .build { relativePath ->
            getTimestampCommitMapFromRevCommitList(getRevCommitList(relativePath, GitHelper.getArchiveRepo()))
        }

    fun getJsonTimestampList(relativePathToRepoFolder: String): List<Long> {
        return jsonRepoPathToRevCommitCache.get(relativePathToRepoFolder).keys.toList()
    }


    private fun getRevCommitList(relativePathToRepoFolder: String, repo: Repository): List<RevCommit> {
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
        return result
    }

    fun getJsonCommitAtTimestamp(relativePathToRepoFolder: String, timestamp: Long): RevCommit {
        val m: Map<Long, RevCommit> = jsonRepoPathToRevCommitCache.get(relativePathToRepoFolder)
        if (!m.containsKey(timestamp)) throw IllegalArgumentException("Timestamp $timestamp not in commit history")
        return m[timestamp]!!
    }
}