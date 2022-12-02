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
    var state: Short,
    var dateline: Long,
    var subFloorList: List<Post>? = null
) {
    val isTopPost: Boolean
        get() = floorNum == 1

    companion object {
        const val STATE_NORMAL: Short = 0
        const val STATE_REOPEN: Short = 11
        const val STATE_CLOSED: Short = 1
        const val STATE_SILENT: Short = 5
    }
}