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
    val spaceDisplayName: String?
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
    val topicState: Long,
    val spaceDisplayName: String?
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
typealias VAllTopicCount30dRow = VAllTopicCountRow
typealias VAllTopicCount7dRow = VAllTopicCountRow
typealias VAllPostCount30dRow = VAllPostCountRow
typealias VAllPostCount7dRow = VAllPostCountRow

data class VLikeRevCountSpaceRow(
    val type: Int,
    val username: String,
    val spaceName: String,
    val spaceDisplayName: String,
    val count: Int
)

typealias VLikeCountSpaceRow = VLikeRevCountSpaceRow

data class VUserLatestLikeRevRow(
    val type: Int,
    val mid: Int,
    val pid: Int,
    val username: String,
    val faceKey: Int,
    val title: String?,
    val dateline: Long,
    val spaceDisplayName: String?
)

// For Cache query
data class RepoIdCommitId(
    val repoId: Long,
    val commitId: String
)

// Migrate from BgmDao.kt
data class MetaRow(val k: String, val v: String)

data class DeReplicaLikeRev(
    val type: Int,
    val mid: Int,
    val pid: Int,
    val value: Int
)

data class DeRepetitiveTopicData(val type: Int, val id: Int)

data class DeRepetitivePostData(val type: Int, val id: Int, val mid: Int)

data class DeRepetitiveBlogTopicData(val type: Int, val id: Int)
