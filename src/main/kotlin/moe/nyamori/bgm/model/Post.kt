package moe.nyamori.bgm.model

import org.jetbrains.annotations.Nullable

data class Post(
    var id: Int,
    var user: User?,
    val floorNum: Int, // #{floor} Start from 1
    @Nullable
    val subFloorNum: Int?, // #{floor}-{subFloor} Start from 1
    var mid: Int, // Topic ID
    @Nullable
    var related: Int?, // Reply to some floor (sub-reply), store the floor's id
    var contentHtml: String?,
    @Nullable
    var contentBbcode: String?,
    var state: Short,
    var dateline: Long,
    var subFloorList: List<Post>?
) {
    val isTopPost: Boolean
        get() = floorNum == 1

    companion object {
        const val STATE_NORMAL: Short = 0
    }
}