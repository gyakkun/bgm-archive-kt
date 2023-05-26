package moe.nyamori.bgm.model

data class LikeRev(
    val type: Int,
    val mid: Int,
    val pid: Int,
    val value: Int,
    val total: Int,
    val uid: Int
)


data class LikeRevUsername(
    val type: Int,
    val mid: Int,
    val pid: Int,
    val value: Int,
    val total: Int,
    val username: String,
    val nickname: String? = null
)