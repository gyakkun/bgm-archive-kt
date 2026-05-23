package moe.nyamori.bgm.git

import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit

interface ISlimGitCommit {
    val sha1: String
    val fullMessage: String
    val shortMessage: String get() = fullMessage.lines().firstOrNull() ?: ""
}

interface IGitCommit : ISlimGitCommit {
    val commitTimeEpochMs: Long
    val authorTimeEpochMs: Long
}

class JGitCommitAdapter(val revCommit: RevCommit) : IGitCommit {
    override val sha1: String
        get() = ObjectId.toString(revCommit.id)
    override val fullMessage: String
        get() = revCommit.fullMessage.trimEnd('\n')
    override val commitTimeEpochMs: Long
        get() = revCommit.committerIdent.whenAsInstant.toEpochMilli()
    override val authorTimeEpochMs: Long
        get() = revCommit.authorIdent.whenAsInstant.toEpochMilli()
    
    // allow checking equality based on sha1
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IGitCommit) return false
        return sha1 == other.sha1
    }

    override fun hashCode(): Int {
        return sha1.hashCode()
    }
}

data class GitExtCommit(
    override val sha1: String,
    override val fullMessage: String,
    override val commitTimeEpochMs: Long,
    override val authorTimeEpochMs: Long
) : IGitCommit {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IGitCommit) return false
        return sha1 == other.sha1
    }

    override fun hashCode(): Int {
        return sha1.hashCode()
    }
}
