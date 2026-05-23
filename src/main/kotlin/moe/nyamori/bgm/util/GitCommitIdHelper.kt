package moe.nyamori.bgm.util

import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.IGitCommit
import org.eclipse.jgit.lib.Repository

object GitCommitIdHelper {

    fun IGitCommit.timestampHint(safely: Boolean = false): Long {
        val fallback = this.commitTimeEpochMs.coerceAtMost(this.authorTimeEpochMs)
        val split = this.fullMessage.split("|")
        return if (split.size == 1) {
            if (this.fullMessage.startsWith("META", ignoreCase = true)
                || this.fullMessage.startsWith("init", ignoreCase = true)
            ) fallback
            else if (!safely) {
                throw IllegalStateException("Commit message expected to be ended with unix timestamp but got: \"${this.fullMessage}\"")
            } else fallback
        } else {
            split.last().trim().toLongOrNull() ?: fallback
        }
    }

    fun Repository.isJsonRepo(): Boolean = this.absolutePathWithoutDotGit().indexOf("json", ignoreCase = true) > 0
    fun Repository.isHtmlRepo(): Boolean = !this.isJsonRepo()
}