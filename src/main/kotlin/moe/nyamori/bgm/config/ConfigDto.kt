package moe.nyamori.bgm.config

import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.simpleName
import moe.nyamori.bgm.model.SpaceType
import org.eclipse.jgit.lib.Repository
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

interface IConfig {
    /**
     * Path start with / (Linux) or X: (Windows) will be treated as absolute path, otherwise will be relative from home folder
     */
    val homeFolderAbsolutePath: String
    val prevProcessedCommitRevIdFileName: String
    val preferJgit: Boolean
    val preferGitBatchAdd: Boolean
    val disableCommitHook: Boolean

    val httpHost: String
    val httpPort: Int

    // db things
    val dbIsEnableWal: Boolean // sqlite only

    /**
     * if jdbc url is empty then defaults to sqlite file in sqlite file path
     */
    val jdbcUrl: String
    val jdbcUsername: String?
    val jdbcPassword: String?
    val hikariMinIdle: Int
    val hikariMaxConn: Int

    val dbMetaKeyPrevCachedCommitRevId: String
    val dbMetaKeyPrevPersistedJsonCommitRevId: String

    val disableSpotCheck: Boolean
    val disableDbPersist: Boolean
    val disableDbPersistKey: Boolean
    val dbPersistKey: String

    val isRemoveJsonAfterProcess: Boolean

    val spotCheckerTimeoutThresholdMs: Long
    val bgmHealthStatus500TimeoutThresholdMs: Long

    val enableCrankerConnector: Boolean
    val crankerRegUrl: String
    val crankerSlidingWin: Int
    val crankerComponent: String

    val logCacheDetail: Boolean

    // TODO: Add mutex lock for each repo to perform parse/build cache/etc. jobs
    val repoList: List<RepoDto>

    val spotCheckSampleSizeByType: Map<SpaceType, Int>

    val gitRelatedLockTimeoutMs: Long

    val blockRangeList: List<BlockRange>
    val spaceBlockList: List<SpaceBlock>
}

data class ConfigDto(
    /**
     * Path start with / (Linux) or X: (Windows) will be treated as absolute path, otherwise will be relative from home folder
     */
    override val homeFolderAbsolutePath: String,
    override val prevProcessedCommitRevIdFileName: String,
    override val preferJgit: Boolean,
    override val preferGitBatchAdd: Boolean,
    override val disableCommitHook: Boolean,

    override val httpHost: String,
    override val httpPort: Int,

    // db things
    override val dbIsEnableWal: Boolean, // sqlite only
    /**
     * if jdbc url is empty, then defaults to sqlite file in sqlite file path
     */
    override val jdbcUrl: String,
    override val jdbcUsername: String?,
    override val jdbcPassword: String?,
    override val hikariMinIdle: Int,
    override val hikariMaxConn: Int,

    override val dbMetaKeyPrevCachedCommitRevId: String,
    override val dbMetaKeyPrevPersistedJsonCommitRevId: String,

    override val disableSpotCheck: Boolean,
    override val disableDbPersist: Boolean,
    override val disableDbPersistKey: Boolean,
    override val dbPersistKey: String,

    override val isRemoveJsonAfterProcess: Boolean,

    override val spotCheckerTimeoutThresholdMs: Long,
    override val bgmHealthStatus500TimeoutThresholdMs: Long,

    override val enableCrankerConnector: Boolean,
    override val crankerRegUrl: String,
    override val crankerSlidingWin: Int,
    override val crankerComponent: String,

    override val logCacheDetail: Boolean,

    // TODO: Add mutex lock for each repo to perform parse/build cache/etc. jobs
    override val repoList: List<RepoDto>,

    override val spotCheckSampleSizeByType: Map<SpaceType, Int>,

    override val gitRelatedLockTimeoutMs: Long,

    override val blockRangeList: List<BlockRange>,
    override val spaceBlockList: List<SpaceBlock>,
) : IConfig

data class RepoDto(
    val id: Int,
    val path: String,
    val type: RepoType,
    val optFriendlyName: String,
    val optRepoIdCouplingWith: Int?,
    val optIsStatic: Boolean,
    val optMutexTimeoutMs: Long,
) {
    @Transient
    private val lock = ReentrantLock()

    @Transient
    val repo = GitHelper.getRepoByPath(path)

    fun <T> withLock(action: () -> T): T {
        try {
            if (!lock.tryLock(optMutexTimeoutMs, TimeUnit.MILLISECONDS)) {
                throw IllegalStateException("Failed to acquire lock after $optMutexTimeoutMs ms")
            }
            return action()
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
        }
    }
}

fun Repository.toRepoDtoOrThrow(): RepoDto {
    val that = this
    val dto = checkRepoExist(that) ?: throw IllegalStateException("Repository does not exist: ${that.simpleName()}")
    return dto
}

private fun checkRepoExist(repo: Repository): RepoDto? {
    return Config.repoList.firstOrNull { it.repo == repo }
}

fun Repository.hasCouplingJsonRepo(): Boolean {
    val dto = this.toRepoDtoOrThrow()
    return dto.type == RepoType.HTML && dto.optRepoIdCouplingWith != null
}

fun Repository.hasCouplingArchiveRepo(): Boolean {
    val dto = this.toRepoDtoOrThrow()
    return dto.type == RepoType.JSON && dto.optRepoIdCouplingWith != null
}

fun Repository.getCouplingJsonRepo(): Repository? {
    val dto = this.toRepoDtoOrThrow()
    if (!hasCouplingJsonRepo()) return null
    return Config.repoList.first { it.id == dto.optRepoIdCouplingWith!! }.repo
}

fun Repository.getCouplingArchiveRepo(): Repository? {
    val dto = this.toRepoDtoOrThrow()
    if (!hasCouplingArchiveRepo()) return null
    return Config.repoList.first { it.id == dto.optRepoIdCouplingWith!! }.repo
}

enum class RepoType {
    HTML, JSON
}

data class SpaceBlock(
    val spaceType: String?,
    val spaceName: String?,
    val blockRange: BlockRange?
) {
    fun validateOrNull() = runCatching {
        val type = SpaceType.valueOf(spaceType!!.uppercase())
        val name = spaceName!!
        val ignore = blockRange!!.toInstantPairOrNull()
        SpaceBlock(type.name, name, blockRange)
    }.getOrNull()
}

// ISO-8601 Timestamp
data class BlockRange(
    val startTime: String?,
    val endTime: String?,
) {

    fun toInstantPairOrNull(): Pair<java.time.Instant, java.time.Instant>? = runCatching {
        val (sZdt, eZdt) = extract()
        return (sZdt.toInstant() to eZdt.toInstant())
    }.getOrNull()

    private fun extract(): Pair<ZonedDateTime, ZonedDateTime> {
        return ZonedDateTime.parse(startTime!!) to ZonedDateTime.parse(endTime!!)
    }
}