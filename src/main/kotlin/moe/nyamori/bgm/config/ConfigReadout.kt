package moe.nyamori.bgm.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import kotlin.io.path.*
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

fun checkAndGetConfigDto(): ConfigDto {
    val gson = GsonBuilder().disableHtmlEscaping().create()
    val envConfigPath = System.getenv("E_BGM_ARCHIVE_CONFIG_PATH")
    val sysPropConfigPath = System.getProperty("config.path")
    if (!envConfigPath.isNullOrEmpty()) {
        return extract("env var", envConfigPath, gson)
    }
    if (!sysPropConfigPath.isNullOrEmpty()) {
        return extract("sys prop", sysPropConfigPath, gson)
    }
    return ConfigReadout().toDto()
}

private fun extract(src: String, envConfigPath: String, gson: Gson): ConfigDto {
    val p = Path(envConfigPath)
    if (p.exists() && p.isRegularFile()) {
        val str = p.readText(UTF_8)
        val readout = gson.fromJson(str, ConfigReadout::class.java)
        val dto = readout.toDto()
        return dto
    } else throw IllegalArgumentException("config path in $src is invalid: $p")
}

data class ConfigReadout(
    /**
     * Path start with / (Linux) or X: (Windows) will be treated as absolute path, otherwise will be relative from home folder
     */
    val homeFolderAbsolutePath: String? = null,
    val prevProcessedCommitRevIdFileName: String? = null,
    val preferJgit: Boolean? = null,
    val preferGitBatchAdd: Boolean? = null,
    val disableAllHooks: Boolean? = null,

    val httpHost: String? = null,
    val httpPort: Int? = null,

    // db things
    val dbIsEnableWal: Boolean? = null, // sqlite only
    /**
     * if jdbc url is empty, then defaults to sqlite file in sqlite file path
     */
    val jdbcUrl: String? = null,
    val jdbcUsername: String? = null,
    val jdbcPassword: String? = null,
    val hikariMinIdle: Int? = null,
    val hikariMaxConn: Int? = null,

    val dbMetaKeyPrevCachedCommitRevId: String? = null,
    val dbMetaKeyPrevPersistedJsonCommitRevId: String? = null,

    val disableSpotCheck: Boolean? = null,
    val disableDbPersist: Boolean? = null,
    val disableDbPersistKey: Boolean? = null,
    val dbPersistKey: String? = null,

    val isRemoveJsonAfterProcess: Boolean? = null,

    val spotCheckerTimeoutThresholdMs: Long? = null,
    val bgmHealthStatus500TimeoutThresholdMs: Long? = null,

    val enableCrankerConnector: Boolean? = null,
    val crankerRegUrl: String? = null,
    val crankerSlidingWin: Int? = null,
    val crankerComponent: String? = null,

    // TODO: Add mutex lock for each repo to perform parse/build cache/etc. jobs
    val repoList: List<RepoReadout>? = null,
)

data class RepoReadout(
    val id: Int?,
    val path: String?,
    val type: String?,
    val optFriendlyName: String?,
    val optExpectedCommitPerDay: Int?,
    val optRepoIdCouplingWith: Int?,
    val optIsStatic: Boolean?,
    val optMutexTimeoutMs: Long?,
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
        } else if( !Path(this.homeFolderAbsolutePath).isDirectory()){
            throw IllegalStateException("$homeFolderAbsolutePath is not a folder")
        }
        this.homeFolderAbsolutePath
    } else {
        defHome
    }
    val finJdbcUrl: String = if (jdbcUrl.isNullOrEmpty()) {
            "jdbc:sqlite:" + defHome + File.separator + "bgm-archive-db" + File.separator + "bgm-archive.sqlite"
    } else {
        jdbcUrl
    }

    val finRepoList: List<RepoDto> = if (repoList.isNullOrEmpty()) {
        ConfigLogger.error("Empty repo in config")
        emptyList()
    } else {
        val res = mutableListOf<RepoDto>()
        val idUsed = mutableSetOf<Int>()
        for (r in repoList) {
            if (r.id == null) throw IllegalArgumentException("repo id should not be null")
            if (!idUsed.add(r.id)) throw IllegalArgumentException("duplicated repo id ${r.id}")
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
                    r.optFriendlyName
                        ?: Path(finRepoPath).name /* The last part of the name, FIXME what if it's a dot git folder? */,
                    r.optExpectedCommitPerDay ?: 150,
                    r.optRepoIdCouplingWith,
                    r.optIsStatic ?: true,
                    r.optMutexTimeoutMs ?: 5_000L
                )
            )
        }

        for (r in res) {
            if (r.optRepoIdCouplingWith != null) {
                val count = res.count { it.id == r.optRepoIdCouplingWith }
                if (count == 0) {
                    throw IllegalArgumentException("coupling repo not found: this = ${r.path}, coupling id = ${r.optRepoIdCouplingWith}")
                }
                if (count > 1) {
                    throw IllegalArgumentException("more than 1 coupling repo for ${r.path}, coupling id = ${r.optRepoIdCouplingWith}")
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
        httpPort = this.httpPort?.coerceIn(1025..65535) ?: 5926,

        dbIsEnableWal = this.dbIsEnableWal ?: false,

        jdbcUrl = finJdbcUrl,
        jdbcUsername = if (this.jdbcUsername.isNullOrEmpty()) null else this.jdbcUsername,
        jdbcPassword = if (this.jdbcPassword.isNullOrEmpty()) null else this.jdbcPassword,
        hikariMinIdle = this.hikariMinIdle?.coerceIn(2..40) ?: 2,
        hikariMaxConn = this.hikariMaxConn?.coerceIn(4..100) ?: 10,

        dbMetaKeyPrevCachedCommitRevId = this.dbMetaKeyPrevCachedCommitRevId
            ?: "prev_cached_commit_rev_id",
        dbMetaKeyPrevPersistedJsonCommitRevId = this.dbMetaKeyPrevPersistedJsonCommitRevId
            ?: "prev_persisted_json_commit_rev_id",

        disableSpotCheck = this.disableSpotCheck ?: false,
        disableDbPersist = this.disableDbPersist ?: false,
        disableDbPersistKey = this.disableDbPersistKey ?: false,
        dbPersistKey = this.dbPersistKey ?: UUID.randomUUID().toString(),

        isRemoveJsonAfterProcess = this.isRemoveJsonAfterProcess ?: false,

        spotCheckerTimeoutThresholdMs = this.spotCheckerTimeoutThresholdMs?.coerceIn(30_000L..300_000L) ?: 200_000L,
        bgmHealthStatus500TimeoutThresholdMs = this.bgmHealthStatus500TimeoutThresholdMs?.coerceIn(200_000L..2_000_000L)
            ?: 1_200_000,

        enableCrankerConnector = this.enableCrankerConnector ?: false,
        crankerRegUrl = this.crankerRegUrl ?: "ws://localhost:3000",
        crankerSlidingWin = this.crankerSlidingWin?.coerceIn(1..50) ?: 2,
        crankerComponent = this.crankerComponent ?: "bgm-archive-kt",

        repoList = finRepoList
    )
}
