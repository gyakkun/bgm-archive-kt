package moe.nyamori.bgm.config

import java.io.File

object Config {

    var BGM_ARCHIVE_GIT_REPO_DIR: String = System.getenv().getOrDefault(
        "E_BGM_ARCHIVE_GIT_REPO",
        File(System.getProperty("user.home")).resolve("source/bgm-archive").absolutePath
    )

    var BGM_ARCHIVE_JSON_GIT_REPO_DIR: String = System.getenv().getOrDefault(
        "E_BGM_ARCHIVE_JSON_GIT_REPO",
        File(System.getProperty("user.home")).resolve("source/bgm-archive-json").absolutePath
    )

    var BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME: String = System.getenv().getOrDefault(
        "E_BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME",
        "last_processed_commit_rev_id"
    )

    var BGM_ARCHIVE_PREFER_JGIT: Boolean =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_PREFER_JGIT", "false").toBoolean()

    var BGM_ARCHIVE_DISABLE_HOOK: Boolean =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_DISABLE_HOOK", "false").toBoolean()

    var BGM_ARCHIVE_ADDRESS: String =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_ADDRESS", "localhost")

    var BGM_ARCHIVE_PORT: Int =
        System.getenv().getOrDefault("E_BGM_ARCHIVE_PORT", "5926").toInt()
}