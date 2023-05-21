package moe.nyamori.bgm.model

import org.jetbrains.annotations.Nullable

data class Post(
    var id: Int,
    var user: User? = null,
    val floorNum: Int, // #{floor} Start from 1
    @Nullable
    val subFloorNum: Int? = null, // #{floor}-{subFloor} Start from 1
    var mid: Int, // Topic ID
    @Nullable
    var related: Int? = null, // Reply to some floor (sub-reply), store the floor's id
    var contentHtml: String? = null,
    @Nullable
    var contentBbcode: String? = null,
    var state: Long = STATE_NORMAL,
    var dateline: Long,
    var subFloorList: List<Post>? = null
) {
    companion object {
        const val STATE_NORMAL: Long = 0 // 0
        const val STATE_DELETED: Long = 1 shl 0 // 1
        const val STATE_CLOSED: Long = 1 shl 1 // 2
        const val STATE_SILENT: Long = 1 shl 2 // 4
        const val STATE_REOPEN: Long = 1 shl 3 // 8
        const val STATE_ADMIN_DELETED: Long = 1 shl 4 // 16
        const val STATE_BLOG_REDIRECT: Long = 1 shl 5 //32
        const val STATE_BLOG_CLUB: Long = 1 shl 6 // 64
        const val STATE_VIOLATIVE: Long = 1 shl 7 // 128
    }

    fun isNormal(): Boolean {
        return state and 1L == STATE_NORMAL
    }

    fun isDeleted(): Boolean {
        return state and STATE_DELETED == STATE_DELETED
    }

    fun isClosed(): Boolean {
        return state and STATE_CLOSED == STATE_CLOSED
    }

    fun isReopen(): Boolean {
        return state and STATE_REOPEN == STATE_REOPEN
    }

    fun isSilent(): Boolean {
        return state and STATE_SILENT == STATE_SILENT
    }

    fun isAdminDeleted(): Boolean {
        return state and STATE_ADMIN_DELETED == STATE_ADMIN_DELETED
    }

    fun isBlogClub(): Boolean {
        return state and STATE_BLOG_CLUB == STATE_BLOG_CLUB
    }

    fun isBlogRedirect(): Boolean {
        return state and STATE_BLOG_REDIRECT == STATE_BLOG_REDIRECT
    }

    fun isViolative(): Boolean {
        return state and STATE_VIOLATIVE == STATE_VIOLATIVE
    }
}