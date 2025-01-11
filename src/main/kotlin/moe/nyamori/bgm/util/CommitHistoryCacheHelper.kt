package moe.nyamori.bgm.util

import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper.findChangedFilePaths
import moe.nyamori.bgm.git.GitHelper.getFirstCommitIdStr
import moe.nyamori.bgm.git.GitHelper.getLatestCommitRef
import moe.nyamori.bgm.git.GitHelper.getRevCommitById
import moe.nyamori.bgm.git.GitHelper.getWalkBetweenCommitInReverseOrder
import moe.nyamori.bgm.git.GitHelper.simpleName
import moe.nyamori.bgm.util.GitCommitIdHelper.sha1Str
import moe.nyamori.bgm.util.StringHashingHelper.repoIdFromDto
import org.eclipse.jgit.lib.Repository
import org.slf4j.LoggerFactory

object CommitHistoryCacheHelper {
    private val LOGGER = LoggerFactory.getLogger(CommitHistoryCacheHelper::class.java)
    fun Repository.buildCache() {
        check(HttpHelper.DB_WRITE_LOCK.isHeldByCurrentThread)
        val repo = this
        // require(this.hasCouplingJsonRepo() || this.hasCouplingArchiveRepo())
        var isFreshCacheBuild = false
        val prevCachedSha1 =
            Dao.bgmDao.getPrevCachedCommitId(this) ?: run { isFreshCacheBuild = true; this.getFirstCommitIdStr() }
        val latestSha1 = this.getLatestCommitRef().sha1Str()
        val firstCommitSha1 = this.getFirstCommitIdStr()
        val firstRevCommit = this.getRevCommitById(firstCommitSha1)
        val prevCachedRevCommit = this.getRevCommitById(prevCachedSha1)
        val prevCachedMsg = prevCachedRevCommit.fullMessage.trim()
        val latestRevCommit = this.getRevCommitById(latestSha1)
        val latestMsg = latestRevCommit.fullMessage.trim()
        val walk = getWalkBetweenCommitInReverseOrder(
            topCommit = latestRevCommit, bottomCommit = prevCachedRevCommit,
            stepInAdvance = false
        )

        var prev = walk.next() // used in the iteration (now = next() ... prev = now)

        // FIXME: Workaround the inclusive walk boundary
        while (prev != null) {
            if (prevCachedRevCommit == prev) {
                break
            }
            prev = walk.next()
        }
        var counter = 0
        var failedCount = 0
        run breakable@{
            walk.forEach outer@{ cur ->
                runCatching {
                    counter++
                    if (cur == prev) {
                        LOGGER.warn("Commit $cur has been iterated twice! Repo: ${this.simpleName()}")
                        return@breakable
                    }
                    val curCommitId = cur.sha1Str()
                    val curCommitFullMsg = cur.fullMessage
                    if (curCommitFullMsg.startsWith("META", ignoreCase = true) && cur == firstRevCommit) {
                        Dao.bgmDao.updatePrevCachedCommitId(this, curCommitId)
                        prev = cur
                        return@outer
                    }
                    val changedFilePaths = this.findChangedFilePaths(prev, cur)
                        .filter { it.endsWith("html", ignoreCase = true) || it.endsWith("json", ignoreCase = true) }
                    LOGGER.debug("[CACHE] $this - cur commit: $curCommitId , msg - $curCommitFullMsg")

                    // 1. Update changed files
                    val resIntArray = Dao.bgmDao.batchUpsertFileRelativePathForCache(changedFilePaths)
                    // LOGGER.info("File ids insert res int array: {}", resIntArray)

                    // 2. Insert this commit to repo commit table
                    val resInt =
                        Dao.bgmDao.insertRepoCommitForCache(this.repoIdFromDto().toLong(), curCommitId)
                    // LOGGER.info("commit insert res int: $resInt")

                    // 3. Insert to file-commit
                    val cacheFileCommitInsertRes = Dao.bgmDao.batchUpsertFileCommitForCache(
                        changedFilePaths,
                        this.repoIdFromDto().toLong(),
                        curCommitId
                    )
                    // LOGGER.info("Insert result: $cacheFileCommitInsertRes")

                    // 4. Update meta data
                    val updateMetaRes = Dao.bgmDao.updatePrevCachedCommitId(this, curCommitId)
                    // LOGGER.info("Update meta res: $cacheFileCommitInsertRes")

                    // 5. validate
                    // changedFilePaths.forEach {
                    //     val validate = Dao.bgmDao.queryRepoCommitForCacheByFileRelativePath(it)
                    //     if (validate.isEmpty()) throw IllegalStateException("$it Not found in cache!")
                    //     if (validate.none { it.commitId == curCommitId }) throw IllegalStateException("$it Not found in cache!")
                    // }

                    // End of this iteration
                    // return@breakable
                    LOGGER.debug("[CACHE] $this build cache for commit successfully - [$curCommitId - $curCommitFullMsg]")
                    prev = cur
                }.onFailure {
                    failedCount++
                    LOGGER.error("Failed to build cache for $this - commit - ${cur.sha1Str()}")
                }
            }
            @Suppress("KotlinConstantConditions")
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

}