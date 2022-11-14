package moe.nyamori.bgm.model

data class Topic(
    var id: Int,
    var space: Space? = null,
    var uid: Int? = null,
    var title: String? = null,
    var dateline: Long? = null,
    var display: Boolean = false,
    var topPostPid: Int? = null,
    var postList: List<Post>? = null
)
