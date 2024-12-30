package moe.nyamori.bgm.config

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

    val spotCheckerTimeoutThresholdMs: Int,
    val bgmHealthStatus500TimeoutThresholdMs: Int,

    val enableCrankerConnector: Boolean,
    val crankerRegUrl: String,
    val crankerSlidingWin: Int,
    val crankerComponent: String,

    // TODO: Add mutex lock for each repo to perform parse/build cache/etc. jobs
    val repoMutexTimeoutMs: Int,
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
)

enum class RepoType {
    HTML, JSON
}
