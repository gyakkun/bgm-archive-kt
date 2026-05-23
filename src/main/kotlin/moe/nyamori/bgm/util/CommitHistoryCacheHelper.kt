package moe.nyamori.bgm.util

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper.getFirstCommitIdStr
import moe.nyamori.bgm.git.GitHelper.getGivenCommitByIdStrOrFirstCommit
import moe.nyamori.bgm.git.GitHelper.getLatestCommit
import moe.nyamori.bgm.git.GitHelper.processHistory
import moe.nyamori.bgm.util.StringHashingHelper.repoIdFromDto
import org.eclipse.jgit.lib.Repository
import org.slf4j.LoggerFactory
import kotlin.random.Random

object CommitHistoryCacheHelper {
    private val LOGGER = LoggerFactory.getLogger(CommitHistoryCacheHelper::class.java)
    fun Repository.buildCache() {
        check(HttpHelper.DB_WRITE_LOCK.isHeldByCurrentThread)
        val jgitToggle = Config.isUseJgitInCacheBuild
        val repo = this
        // require(this.hasCouplingJsonRepo() || this.hasCouplingArchiveRepo())
        var isFreshCacheBuild = false
        val prevCachedSha1 =
            Dao.bgmDao.getPrevCachedCommitId(this) ?: run { isFreshCacheBuild = true; this.getFirstCommitIdStr(useJgit = jgitToggle) }
        val latestSha1 = this.getLatestCommit(useJgit = jgitToggle).sha1
        val firstCommitSha1 = this.getFirstCommitIdStr(useJgit = jgitToggle)
        
        var prevCachedMsg = ""
        var latestMsg = ""
        runCatching {
            prevCachedMsg = this.getGivenCommitByIdStrOrFirstCommit(prevCachedSha1, useJgit = jgitToggle).fullMessage.trim().substringBefore("\n")
            latestMsg = this.getGivenCommitByIdStrOrFirstCommit(latestSha1, useJgit = jgitToggle).fullMessage.trim().substringBefore("\n")
        }

        var counter = 0
        var failedCount = 0

        this.processHistory(prevCachedSha1, latestSha1, useJgit = jgitToggle) { cur, files ->
            runCatching {
                counter++
                val curCommitId = cur.sha1
                val curCommitFullMsg = cur.fullMessage
                
                if (curCommitFullMsg.startsWith("META", ignoreCase = true) && curCommitId == firstCommitSha1) {
                    Dao.bgmDao.updatePrevCachedCommitId(repo, curCommitId)
                    return@processHistory
                }
                
                val shouldLog = Random.nextFloat() < 0.001F || Config.logCacheDetail
                val changedFilePaths = files.filter { it.endsWith("html", ignoreCase = true) || it.endsWith("json", ignoreCase = true) }
                if (shouldLog) LOGGER.info("[CACHE] $repo - cur commit: $curCommitId , msg - $curCommitFullMsg")

                // 1. Update changed files
                val resIntArray = Dao.bgmDao.batchUpsertFileRelativePathForCache(changedFilePaths)
                if (shouldLog) LOGGER.info("[CACHE] File ids insert res int array: {}", resIntArray)

                // 2. Insert this commit to repo commit table
                val resInt = Dao.bgmDao.insertRepoCommitForCache(repo.repoIdFromDto().toLong(), curCommitId)
                if (shouldLog) LOGGER.info("[CACHE] commit insert res int: $resInt")

                // 3. Insert to file-commit
                val cacheFileCommitInsertRes = Dao.bgmDao.batchUpsertFileCommitForCache(
                    changedFilePaths,
                    repo.repoIdFromDto().toLong(),
                    curCommitId
                )
                if (shouldLog) LOGGER.info("[CACHE] Insert result: $cacheFileCommitInsertRes")

                // 4. Update meta data
                val updateMetaRes = Dao.bgmDao.updatePrevCachedCommitId(repo, curCommitId)
                if (shouldLog) LOGGER.info("[CACHE] Update meta res: $updateMetaRes")

                if (shouldLog)
                    LOGGER.info("[CACHE] $repo build cache for commit successfully - [$curCommitId - $curCommitFullMsg]")

            }.onFailure {
                failedCount++
                LOGGER.error("Failed to build cache for $repo - commit - ${cur.sha1}", it)
            }
        }

        if (counter > 0 && failedCount == 0) {
            val sentence = """Successfully build cache for $repo from 
                [old] [$prevCachedMsg] [$prevCachedSha1]
                to
                [latest] [$latestMsg] [$latestSha1]
                Total: $counter""".trimIndent()
            LOGGER.info(sentence)
        } else if (counter > 0 && failedCount != 0) {
            val sentence = """Failed to build cache for $repo from 
                [old] [$prevCachedMsg] [$prevCachedSha1]
                to
                [latest] [$latestMsg] [$latestSha1]
                Total: $counter Failed: $failedCount
                """;
            LOGGER.error(sentence)
        }
    }

}