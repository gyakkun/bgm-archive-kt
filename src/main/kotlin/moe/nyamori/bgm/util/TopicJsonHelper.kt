package moe.nyamori.bgm.util

import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.model.*
import java.util.ArrayList

object TopicJsonHelper {
    const val TITLE_MAX_LENGTH = 100
    fun getUserListFromTopic(postList: List<Post>) =
        postList.mapNotNull { it.user }.distinct()

    fun getPostListFromTopic(topic: Topic): List<Post> {
        if (!isValidTopic(topic)) {
            emptyList<Post>()
        }
        return topic.getAllPosts().map { processPostWithEmptyUid(it) }
    }

    fun handleBlogTagAndRelatedSubject(topic: Topic) {
        if (!isValidTopic(topic)) return
        if (topic.space!!.type != SpaceType.BLOG) return
        val blogSpace = topic.space!! as Blog
        val relatedSubjectIds = blogSpace.relatedSubjectIds
        val tags = blogSpace.tags
        if (!relatedSubjectIds.isNullOrEmpty()) {
            Dao.bgmDao().upsertBlogSubjectIdMapping(relatedSubjectIds.map { Pair(topic.id, it) })
        }
        if (!tags.isNullOrEmpty()) {
            Dao.bgmDao().upsertBlogTagMapping(tags.map { Pair(topic.id, it) })
        }
    }

    fun preProcessTopic(topic: Topic): Topic {
        if (topic.uid != null) {
            return topic.copy(title = topic.title?.substring(0, topic.title!!.length.coerceAtMost(TITLE_MAX_LENGTH)))
        }
        if (topic.isEmptyTopic()) {
            return topic.copy(uid = 0, dateline = 0)
        }
        val topPostPid = topic.topPostPid!!
        val topPost = topic.getAllPosts().first { it.id == topPostPid }
        var topPostUser = topPost.user
        if (topPostUser == null) {
            topPostUser = User(id = 0, username = "0", nickname = "0")
        } else {
            if (topPostUser.id == null) {
                topPostUser = topPostUser.let { it.copy(id = StringHashingHelper.stringHash(it.username)) }
            }
        }
        return topic.copy(
            uid = topPostUser.id,
            title = topic.title?.substring(0, topic.title!!.length.coerceAtMost(TITLE_MAX_LENGTH))
        )
    }

    fun processPostWithEmptyUid(post: Post): Post {
        if (post.user != null && post.user!!.id != null) return post
        var postUser = post.user
        if (postUser == null) {
            postUser = User(id = 0, username = "0", nickname = "0")
        } else {
            if (postUser.id == null) {
                postUser = postUser.let { it.copy(id = StringHashingHelper.stringHash(it.username)) }
            }
        }
        return post.copy(user = postUser)
    }

    fun isValidTopic(topic: Topic): Boolean {
        return topic.space != null
        // && (topic.space!!.type == SpaceType.GROUP || topic.space!!.type == SpaceType.SUBJECT)
    }

    fun getLikeListFromTopic(topic: Topic): List<Like> {
        if (!isValidTopic(topic)) return emptyList()

        if (topic.space?.meta?.get("data_likes_list") == null) return emptyList()
        val dataLikesList = topic.space!!.meta!!["data_likes_list"] as Map<String, Any?>
        return dataLikesList.map {
            val pid = it.key.toInt()
            val faces = it.value
            if (faces is Map<*, *>) {
                val res = ArrayList<Like>()
                (faces as Map<String, Any?>).forEach {
                    val faceId = it.key.toInt()
                    val m = it.value as Map<String, Any?>
                    val mid = (m["main_id"] as Double).toInt()
                    val value = (m["value"] as String).toInt()
                    val total = (m["total"] as String).toInt()
                    res.add(Like(type = topic.space!!.type.id, mid = mid, pid = pid, value = value, total = total))
                }
                return@map res
            } else if (faces is List<*>) {
                val res = ArrayList<Like>()
                (faces as List<Map<String, Any?>>).forEach {
                    val m = it
                    val mid = (m["main_id"] as Double).toInt()
                    val value = (m["value"] as String).toInt()
                    val total = (m["total"] as String).toInt()
                    res.add(Like(type = topic.space!!.type.id, mid = mid, pid = pid, value = value, total = total))
                }
                return@map res
            } else {
                return@map emptyList<Like>()
            }
        }.flatten().toList()
    }
}