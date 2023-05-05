package moe.nyamori.bgm.model

import org.jetbrains.annotations.Nullable

data class Post(
    var id: Int,
    var user: User? = null,
    val floorNum: Int, // #{floor} Start from 1
    @Nullable
    val subFloorNum: Int? = null, // #{floor}-{subFloor} Start from 1
    var mid: Int, // Topic ID
    @Nullable
    var related: Int? = null, // Reply to some floor (sub-reply), store the floor's id
    var contentHtml: String? = null,
    @Nullable
    var contentBbcode: String? = null,
    var state: Long = STATE_NORMAL,
    var dateline: Long,
    var subFloorList: List<Post>? = null
) {
    companion object {
        const val STATE_NORMAL: Long = 0
        const val STATE_DELETED: Long = 1 shl 0
        const val STATE_CLOSED: Long = 1 shl 1
        const val STATE_SILENT: Long = 1 shl 2
        const val STATE_REOPEN: Long = 1 shl 3
        const val STATE_ADMIN_DELETED: Long = 1 shl 4
    }
}