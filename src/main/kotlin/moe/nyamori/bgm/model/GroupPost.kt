package moe.nyamori.bgm.model

data class GroupPost(
    var id: Int,
    val floor: Int, // #{floor} Start from 1
    val subFloor: Int?, // #{floor}-{subFloor} Start from 1
    var mid: Int, // Topic ID
    var uid: Int,
    var related: Int?, // Reply to some floor (sub-reply), store the floor's id
    var contentHtml: String?,
    var contentBbcode: String?,
    var state: Short,
    var dateline: Long,
    var user: User?
) {
    val isTopPost: Boolean
        get() = id == 1
}