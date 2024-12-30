package moe.nyamori.bgm.config

import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.text.Charsets.UTF_8

private val ConfigLogger = LoggerFactory.getLogger("ConfigInit")


fun main() {
    val configJson =
        object {}.javaClass.getResourceAsStream("/config.sample.json")?.readAllBytes()?.toString(UTF_8) ?: "{}"
    val gson = GsonBuilder().disableHtmlEscaping().create()
    val configReadout = gson.fromJson(configJson, ConfigReadout::class.java)
    ConfigLogger.info("{}", configReadout)
    val configDto = configReadout.toDto()
    ConfigLogger.info("{}", configDto)
}

data class ConfigReadout(
    /**
     * Path start with / (Linux) or X: (Windows) will be treated as absolute path, otherwise will be relative from home folder
     */
    val homeFolderAbsolutePath: String?,
    val prevProcessedCommitRevIdFileName: String?,
    val preferJgit: Boolean?,
    val preferGitBatchAdd: Boolean?,
    val disableAllHooks: Boolean?,

    val httpHost: String?,
    val httpPort: Int?,

    // db things
    val sqliteFilePath: String?,
    val dbIsEnableWal: Boolean?, // sqlite only
    /**
     * if jdbc url is empty, then defaults to sqlite file in sqlite file path
     */
    val jdbcUrl: String?,
    val jdbcUsername: String?,
    val jdbcPassword: String?,
    val hikariMinIdle: Int?,
    val hikariMaxConn: Int?,

    val dbMetaKeyPrevCachedCommitRevId: String?,
    val dbMetaKeyPrevPersistedJsonCommitRevId: String?,

    val disableSpotCheck: Boolean?,
    val disableDbPersist: Boolean?,
    val disableDbPersistKey: Boolean?,
    val dbPersistKey: String?,

    val spotCheckerTimeoutThresholdMs: Int?,
    val bgmHealthStatus500TimeoutThresholdMs: Int?,

    val enableCrankerConnector: Boolean?,
    val crankerRegUrl: String?,
    val crankerSlidingWin: Int?,
    val crankerComponent: String?,

    // TODO: Add mutex lock for each repo to perform parse/build cache/etc. jobs
    val repoMutexTimeoutMs: Int?,
    val repoList: List<RepoLike>?,
)

data class RepoLike(
    val id: Int?,
    val path: String?,
    val type: String?,
    val optRepoIdCouplingWith: Int?,
    val optExpectedCommitPerDay: Int?,
    val optFriendlyName: String?,
    val optIsStatic: Boolean?,
)

private fun String.toAbsPath(homeFolderAbsolutePath: String): String {
    val osName = System.getProperty("os.name", "unknown").lowercase()
    val isWin = osName.contains("win")
    val isMac = osName.contains("mac")
    val isLinux = osName.contains("linux")
    if (isWin) {
        return if (this.matches("^[a-zA-Z]:\\.*$".toRegex())) {
            this
        } else {
            Path(homeFolderAbsolutePath).resolve(this.trim()).toString()
        }
    }
    if (isMac || isLinux) {
        return if (this.startsWith(File.separator)) {
            this
        } else {
            Path(homeFolderAbsolutePath).resolve(this.trim()).toString()
        }
    }
    throw IllegalStateException("unreachable")
}

