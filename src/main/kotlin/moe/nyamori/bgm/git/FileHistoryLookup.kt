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
import moe.nyamori.bgm.model.lowercaseName
import moe.nyamori.bgm.util.FilePathHelper
import moe.nyamori.bgm.util.GitCommitIdHelper.sha1Str
import moe.nyamori.bgm.util.GitCommitIdHelper.timestampHint
import moe.nyamori.bgm.util.StringHashingHelper.repoIdFromDto
import moe.nyamori.bgm.util.blockAndPrintProcessResults
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants.DOT_GIT
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*


object FileHistoryLookup {
    private val log = LoggerFactory.getLogger(FileHistoryLookup::class.java)

    @JvmStatic
    fun main(args: Array<String>) {

    }

    data class CommitHashAndTimestampAndMsg(
        val repo: Repository,
        val hash: String,
        val commitTimeEpochMs: Long,
        val authorTimeEpochMs: Long,
        val msg: String
    )

    private val spaceTypeTopicIdPairToChatamPairCache: LoadingCache<
            Pair<SpaceType, Int/*topicId*/>,
            TreeMap<Long, ChatamPair>
            > = Caffeine.newBuilder()
            .maximumSize(100)
            .scheduler(Scheduler.systemScheduler())
            .expireAfterAccess(Duration.ofMinutes(12))
            .expireAfterWrite(Duration.ofMinutes(12))
        .build { (spaceType, topicId) ->
            getExactTimestampToChatamMapByTopicTypeAndId(spaceType, topicId)
        }


    fun getExactTimestampToChatamMapByTopicTypeAndId(spaceType: SpaceType, topicId: Int): TreeMap<Long, ChatamPair> {
        val jsonRelPath = topicId.toJsonRelPath(spaceType)
        val htmlRelPath = topicId.toHtmlRelPath(spaceType)
        val jsonCommitList = getAllChatamByRelativePath(jsonRelPath)
        val htmlCommitList = getAllChatamByRelativePath(htmlRelPath)
        val etfmt = extractExactTimestampFromMetaTs(spaceType, topicId, jsonCommitList, htmlCommitList)
        // filter out blocked range
        val filtered = etfmt.filterKeys { msTs ->
            Config.blockRangeList
                .mapNotNull { it.toInstantPairOrNull() }
                .none { pair ->
                    val inst = Instant.ofEpochMilli(msTs)
                    inst <= pair.second && inst >= pair.first
                }
        }
        return TreeMap(filtered)
    }

    data class ChatamPair(
        val spaceType: SpaceType,
        val topicId: Int,
        val json: CommitHashAndTimestampAndMsg,
        val html: CommitHashAndTimestampAndMsg
    )

