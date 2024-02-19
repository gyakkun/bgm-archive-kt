package moe.nyamori.bgm.util

import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit

object GitCommitIdHelper {
    fun RevCommit.sha1Str(): String = ObjectId.toString(this.id)

    fun RevCommit.timestampHint(): Long {
        val commitTime = this.committerIdent.whenAsInstant.toEpochMilli()
        val authorTime = this.authorIdent.whenAsInstant.toEpochMilli()
        val fallback = commitTime.coerceAtMost(authorTime)
        val split = this.fullMessage.split("|")
        return if (split.size == 1) {
            if (fullMessage.startsWith("META", ignoreCase = true)
                || fullMessage.startsWith("init", ignoreCase = true)
            ) fallback
            else {
                throw IllegalStateException("Commit message expected to be ended with unix timestamp but got: \"$fullMessage\"")
            }
        } else {
            split.last().trim().toLongOrNull() ?: fallback
        }
    }

    fun FileHistoryLookup.CommitHashAndTimestampAndMsg.timestampHint(safely: Boolean = false): Long {
        val fallback = this.commitTimeEpochMs.coerceAtMost(authorTimeEpochMs)
        val split = this.msg.split("|")
        return if (split.size == 1) {
            if (this.msg.startsWith("META", ignoreCase = true)
                || this.msg.startsWith("init", ignoreCase = true)
            ) fallback
            else if (!safely) {
                throw IllegalStateException("Commit message expected to be ended with unix timestamp but got: \"${this.msg}\"")
            } else fallback
        } else {
            split.last().trim().toLongOrNull() ?: fallback
        }
    }

    fun Repository.isJsonRepo(): Boolean = this.absolutePathWithoutDotGit().indexOf("json", ignoreCase = true) > 0
    fun Repository.isHtmlRepo(): Boolean = !this.isJsonRepo()
}