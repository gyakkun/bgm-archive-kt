package moe.nyamori.bgm.model

import moe.nyamori.bgm.util.StringHashingHelper

data class Topic(
        var id: Int,
        var space: Space? = null,
        var uid: Int? = null,
        var title: String? = null,
        var dateline: Long? = null,
        var display: Boolean = false,
        var topPostPid: Int? = null,
        var state: Long = Post.STATE_NORMAL,
        var postList: List<Post>? = null
) {
    fun getSid(): Int? {
        if (this.space == null) {
            throw IllegalStateException("Not able to get sid for null space!")
        }
        if (this.isEmptyTopic()) return null
        return when (space!!) {
            is Subject -> {
                StringHashingHelper.stringHash((space!! as Subject).name!!)
            }

            is Group -> {
                StringHashingHelper.stringHash((space!! as Group).name!!)
            }

            is Blog -> {
                uid!!
            }

            is Reserved -> {
                throw IllegalStateException("Not able to get sid for reserved space!")
            }
        }
    }

    fun getAllPosts(): List<Post> {
        if (this.postList == null) return emptyList()
        val result = ArrayList<Post>()
        this.postList!!.forEach {
            result.add(it)
            if (it.subFloorList != null) {
                result.addAll(it.subFloorList!!)
            }
        }
        return result
    }

    fun getLastPostPid(): Int? {
        val allPosts = getAllPosts()
        if (allPosts.isEmpty()) return null
        return allPosts.maxBy { it.id }.id
    }

    fun isNormal(): Boolean {
        return state and 1L == Post.STATE_NORMAL
    }

    fun isDeleted(): Boolean {
        return state and Post.STATE_DELETED == Post.STATE_DELETED
    }

    fun isClosed(): Boolean {
        return state and Post.STATE_CLOSED == Post.STATE_CLOSED
    }

    fun isReopen(): Boolean {
        return state and Post.STATE_REOPEN == Post.STATE_REOPEN
    }

    fun isSilent(): Boolean {
        return state and Post.STATE_SILENT == Post.STATE_SILENT
    }

    fun isAdminDeleted(): Boolean {
        return state and Post.STATE_ADMIN_DELETED == Post.STATE_ADMIN_DELETED
    }

    fun isBlogClub(): Boolean {
        return state and Post.STATE_BLOG_CLUB == Post.STATE_BLOG_CLUB
    }

    fun isBlogRedirect(): Boolean {
        return state and Post.STATE_BLOG_REDIRECT == Post.STATE_BLOG_REDIRECT
    }

    fun isEmptyTopic(): Boolean {
        return isBlogClub() || isBlogRedirect() || isDeleted()
                || postList.isNullOrEmpty()
                || topPostPid == null || (topPostPid!! == 0) || (topPostPid!! == -1)
    }
}

