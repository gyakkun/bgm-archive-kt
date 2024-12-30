package moe.nyamori.bgm.config

import moe.nyamori.bgm.git.GitHelper
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

data class ConfigDto(
    /**
     * Path start with / (Linux) or X: (Windows) will be treated as absolute path, otherwise will be relative from home folder
     */
    val homeFolderAbsolutePath: String,
    val prevProcessedCommitRevIdFileName: String,
    val preferJgit: Boolean,
    val preferGitBatchAdd: Boolean,
    val disableAllHooks: Boolean,

    val httpHost: String,
    val httpPort: Int,

    // db things
    val dbIsEnableWal: Boolean, // sqlite only
    /**
     * if jdbc url is empty, then defaults to sqlite file in sqlite file path
     */
    val jdbcUrl: String,
    val jdbcUsername: String?,
    val jdbcPassword: String?,
    val hikariMinIdle: Int,
    val hikariMaxConn: Int,

    val dbMetaKeyPrevCachedCommitRevId: String,
    val dbMetaKeyPrevPersistedJsonCommitRevId: String,

    val disableSpotCheck: Boolean,
    val disableDbPersist: Boolean,
    val disableDbPersistKey: Boolean,
    val dbPersistKey: String,

    val isRemoveJsonAfterProcess: Boolean,

    val spotCheckerTimeoutThresholdMs: Long,
    val bgmHealthStatus500TimeoutThresholdMs: Long,

    val enableCrankerConnector: Boolean,
    val crankerRegUrl: String,
    val crankerSlidingWin: Int,
    val crankerComponent: String,

    // TODO: Add mutex lock for each repo to perform parse/build cache/etc. jobs
    val repoList: List<RepoDto>,
)

data class RepoDto(
    val id: Int,
    val path: String,
    val type: RepoType,
    val expectedCommitPerDay: Int,
    val friendlyName: String,
    val isStatic: Boolean,
    val optRepoIdCouplingWith: Int?,

    val mutexTimeoutMs: Long,
) {
    private val lock = ReentrantLock()

    val repo = GitHelper.getRepoByPath(path)

    fun <T> withLock(action: () -> T) {
        try {
            if (!lock.tryLock(mutexTimeoutMs, TimeUnit.MILLISECONDS)) {
                throw IllegalStateException("Failed to acquire lock after $mutexTimeoutMs ms")
            }
            action()
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
        }
    }
}

fun RepoDto.hasCouplingJsonRepo(): Boolean {
    return type == RepoType.HTML && optRepoIdCouplingWith != null
}

fun RepoDto.hasCouplingArchiveRepo(): Boolean {
    return type == RepoType.JSON && optRepoIdCouplingWith != null
}

fun RepoDto.getCouplingJsonRepo(): RepoDto? {
    if (!hasCouplingJsonRepo()) return null
    return Config.ALL_REPOS.first { it.id == this.optRepoIdCouplingWith!! }
}

fun RepoDto.getCouplingArchiveRepo(): RepoDto? {
    if (!hasCouplingArchiveRepo()) return null
    return Config.ALL_REPOS.first { it.id == this.optRepoIdCouplingWith!! }
}

enum class RepoType {
    HTML, JSON
}