fun ConfigReadout.toDto(): ConfigDto {
    val userHome = System.getProperty("user.home")
    val defHome = userHome + File.separator + "source"
    // check things first
    val finHome: String = if (!this.homeFolderAbsolutePath.isNullOrBlank()) {
        if (Path(this.homeFolderAbsolutePath).notExists()) {
            throw IllegalStateException("$homeFolderAbsolutePath not found")
        }
        this.homeFolderAbsolutePath
    } else {
        defHome
    }
    val finJdbcUrl: String = if (jdbcUrl.isNullOrEmpty()) {
        if (sqliteFilePath.isNullOrEmpty()) {
            "jdbc:sqlite:" + defHome + File.separator + "bgm-archive-db" + File.separator + "bgm-archive.sqlite"
        } else {
            "jdbc:sqlite:" + sqliteFilePath.toAbsPath(finHome)
        }
    } else {
        jdbcUrl
    }

    val finRepoList: List<RepoDto> = if (repoList.isNullOrEmpty()) {
        ConfigLogger.error("Empty repo in config")
        emptyList()
    } else {
        val res = mutableListOf<RepoDto>()
        for (r in repoList) {
            if (r.id == null) throw IllegalArgumentException("repo id should not be null")
            if (r.path.isNullOrEmpty()) throw IllegalArgumentException("repo path should not be null")
            val finRepoPath = r.path.toAbsPath(finHome)
            if (Path(finRepoPath).notExists()) throw IllegalArgumentException("repo path doesn't exist: $finRepoPath")
            if (r.type.isNullOrEmpty()) throw IllegalArgumentException("repo type should not be null")
            if (r.type.uppercase() !in RepoType.entries.map { it.name }) throw IllegalArgumentException("repo type should not be one of ${RepoType.entries}")
            res.add(
                RepoDto(
                    r.id,
                    finRepoPath,
                    RepoType.valueOf(r.type.uppercase()),
                    r.optExpectedCommitPerDay ?: 150,
                    r.optFriendlyName ?: Path(finRepoPath).name,
                    r.optIsStatic ?: true,
                    r.optRepoIdCouplingWith
                )
            )
        }
        for (r in res) {
            if (r.optRepoIdCouplingWith != null) {
                if (res.none { it.id == r.optRepoIdCouplingWith }) {
                    throw IllegalArgumentException("coupling repo not found: this = ${r.path}, coupling id = ${r.optRepoIdCouplingWith}")
                }
            }
        }
        res
    }

    return ConfigDto(
        homeFolderAbsolutePath = finHome,
        prevProcessedCommitRevIdFileName = this.prevProcessedCommitRevIdFileName ?: "last_processed_commit_rev_id",
        preferJgit = this.preferJgit ?: false,
        preferGitBatchAdd = this.preferGitBatchAdd ?: false,
        disableAllHooks = this.disableAllHooks ?: false,

        httpHost = this.httpHost ?: "localhost",
        httpPort = this.httpPort ?: 5926,

        dbIsEnableWal = this.dbIsEnableWal ?: false,

        jdbcUrl = finJdbcUrl,
        jdbcUsername = if (this.jdbcUsername.isNullOrEmpty()) null else this.jdbcUsername,
        jdbcPassword = if (this.jdbcPassword.isNullOrEmpty()) null else this.jdbcPassword,
        hikariMinIdle = this.hikariMinIdle ?: 2,
        hikariMaxConn = this.hikariMaxConn ?: 10,

        dbMetaKeyPrevCachedCommitRevId = this.dbMetaKeyPrevCachedCommitRevId
            ?: "prev_cached_commit_rev_id",
        dbMetaKeyPrevPersistedJsonCommitRevId = this.dbMetaKeyPrevPersistedJsonCommitRevId
            ?: "prev_persisted_json_commit_rev_id",

        disableSpotCheck = this.disableSpotCheck ?: false,
        disableDbPersist = this.disableDbPersist ?: false,
        disableDbPersistKey = this.disableDbPersistKey ?: false,
        dbPersistKey = this.dbPersistKey ?: UUID.randomUUID().toString(),

        spotCheckerTimeoutThresholdMs = this.spotCheckerTimeoutThresholdMs ?: 200_000,
        bgmHealthStatus500TimeoutThresholdMs = this.bgmHealthStatus500TimeoutThresholdMs ?: 1_200_000,

        enableCrankerConnector = this.enableCrankerConnector ?: false,
        crankerRegUrl = this.crankerRegUrl ?: "ws://localhost:3000",
        crankerSlidingWin = this.crankerSlidingWin ?: 2,
        crankerComponent = this.crankerComponent ?: "bgm-archive-kt",

        repoMutexTimeoutMs = this.repoMutexTimeoutMs ?: 5_000,
        repoList = finRepoList
    )
}
