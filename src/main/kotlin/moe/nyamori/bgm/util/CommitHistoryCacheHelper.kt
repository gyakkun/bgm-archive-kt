package moe.nyamori.bgm.util

import moe.nyamori.bgm.config.RepoDto
import moe.nyamori.bgm.db.IBgmDao
import moe.nyamori.bgm.git.GitHelper.findChangedFilePaths
import moe.nyamori.bgm.git.GitHelper.getFirstCommitIdStr
import moe.nyamori.bgm.git.GitHelper.getLatestCommitRef
import moe.nyamori.bgm.git.GitHelper.getRevCommitById
import moe.nyamori.bgm.git.GitHelper.getWalkBetweenCommitInReverseOrder
import moe.nyamori.bgm.git.GitHelper.simpleName
import moe.nyamori.bgm.util.GitCommitIdHelper.sha1Str
import moe.nyamori.bgm.util.StringHashingHelper.hashedAbsolutePathWithoutGitId
import org.slf4j.LoggerFactory

object CommitHistoryCacheHelper {
    private val LOGGER = LoggerFactory.getLogger(CommitHistoryCacheHelper::class.java)
    fun RepoDto.buildCache(bgmDao: IBgmDao) {
        check(HttpHelper.DB_WRITE_LOCK.isHeldByCurrentThread)
        val repoDto = this
        // require(this.hasCouplingJsonRepo() || this.hasCouplingArchiveRepo())
        var isFreshCacheBuild = false
        val prevCachedSha1 =
            bgmDao.getPrevCachedCommitId(repoDto) ?: run { isFreshCacheBuild = true; repoDto.getFirstCommitIdStr() }
        val latestSha1 = repoDto.getLatestCommitRef().sha1Str()
        val firstCommitSha1 = repoDto.getFirstCommitIdStr()
        val firstRevCommit = repoDto.getRevCommitById(firstCommitSha1)
        val prevCachedRevCommit = repoDto.getRevCommitById(prevCachedSha1)
        val prevCachedMsg = prevCachedRevCommit.fullMessage.trim()
        val latestRevCommit = repoDto.getRevCommitById(latestSha1)
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
                        LOGGER.warn("Commit $cur has been iterated twice! Repo: ${repoDto.repo.simpleName()}")
                        return@breakable
                    }
                    val curCommitId = cur.sha1Str()
                    val curCommitFullMsg = cur.fullMessage
                    if (curCommitFullMsg.startsWith("META", ignoreCase = true) && cur == firstRevCommit) {
                        bgmDao.updatePrevCachedCommitId(repoDto, curCommitId)
                        prev = cur
                        return@outer
                    }
                    val changedFilePaths = repoDto.repo.findChangedFilePaths(prev, cur)
                        .filter { it.endsWith("html", ignoreCase = true) || it.endsWith("json", ignoreCase = true) }
                    LOGGER.debug("[CACHE] ${repoDto.repo.simpleName()} - cur commit: $curCommitId , msg - $curCommitFullMsg")

                    // 1. Update changed files
                    val resIntArray = bgmDao.batchUpsertFileRelativePathForCache(changedFilePaths)
                    // LOGGER.info("File ids insert res int array: {}", resIntArray)

                    // 2. Insert this commit to repo commit table
                    val resInt =
                        bgmDao.insertRepoCommitForCache(repoDto.hashedAbsolutePathWithoutGitId().toLong(), curCommitId)
                    // LOGGER.info("commit insert res int: $resInt")

                    // 3. Insert to file-commit
                    val cacheFileCommitInsertRes = bgmDao.batchUpsertFileCommitForCache(
                        changedFilePaths,
                        repoDto.hashedAbsolutePathWithoutGitId().toLong(),
                        curCommitId
                    )
                    // LOGGER.info("Insert result: $cacheFileCommitInsertRes")

                    // 4. Update meta data
                    val updateMetaRes = bgmDao.updatePrevCachedCommitId(repoDto, curCommitId)
                    // LOGGER.info("Update meta res: $cacheFileCommitInsertRes")

                    // 5. validate
                    // changedFilePaths.forEach {
                    //     val validate = bgmDao.queryRepoCommitForCacheByFileRelativePath(it)
                    //     if (validate.isEmpty()) throw IllegalStateException("$it Not found in cache!")
                    //     if (validate.none { it.commitId == curCommitId }) throw IllegalStateException("$it Not found in cache!")
                    // }

                    // End of this iteration
                    // return@breakable
                    LOGGER.debug("[CACHE] ${repoDto.repo.simpleName()} build cache for commit successfully - [$curCommitId - $curCommitFullMsg]")
                    prev = cur
                }.onFailure {
                    failedCount++
                    LOGGER.error("Failed to build cache for ${repoDto.repo.simpleName()} - commit - ${cur.sha1Str()}")
                }
            }
            @Suppress("KotlinConstantConditions")
            if (counter > 0 && failedCount == 0) {
                val sentence = """Successfully build cache for $repoDto from 
                    [old] [$prevCachedMsg] [$prevCachedSha1]
                    to
                    [latest] [$latestMsg] [$latestSha1]
                    Total: $counter""".trimIndent()
                LOGGER.info(sentence)
            } else if (counter > 0 && failedCount != 0) {
                val sentence = """Failed to build cache for $repoDto from 
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