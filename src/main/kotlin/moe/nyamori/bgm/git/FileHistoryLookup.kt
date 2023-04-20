package moe.nyamori.bgm.git

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import java.lang.IllegalArgumentException
import java.sql.Timestamp
import java.time.Duration
import java.util.*
import java.util.stream.Stream
import kotlin.collections.ArrayList


object FileHistoryLookup {
    @JvmStatic
    fun main(args: Array<String>) {

    }

    private val repoPathToRevCommitCache: LoadingCache<Pair<Repository, String>, Map<Long, RevCommit>> =
        Caffeine.newBuilder()
            .maximumSize(6)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build { key ->
                getTimestampCommitMapFromRevCommitList(key.first.getRevCommitList(key.second))
            }


    fun getJsonTimestampList(relativePathToRepoFolder: String): List<Long> {
        return Stream.concat(
            repoPathToRevCommitCache.get(Pair(GitHelper.jsonRepoSingleton, relativePathToRepoFolder)).keys.stream(),
            GitHelper.jsonStaticRepoListSingleton.map {
                repoPathToRevCommitCache.get(Pair(it, relativePathToRepoFolder))
                    .keys
            }.flatten().stream()
        ).toList()
    }

    fun getArchiveTimestampList(relativePathToRepoFolder: String): List<Long> {
        return Stream.concat(
            repoPathToRevCommitCache.get(Pair(GitHelper.archiveRepoSingleton, relativePathToRepoFolder)).keys.stream(),
            GitHelper.archiveStaticRepoListSingleton.map {
                repoPathToRevCommitCache.get(Pair(it, relativePathToRepoFolder))
                    .keys
            }.flatten().stream()
        ).toList()
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

    fun getArchiveFileContentAsStringAtTimestamp(timestamp: Long, relativePath: String): String {
        val repoList = run {
            val result = ArrayList<Repository>()
            result.add(GitHelper.archiveRepoSingleton)
            result.addAll(GitHelper.archiveStaticRepoListSingleton)
            return@run result
        }

        repoList.forEach {
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
        val repoList = run {
            val result = ArrayList<Repository>()
            result.add(GitHelper.jsonRepoSingleton)
            result.addAll(GitHelper.jsonStaticRepoListSingleton)
            return@run result
        }

        repoList.forEach {
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