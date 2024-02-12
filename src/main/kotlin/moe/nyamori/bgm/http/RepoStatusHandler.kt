package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.CommitToJsonProcessor.blockAndPrintProcessResults
import java.io.File

object RepoStatusHandler : Handler {
    override fun handle(ctx: Context) {
        ctx.json(object {
            val gitRepositories = mutableListOf<String>().apply {
                add(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR)
                add(Config.BGM_ARCHIVE_GIT_REPO_DIR)
                addAll(Config.BGM_ARCHIVE_GIT_STATIC_REPO_DIR_LIST.split(","))
                addAll(Config.BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST.split(","))
            }
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .map { File(it) }
                .associate {
                    it.path.split(File.separator).last() to run {
                        val gitProcess = Runtime.getRuntime()
                            .exec("git count-objects -vH", null, it)
                        gitProcess.blockAndPrintProcessResults().map {
                            it.split(":")
                        }.associate { Pair(it[0].trim(), it[1].trim()) }
                    }
                }
        })
    }
}