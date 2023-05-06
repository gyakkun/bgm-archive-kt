package moe.nyamori.bgm.db

import moe.nyamori.bgm.model.Like

data class SpaceNameMappingData(val type: Int, val sid: Int, val name: String, val displayName: String)

data class PostRow(val type: Int, val id: Int, val mid: Int, val uid: Int, val dateline: Long, val state: Long)

data class TopicRow(
    val type: Int,
    val id: Int,
    val uid: Int,
    val sid: Int,
    val dateline: Long,
    val state: Long,
    val lastPostPid: Int?,
    val title: String?
)

typealias LikeRow = Like
data class UserRow(
    val id: Int,
    val username: String
)
