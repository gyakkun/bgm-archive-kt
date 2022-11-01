package moe.nyamori.bgm.model

data class GroupPost(
    var id: Int,
    var mid: Int, // Topic ID
    var uid: Int,
    var related: Int?,
    var contentHtml: String?,
    var contentBbcode: String?,
    var state: Short,
    var dateline: Long,
    var isTopPost: Boolean?,
    var user: User?
)