package moe.nyamori.bgm.model

import moe.nyamori.bgm.util.StringHashingHelper

data class User(
    var id: Int?,
    var username: String,
    var nickname: String? = null,
    var sign: String? = null
) {
    fun getId(): Int {
        if (id != null) return id!!
        return StringHashingHelper.stringHash(username)
    }
}
