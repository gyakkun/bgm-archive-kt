package moe.nyamori.bgm

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

}