    private fun extractExactTimestampFromMetaTs(
        spaceType: SpaceType,
        topicId: Int,
        jsonCommitList: List<CommitHashAndTimestampAndMsg>,
        archiveCommitList: List<CommitHashAndTimestampAndMsg>
    ): Map<Long, ChatamPair> {
        val jsonChatamByTs = jsonCommitList.associateBy { it.timestampHint() }
        val tsList = archiveCommitList
            .filter {
                jsonChatamByTs.contains(it.timestampHint())
                        // Special handling
                        || (jsonChatamByTs.isNotEmpty() && it.msg.startsWith("META"))
            }
            .associate { htmlChatam ->
                val thePairedJsonChatam = jsonChatamByTs[htmlChatam.timestampHint()] ?: run {
                    // This special handling means that we can't find a paired json commit with the same
                    // timestamp in the html commit. We use ceiling to find the next commit of the least great
                    // timestamp in json repo of the same path to ensure the json file for this html file exists.
                    // If still not exists then finally we will fall back to the first json file (tm.firstEntry().value)
                    // We don't worry about if the json list is empty because the previous filter will prevent
                    // this from happening.
                    //
                    // This special handling is mainly for the first batch of historical data of GROUP topics.
                    // See the commit "META: ADD HISTORICAL DATA." in bgm-archive-old repo.
                    log.warn(
                        "Seems we need to do special handling for commit: {} , timestampHint = {}",
                        htmlChatam.msg.trim(),
                        Instant.ofEpochMilli(htmlChatam.timestampHint())
                    )
                    val tm = TreeMap(jsonChatamByTs)
                    val res = tm.ceilingEntry(htmlChatam.timestampHint())?.value ?: tm.firstEntry().value
                    log.warn(
                        "Html commit {} mapped to json commit {}. Time diff (html-json) = {}",
                        htmlChatam.msg.trim(),
                        res.msg.trim(),
                        Duration.ofMillis(htmlChatam.timestampHint() - res.timestampHint())
                    )
                    res
                }

                // This is to extract the accurate timestamp from meta_ts.txt file
                val metaTs = htmlChatam.repo.getFileContentAsStringInACommit(
                    htmlChatam.hash, "${spaceType.lowercaseName()}/meta_ts.txt", forceJgit = true
                )
                if (metaTs.trim().isBlank()) return@associate htmlChatam.timestampHint() to ChatamPair(
                    spaceType,
                    topicId,
                    thePairedJsonChatam,
                    htmlChatam
                )
                val exactTs: Long = metaTs.lines().filter { it.isNotBlank() }.map { it.split(":") }.filter {
                    // Simple validation of the line in meta_ts file
                    if (it.size != 2) {
                        log.warn(
                            "Invalid meta_ts line: ${htmlChatam.repo.folderName()} - commit - ${
                                htmlChatam.hash
                            } - line - $it"
                        )
                        return@filter false
                    } else return@filter true
                }.associate {
                    // associate: If any of two pairs would have the same key the last one gets added to the map.
                    // which is of the correct semantic because if one file is archived more than once in a
                    // commit, we definitely need the last result.
                    it[0] to it[1]
                }.get(topicId.toString())?.toLongOrNull()
                    ?: run {
                        log.error(
                            "Topic id not found in this meta_ts file: ${htmlChatam.repo.folderName()} - commit - ${
                                htmlChatam.hash
                            } - type - ${spaceType.lowercaseName()} - topicId - $topicId"
                        )
                        htmlChatam.timestampHint()
                    }
                return@associate exactTs to ChatamPair(
                    spaceType,
                    topicId,
                    thePairedJsonChatam, htmlChatam
                )
            }
        return tsList
    }

    val notLogRelPathSuffix = setOf("meta_ts.txt")

    fun getAllChatamByRelativePath(
        relPath: String,
        forceNotDbCache: Boolean = false,
        forceJgit: Boolean = false
    ): List<CommitHashAndTimestampAndMsg> = runCatching {
        if (forceNotDbCache) throw RuntimeException("Force opt out db cache!")
        val isHtml = relPath.endsWith("html", ignoreCase = true)
        val isJson = relPath.endsWith("json", ignoreCase = true)
        if (!isJson && !isHtml) {
            throw UnsupportedOperationException("Not support get chatam from db cache for non-json and non-html file!")
        }
        val repoIdToCommitIdList = Dao.bgmDao.queryRepoCommitForCacheByFileRelativePath(relPath).groupBy { it.repoId }
        val repoList = if (isHtml) allArchiveRepoListSingleton else allJsonRepoListSingleton
        val repoIdToRepo = repoList.associateBy { it.repoIdFromDto().toLong() }
        val res = repoIdToRepo.mapNotNull { (k, repo) ->
            repoIdToCommitIdList[k]
                ?.mapNotNull { runCatching { repo.getRevCommitById(it.commitId) }.getOrNull() }
                ?.map { it.toChatam(repo) }
        }.flatten()
        res
    }.onFailure {
        log.error("Failed to get all chatam by relPath=$relPath, fallback to git way: ", it)
    }.getOrNull() ?: runCatching {
        val timing = System.currentTimeMillis()
        val isHtml = relPath.endsWith("html", ignoreCase = true)
        val isJson = relPath.endsWith("json", ignoreCase = true)
        val repoList = if (isHtml) allArchiveRepoListSingleton
        else if (isJson) allJsonRepoListSingleton
        else listOf(allArchiveRepoListSingleton, allJsonRepoListSingleton).flatten()

        val res = if (Config.preferJgit || forceJgit) {
            repoList.parallelStream().map {
                runCatching {
                    it.getRevCommitListJgit(relPath)
                }.onFailure {
                    log.error(
                        "Failed to get rev commit list from jgit for path=$relPath. Try fallback to ext git. ",
                        it
                    )
                }.getOrNull() ?: runCatching {
                    it.getRevCommitListExtGit(relPath)
                }.onFailure {
                    log.error("Even failed to get rev commit list from ext git for path=$relPath. Will throw ex", it)
                }.getOrThrow()
            }.flatMap { it.stream() }.toList()
        } else repoList.parallelStream().map {
            runCatching {
                it.getRevCommitListExtGit(relPath)
            }.onFailure {
                log.error("Even failed to get rev commit list from ext git for path=$relPath. Will throw ex", it)
            }.getOrThrow()
        }.flatMap { it.stream() }.toList()
        if (notLogRelPathSuffix.none { relPath.endsWith(it) }) {
            val elapsed = System.currentTimeMillis() - timing
            if (elapsed >= 100) {
                log.warn(
                    "$this ${
                        if (Config.preferJgit) "jgit" else "external git"
                    } get log timing: ${elapsed}ms. RelPath: $relPath"
                )
            }
        }
        res
    }.onFailure {
        log.error("Ex when getting all chatam by relPath=$relPath using git: ", it)
    }.getOrDefault(emptyList())

    fun RevCommit.toChatam(repo: Repository) = CommitHashAndTimestampAndMsg(
        repo,
        this.sha1Str(),
        this.committerIdent.whenAsInstant.toEpochMilli(),
        this.authorIdent.whenAsInstant.toEpochMilli(),
        this.fullMessage
    )

    fun Repository.getRevCommitList(relPath: String): List<CommitHashAndTimestampAndMsg> =
        runCatching {
            val repoIdCommitIdList = Dao.bgmDao.queryRepoCommitForCacheByFileRelativePath(relPath)
            repoIdCommitIdList.filter { it.repoId == this.repoIdFromDto().toLong() }
                .map { this.getRevCommitById(it.commitId) }
                .map { it.toChatam(this) }
        }.onFailure {
            log.error("Failed to get commit list for $relPath by db query: ", it)
        }.getOrNull() ?:
        runCatching {
            val timing = System.currentTimeMillis()
            val res = if (Config.preferJgit) {
                this.getRevCommitListJgit(relPath)
            } else {
                this.getRevCommitListExtGit(relPath)
            }
            if (notLogRelPathSuffix.none { relPath.endsWith(it) }) {
                val elapsed = System.currentTimeMillis() - timing
                if (elapsed >= 100) {
                    log.warn(
                        "$this ${
                            if (Config.preferJgit) "jgit" else "external git"
                        } get log timing: ${elapsed}ms. RelPath: $relPath"
                    )
                }
            }
            return res
        }.onFailure {
            log.error(
                "$this Failed to get rev commit list at $relPath by calling external git process: ",
                it
            )
        }.getOrElse {
            log.warn("Fall back to jgit get commit history for $relPath at $this")
            return this.getRevCommitListJgit(relPath)
        }

    private fun Repository.getRevCommitListJgit(relativePathToRepoFolder: String): List<CommitHashAndTimestampAndMsg> {
        return Git(this).use { git ->
            val commitList = git.log()
                .addPath(relativePathToRepoFolder)
                .call()
            commitList.map { it.toChatam(this) }
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
                    this,
                    it.substring(0, firstVerticalBarIdx),
                    commitTimeEpochSec * 1000,
                    authorTimeEpochSec * 1000,
                    it.substring(thirdVerticalBarIdx + 1)
                )
            }.filterNotNull()
    }


    fun getArchiveFileContentAsStringAtTimestamp(spaceType: SpaceType, topicId: Int, timestamp: Long): String =
        getArchiveFileHashMsgContentAsStringAtTimestamp(spaceType, topicId, timestamp).second

    fun getArchiveFileHashMsgContentAsStringAtTimestamp(
        spaceType: SpaceType,
        topicId: Int,
        timestamp: Long
    ): Pair<ChatamPair, String> {
        val timestampRevCommitMap = spaceTypeTopicIdPairToChatamPairCache.get(spaceType to topicId)
        if (timestampRevCommitMap[timestamp] != null) {
            return timestampRevCommitMap[timestamp]!!.let {
                val diff = it.html.timestampHint() - timestamp
                val diffSec = diff / 1000
                val logSec = diffSec % 60
                val logMin = diffSec / 60
                log.info("Diff=${logMin}m${logSec}s for html ${topicId.toHtmlRelPath(spaceType)} in ${it.html.repo.folderName()} : ${it.html.hash}")
                it to it.html.repo.getFileContentAsStringInACommit(
                    it.html.hash,
                    it.topicId.toHtmlRelPath(it.spaceType),
                )
            }
        }

        throw IllegalStateException(
            "Should get file content for $spaceType-$topicId at timestamp=$timestamp(${
                Timestamp(
                    timestamp
                ).toInstant()
            }) in archive and static repos but got nothing!"
        )
    }

    fun getJsonFileContentAsStringAtTimestamp(spaceType: SpaceType, topicId: Int, timestamp: Long): String =
        getJsonFileHashMsgContentAsStringTimestamp(spaceType, topicId, timestamp).second

    fun getJsonFileHashMsgContentAsStringTimestamp(
        spaceType: SpaceType,
        topicId: Int,
        timestamp: Long
    ): Pair<ChatamPair, String> {
        val timestampRevCommitMap = spaceTypeTopicIdPairToChatamPairCache.get(spaceType to topicId)
        if (timestampRevCommitMap[timestamp] != null) {
            return timestampRevCommitMap[timestamp]!!.let {
                val diff = it.json.timestampHint() - timestamp
                val diffSec = diff / 1000
                val logSec = diffSec % 60
                val logMin = diffSec / 60
                log.debug("Diff=${logMin}m${logSec}s for json ${topicId.toJsonRelPath(spaceType)} in ${it.json.repo.folderName()} : ${it.json.hash}")
                it to it.json.repo.getFileContentAsStringInACommit(
                    it.json.hash,
                    it.topicId.toJsonRelPath(it.spaceType),
                )
            }
        }

        throw IllegalStateException(
            "Should get file content for $spaceType-$topicId at timestamp=$timestamp(${
                Timestamp(
                    timestamp
                ).toInstant()
            }) in json and static repos but got nothing!"
        )
    }

    fun getArchiveTimestampList(spaceType: SpaceType, topicId: Int): SortedSet<Long> {
        return spaceTypeTopicIdPairToChatamPairCache.get(spaceType to topicId).navigableKeySet()
    }

    fun getJsonTimestampList(spaceType: SpaceType, topicId: Int): SortedSet<Long> {
        return spaceTypeTopicIdPairToChatamPairCache.get(spaceType to topicId).navigableKeySet()
    }
}

fun Int.toJsonRelPath(spaceType: SpaceType) =
    spaceType.lowercaseName() + "/" + FilePathHelper.numberToPath(this) + ".json"

fun Int.toHtmlRelPath(spaceType: SpaceType) =
    spaceType.lowercaseName() + "/" + FilePathHelper.numberToPath(this) + ".html"