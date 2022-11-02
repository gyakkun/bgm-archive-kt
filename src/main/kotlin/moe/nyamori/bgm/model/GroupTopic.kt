package moe.nyamori.bgm.model

data class GroupTopic(
    var id: Int,
    var group: Group?,
    var uid: Int?,
    var title: String?,
    var dateline: Long?,
    var display: Boolean?,
    var topPostPid: Int?,
    var postList: List<GroupPost>?
)