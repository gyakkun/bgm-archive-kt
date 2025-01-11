package moe.nyamori.bgm.config

import java.io.File

private lateinit var delegate: IConfig

object Config : IConfig by delegate {

    fun setDelegate(iConfig: IConfig) {
        delegate = iConfig
    }

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

}