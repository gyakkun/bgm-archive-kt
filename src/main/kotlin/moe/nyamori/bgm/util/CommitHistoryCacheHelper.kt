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
    private fun logTime(step:String, startTime:Long, lastStepTime:Long):Long {
        val cur = System.currentTimeMillis()
        LOGGER.info("${step} takes ${ cur-lastStepTime }ms, elapsed: ${startTime - cur}ms")
        return cur
    }
    fun Repository.buildCache() {
        val startTime = System.currentTimeMillis()
        var lastStepTime = startTime
        check(HttpHelper.DB_WRITE_LOCK.isHeldByCurrentThread)
        val jgitToggle = Config.isUseJgitInCacheBuild
        val repo = this
        // require(this.hasCouplingJsonRepo() || this.hasCouplingArchiveRepo())
        var isFreshCacheBuild = false
        val prevCachedSha1 =
            Dao.bgmDao.getPrevCachedCommitId(this) ?: run { isFreshCacheBuild = true; this.getFirstCommitIdStr(useJgit = jgitToggle) }
        lastStepTime = logTime("getPrevCachedSha1", startTime, lastStepTime)
        val latestSha1 = this.getLatestCommit(useJgit = jgitToggle).sha1
        lastStepTime = logTime("getLatestSha1", startTime, lastStepTime)
        val firstCommitSha1Getter = { this.getFirstCommitIdStr(useJgit = jgitToggle) }
        lastStepTime = logTime("getFirstCommitSha1", startTime, lastStepTime)

        var prevCachedMsg = ""
        var latestMsg = ""
        runCatching {
            prevCachedMsg = this.getGivenCommitByIdStrOrFirstCommit(prevCachedSha1, useJgit = jgitToggle).fullMessage.trim().substringBefore("\n")
            lastStepTime = logTime("getPrevCachedMsg", startTime, lastStepTime)
            latestMsg = this.getGivenCommitByIdStrOrFirstCommit(latestSha1, useJgit = jgitToggle).fullMessage.trim().substringBefore("\n")
            lastStepTime = logTime("getLatestMsg", startTime, lastStepTime)
        }

        var counter = 0
        var failedCount = 0

        this.processHistory(prevCachedSha1, latestSha1, useJgit = jgitToggle) { cur, files ->
            runCatching {
                counter++
                val curCommitId = cur.sha1
                val curCommitFullMsg = cur.fullMessage

                if (curCommitFullMsg.startsWith("META", ignoreCase = true) && curCommitId == firstCommitSha1Getter()) {
                    Dao.bgmDao.updatePrevCachedCommitId(repo, curCommitId)
                    return@processHistory
                }
                lastStepTime = logTime("updatePrevCachedCommitId", startTime, lastStepTime)

                val shouldLog = Random.nextFloat() < 0.001F || Config.logCacheDetail
                val changedFilePaths = files.filter { it.endsWith("html", ignoreCase = true) || it.endsWith("json", ignoreCase = true) }
                if (shouldLog) LOGGER.info("[CACHE] $repo - cur commit: $curCommitId , msg - $curCommitFullMsg")
                lastStepTime = logTime("changedFilePaths", startTime, lastStepTime)

                // 1. Update changed files
                val resIntArray = Dao.bgmDao.batchUpsertFileRelativePathForCache(changedFilePaths)
                if (shouldLog) LOGGER.info("[CACHE] File ids insert res int array: {}", resIntArray)
                lastStepTime = logTime("batchUpsertFileRelativePathForCache", startTime, lastStepTime)

                // 2. Insert this commit to repo commit table
                val resInt = Dao.bgmDao.insertRepoCommitForCache(repo.repoIdFromDto().toLong(), curCommitId)
                if (shouldLog) LOGGER.info("[CACHE] commit insert res int: $resInt")
                lastStepTime = logTime("insertRepoCommitForCache", startTime, lastStepTime)

                // 3. Insert to file-commit
                val cacheFileCommitInsertRes = Dao.bgmDao.batchUpsertFileCommitForCache(
                    changedFilePaths,
                    repo.repoIdFromDto().toLong(),
                    curCommitId
                )
                if (shouldLog) LOGGER.info("[CACHE] Insert result: $cacheFileCommitInsertRes")
                lastStepTime = logTime("batchUpsertFileCommitForCache", startTime, lastStepTime)

                // 4. Update meta data
                val updateMetaRes = Dao.bgmDao.updatePrevCachedCommitId(repo, curCommitId)
                if (shouldLog) LOGGER.info("[CACHE] Update meta res: $updateMetaRes")
                lastStepTime = logTime("updatePrevCachedCommitId", startTime, lastStepTime)

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
        lastStepTime = logTime("finished", startTime, lastStepTime)
    }

}