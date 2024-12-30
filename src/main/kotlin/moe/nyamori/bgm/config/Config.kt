package moe.nyamori.bgm.config

object Config {

    // For bare repo
    // Please run: git config remote.origin.fetch 'refs/heads/*:refs/heads/*'
    // to make fetch same as pull

    private lateinit var delegate: ConfigDto

    // FIXME: Temp solution
    fun setConfigDtoDelegate(configDtoDelegate: ConfigDto) {
        this.delegate = configDtoDelegate
    }

    val BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME: String
        get() = delegate.prevProcessedCommitRevIdFileName

    val BGM_ARCHIVE_PREFER_JGIT: Boolean
        get() = delegate.preferJgit

    val BGM_ARCHIVE_PREFER_GIT_BATCH_ADD: Boolean
        get() = delegate.preferGitBatchAdd

    val BGM_ARCHIVE_DISABLE_HOOK: Boolean
        get() = delegate.disableAllHooks

    val BGM_ARCHIVE_ADDRESS: String
        get() = delegate.httpHost

    val BGM_ARCHIVE_PORT: Int
        get() = delegate.httpPort

    val BGM_ARCHIVE_DB_META_KEY_PREV_PERSISTED_JSON_COMMIT_REV_ID: String
        get() = delegate.dbMetaKeyPrevPersistedJsonCommitRevId

    val BGM_ARCHIVE_DB_META_KEY_PREV_CACHED_COMMIT_REV_ID: String
        get() = delegate.dbMetaKeyPrevCachedCommitRevId

    val BGM_ARCHIVE_DISABLE_SPOT_CHECK: Boolean
        get() = delegate.disableSpotCheck

    val BGM_ARCHIVE_DISABLE_DB_PERSIST: Boolean
        get() = delegate.disableDbPersist

    val BGM_ARCHIVE_IS_REMOVE_JSON_AFTER_PROCESS: Boolean
        get() = delegate.isRemoveJsonAfterProcess

    val BGM_ARCHIVE_DB_IS_ENABLE_WAL: Boolean
        get() = delegate.dbIsEnableWal

    val BGM_ARCHIVE_DISABLE_DB_PERSIST_KEY: Boolean
        get() = delegate.disableDbPersistKey

    val BGM_ARCHIVE_DB_PERSIST_KEY: String
        get() = delegate.dbPersistKey

    val BGM_ARCHIVE_SPOT_CHECKER_TIMEOUT_THRESHOLD_MS: Long
        get() = delegate.spotCheckerTimeoutThresholdMs

    val BGM_HEALTH_STATUS_500_TIMEOUT_THRESHOLD_MS: Long
        get() = delegate.bgmHealthStatus500TimeoutThresholdMs

    val BGM_ARCHIVE_ENABLE_CRANKER_CONNECTOR: Boolean
        get() = delegate.enableCrankerConnector

    val BGM_ARCHIVE_CRANKER_REG_URL: String
        get() = delegate.crankerRegUrl

    val BGM_ARCHIVE_CRANKER_SLIDING_WIN: Int
        get() = delegate.crankerSlidingWin

    val ALL_REPOS: List<RepoDto>
        get() = delegate.repoList
}