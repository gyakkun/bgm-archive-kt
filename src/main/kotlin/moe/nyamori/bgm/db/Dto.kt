package moe.nyamori.bgm.db

import moe.nyamori.bgm.model.Like
import moe.nyamori.bgm.model.LikeRev

// For table query
data class SpaceNameMappingData(val type: Int, val sid: Int, val name: String, val displayName: String)

data class PostRow(
    val type: Int,
    val id: Int,
    val mid: Int,
    val uid: Int,
    val dateline: Long,
    val state: Long,
    val sid: Int
)

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
typealias LikeRevRow = LikeRev

data class UserRow(
    val id: Int,
    val username: String,
    val nickname: String?
)

// For view query
data class VAllPostCountRow(
    val type: Int,
    val uid: Int,
    val username: String,
    val state: Long,
    val count: Int
)

data class VLikesSumRow(
    val type: Int,
    val faceKey: Int,
    // val uid: Int,
    val username: String,
    val count: Int
)

data class VUserLatestCreateTopicRow(
    val type: Int,
    val id: Int,
    val uid: Int,
    val sid: Int,
    val dateline: Long,
    val state: Long,
    val lastPostPid: Int,
    val title: String?,
    val lastUpdateTime: Long,
    val username: String,
    val rankLastReply: Int,
    // val rankDateline: Int
)

data class VUserLastReplyTopicRow(
    val type: Int,
    val id: Int,
    val mid: Int,
    val uid: Int,
    val dateline: Long,
    val state: Long,
    val title: String?,
    val username: String,
    val rankReplyAsc: Int,
    val topicState: Long
)

data class VPostCountSpaceRow(
    val type: Int,
    val uid: Int,
    val name: String,
    val displayName: String,
    val username: String,
    val state: Long,
    val count: Int
)

typealias VAllTopicCountRow = VAllPostCountRow
typealias VTopicCountSpaceRow = VPostCountSpaceRow