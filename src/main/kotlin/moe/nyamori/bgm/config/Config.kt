package moe.nyamori.bgm.config

import java.io.File
import java.io.FileWriter
import java.util.*

object Config {

    // For bare repo
    // Please run: git config remote.origin.fetch 'refs/heads/*:refs/heads/*'
    // to make fetch same as pull

    val BGM_ARCHIVE_GIT_REPO_DIR: String = System.getenv().getOrDefault(
        "E_BGM_ARCHIVE_GIT_REPO",
        File(System.getProperty("user.home")).resolve("source/bgm-archive").absolutePath
    )

    val BGM_ARCHIVE_JSON_GIT_REPO_DIR: String = System.getenv().getOrDefault(
        "E_BGM_ARCHIVE_JSON_GIT_REPO",
        File(System.getProperty("user.home")).resolve("source/bgm-archive-json").absolutePath
    )

    // Comma seperated
    val BGM_ARCHIVE_GIT_STATIC_REPO_DIR_LIST: String = System.getenv().getOrDefault(
        "E_BGM_ARCHIVE_GIT_STATIC_REPO_DIR_LIST", ""
    )

    val BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST: String = System.getenv().getOrDefault(
        "E_BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST", ""
    )

    val BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME: String = System.getenv().getOrDefault(
        "E_BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME",
        "last_processed_commit_rev_id"
    )

    val BGM_ARCHIVE_PREFER_JGIT: Boolean =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_PREFER_JGIT", "false").toBoolean()

    val BGM_ARCHIVE_PREFER_GIT_BATCH_ADD: Boolean =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_PREFER_GIT_BATCH_ADD", "false").toBoolean()

    val BGM_ARCHIVE_DISABLE_HOOK: Boolean =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_DISABLE_HOOK", "false").toBoolean()

    val BGM_ARCHIVE_ADDRESS: String =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_ADDRESS", "localhost")

    val BGM_ARCHIVE_PORT: Int =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_PORT", "5926").toInt()

    val BGM_ARCHIVE_SQLITE_FILE: String =
        System.getProperty(
            "E_BGM_ARCHIVE_SQLITE_FILE",
            System.getenv().getOrDefault(
                "E_BGM_ARCHIVE_SQLITE_FILE",
                File(System.getProperty("user.home")).resolve("source/bgm-archive-db/bgm-archive.sqlite").absolutePath
            )
        )

    val BGM_ARCHIVE_DB_META_KEY_PREV_PERSISTED_JSON_COMMIT_REV_ID: String = System.getenv().getOrDefault(
        "E_BGM_ARCHIVE_DB_META_KEY_PREV_PERSISTED_JSON_COMMIT_REV_ID",
        "prev_persisted_json_commit_rev_id"
    )

    val BGM_ARCHIVE_DB_META_KEY_PREV_CACHED_COMMIT_REV_ID: String = System.getenv().getOrDefault(
        "E_BGM_ARCHIVE_DB_META_KEY_PREV_CACHED_COMMIT_REV_ID",
        "prev_cached_commit_rev_id"
    )

    val BGM_ARCHIVE_DISABLE_SPOT_CHECK: Boolean =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_DISABLE_SPOT_CHECK", "false").toBoolean()

    val BGM_ARCHIVE_DISABLE_DB_PERSIST: Boolean =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_DISABLE_DB_PERSIST", "false").toBoolean()

    val BGM_ARCHIVE_DB_PERSIST_KEY: String =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_DB_PERSIST_KEY",
            UUID.randomUUID().toString()
                .also { key ->
                    runCatching {
                        System.err.println("############ DB PERSIST KEY: $key ############")
                        val dbFile = File(BGM_ARCHIVE_SQLITE_FILE)
                        val folder = dbFile.parentFile
                        if (folder.exists()) folder.mkdirs()
                        val keyfile = folder.resolve("db-persist-key")
                        if (!keyfile.exists()) keyfile.createNewFile()
                        FileWriter(keyfile).use { fw ->
                            fw.write(key)
                            fw.flush()
                        }
                    }.onFailure {
                        System.err.println("Error when writing key file!")
                        it.printStackTrace()
                    }
                })

    val BGM_ARCHIVE_HOW_MANY_COMMIT_ON_GITHUB_PER_DAY: Int =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_HOW_MANY_COMMIT_ON_GITHUB_PER_DAY", "500").toIntOrNull() ?: 500

    val BGM_ARCHIVE_SPOT_CHECKER_TIMEOUT_THRESHOLD_MS: Long =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_SPOT_CHECKER_TIMEOUT_THRESHOLD_MS", "200000").toLongOrNull() ?: 200_000 // 200s
}