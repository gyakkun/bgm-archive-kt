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
    var postList: List<Post>? = null,
    var state: Long = Post.STATE_NORMAL
) {
    fun getSid(): Int {
        if (this.space == null) {
            throw IllegalStateException("Not able to get sid for null space!")
        }
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
}

fun Topic.getAllPosts(): List<Post> {
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
