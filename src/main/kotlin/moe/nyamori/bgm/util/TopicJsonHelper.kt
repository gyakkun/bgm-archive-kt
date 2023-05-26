package moe.nyamori.bgm.util

import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.model.*
import org.slf4j.LoggerFactory
import java.util.ArrayList

object TopicJsonHelper {
    private val LOGGER = LoggerFactory.getLogger(TopicJsonHelper.javaClass)
    fun getUserListFromPostList(postList: List<Post>) =
        postList.mapNotNull { it.user }.distinct()

    fun getPostListFromTopic(topic: Topic): List<Post> {
        if (!isValidTopic(topic)) {
            emptyList<Post>()
        }
        return topic.getAllPosts().map { processPostWithEmptyUid(it) }
    }

    fun handleBlogTagAndRelatedSubject(topic: Topic) {
        // TODO: Remove not-existing tags
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
        if (topic.uid != null) return topic
        if (topic.isEmptyTopic()) {
            return topic
        }
        val topPostPid = topic.topPostPid!!
        val topPost = topic.getAllPosts().firstOrNull { it.id == topPostPid }
            ?: throw IllegalStateException("Topic type=${topic.space!!.type} id=${topic.id} has null top post!")
        var topPostUser = topPost.user
        if (topPostUser == null) {
            topPostUser = User(id = null, username = "0", nickname = "0")
        } else {
            if (topPostUser.id == null) {
                topPostUser = topPostUser.let { it.copy(id = StringHashingHelper.stringHash(it.username)) }
            }
        }
        return topic.copy(
            uid = topPostUser.id
        )
    }

    fun processPostWithEmptyUid(post: Post): Post {
        if (post.user != null && post.user!!.id != null) return post
        var postUser = post.user
        if (postUser == null) {
            postUser = User(id = null, username = "0", nickname = "0")
        } else {
            if (postUser.id == null) {
                postUser = postUser.let { it.copy(id = StringHashingHelper.stringHash(it.username)) }
            }
        }
        return post.copy(user = postUser)
    }

    fun isValidTopic(topic: Topic): Boolean {
        return topic.space != null
    }

    fun getLikeListFromTopic(topic: Topic): List<Like> {
        if (!isValidTopic(topic)) return emptyList()
        if (topic.space?.meta?.get("data_likes_list") == null) return emptyList()
        return runCatching {
            val dataLikesList = topic.space!!.meta!!["data_likes_list"] as Map<String, Any?>
            dataLikesList.map {
                val pid = it.key.toInt()
                val faces = it.value
                if (faces is Map<*, *>) {
                    val res = ArrayList<Like>()
                    (faces as Map<String, Any?>).forEach {
                        val m = it.value as Map<String, Any?>
                        val mid = (m["main_id"] as Double).toInt()
                        val value = (m["value"] as String).toInt()
                        val total = getTotal(m)
                        res.add(Like(type = topic.space!!.type.id, mid = mid, pid = pid, value = value, total = total))
                    }
                    return@map res
                } else if (faces is List<*>) {
                    val res = ArrayList<Like>()
                    (faces as List<Map<String, Any?>>).forEach {
                        val m = it
                        val mid = (m["main_id"] as Double).toInt()
                        val value = (m["value"] as String).toInt()
                        val total = getTotal(m)
                        res.add(Like(type = topic.space!!.type.id, mid = mid, pid = pid, value = value, total = total))
                    }
                    return@map res
                } else {
                    return@map emptyList<Like>()
                }
            }.flatten().toList()
        }.onFailure {
            LOGGER.error("Ex when extracting like list for topic: ${topic.space}, id-${topic.id}", it)
        }.getOrDefault(emptyList())
    }

    fun getLikeRevListFromTopic(topic: Topic): List<LikeRevUsername> {
        if (!isValidTopic(topic)) return emptyList()
        if (topic.space?.meta?.get("data_likes_list") == null) return emptyList()
        return runCatching {

            val dataLikesList = topic.space!!.meta!!["data_likes_list"] as Map<String, Any?>
            dataLikesList.map {
                val pid = it.key.toInt()
                val faces = it.value
                if (faces is Map<*, *>) {
                    val res = ArrayList<LikeRevUsername>()
                    (faces as Map<String, Any?>).forEach {
                        val m = it.value as Map<String, Any?>
                        val mid = (m["main_id"] as Double).toInt()
                        val value = (m["value"] as String).toInt()
                        val users = (m["users"] as List<Map<String, Any?>>?) ?: return@forEach
                        users.forEach {
                            if (it["username"] == null) return@forEach
                            if (it["username"]!! !is String) return@forEach
                            val username = it["username"]!! as String
                            val nickname =
                                if (it["nickname"] != null && it["nickname"] is String) it["nickname"]!! as String else null
                            res.add(
                                LikeRevUsername
                                    (
                                    type = topic.space!!.type.id,
                                    mid = mid,
                                    pid = pid,
                                    value = value,
                                    total = 1,
                                    username = username,
                                    nickname = nickname
                                )
                            )

                        }
                    }
                    return@map res
                } else {
                    return@map emptyList<LikeRevUsername>()
                }
            }.flatten().toList()
        }.onFailure {
            LOGGER.error("Ex when extracting like rev username list for topic: ${topic.space}, id-${topic.id}", it)
        }.getOrDefault(emptyList())
    }

    private fun getTotal(likeItemMap: Map<String, Any?>): Int {
        return if (likeItemMap["total"] is String) {
            (likeItemMap["total"] as String).toInt()
        } else if (likeItemMap["total"] is Double) {
            (likeItemMap["total"] as Double).toInt()
        } else if (likeItemMap["total"] is Long) {
            (likeItemMap["total"] as Long).toInt()
        } else if (likeItemMap["total"] is Int) {
            (likeItemMap["total"] as Int)
        } else {
            0 // Fallback
        }
    }
}