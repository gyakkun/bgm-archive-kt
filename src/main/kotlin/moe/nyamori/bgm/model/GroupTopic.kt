package moe.nyamori.bgm.model

data class GroupTopic(
    var id: Int,
    var gid: Int?,
    var gname: String,
    var uid: Int,
    var title: String,
    var dateline: Long,
    var display: Boolean,
    var topPostId: Int?,
    var posts: List<GroupPost>
)