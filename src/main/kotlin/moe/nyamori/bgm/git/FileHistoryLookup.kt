package moe.nyamori.bgm.git

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import moe.nyamori.bgm.git.GitHelper.allArchiveRepoListSingleton
import moe.nyamori.bgm.git.GitHelper.allJsonRepoListSingleton
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import java.sql.Timestamp
import java.time.Duration
import java.util.*


object FileHistoryLookup {
    @JvmStatic
    fun main(args: Array<String>) {

    }

    private val repoPathToRevCommitCache: LoadingCache<Pair<Repository, String>, Map<Long, RevCommit>> =
        Caffeine.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build { key ->
                getTimestampCommitMapFromRevCommitList(key.first.getRevCommitList(key.second))
            }


    fun getJsonTimestampList(relativePathToRepoFolder: String): List<Long> {
        return allJsonRepoListSingleton.map {
            repoPathToRevCommitCache.get(Pair(it, relativePathToRepoFolder))
                .keys
        }.flatten().sorted()
    }

    fun getArchiveTimestampList(relativePathToRepoFolder: String): List<Long> {
        return allArchiveRepoListSingleton.map {
            repoPathToRevCommitCache.get(Pair(it, relativePathToRepoFolder))
                .keys
        }.flatten().sorted()
    }


    fun Repository.getRevCommitList(relativePathToRepoFolder: String): List<RevCommit> {
        val result = ArrayList<RevCommit>()
        Git(this).use { git ->
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
            val ts = it.fullMessage.split("|").last().trim().toLongOrNull() ?: return@forEach
            result[ts] = it
        }
        // Workaround for historical files added in META commit
        if (result.isEmpty() && revCommitList.isNotEmpty()) {
            result[revCommitList.first().commitTime.toLong() * 1000] = revCommitList.first()
        }
        return result
    }

    fun getJsonCommitAtTimestamp(relativePathToRepoFolder: String, timestamp: Long): RevCommit? {
        return allJsonRepoListSingleton.firstNotNullOfOrNull {
            runCatching { getCommitAtTimestampByPath(it, relativePathToRepoFolder, timestamp) }
                .getOrNull()
        }
    }

    fun getArchiveCommitAtTimestamp(relativePathToRepoFolder: String, timestamp: Long): RevCommit? {
        return allArchiveRepoListSingleton.firstNotNullOfOrNull {
            runCatching { getCommitAtTimestampByPath(it, relativePathToRepoFolder, timestamp) }
                .getOrNull()
        }
    }

    fun getCommitAtTimestampByPath(repo: Repository, relativePathToRepoFolder: String, timestamp: Long): RevCommit {
        val m: Map<Long, RevCommit> = repoPathToRevCommitCache.get(Pair(repo, relativePathToRepoFolder))
        if (!m.containsKey(timestamp)) throw IllegalArgumentException("Timestamp $timestamp not in commit history")
        return m[timestamp]!!
    }

    fun getArchiveFileContentAsStringAtTimestamp(timestamp: Long, relativePath: String): String {
        allArchiveRepoListSingleton.forEach {
            val timestampRevCommitMap = repoPathToRevCommitCache.get(Pair(it, relativePath))
            if (timestampRevCommitMap[timestamp] != null) {
                return it.getFileContentAsStringInACommit(timestampRevCommitMap[timestamp]!!, relativePath)
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
                return it.getFileContentAsStringInACommit(timestampRevCommitMap[timestamp]!!, relativePath)
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