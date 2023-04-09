package moe.nyamori.bgm.util

object ParserHelper {
    fun guessUidFromUsername(username: String?): Int? {
        return username?.let { name ->
            if (name.all { ch -> Character.isDigit(ch) }) {
                name.toInt()
            } else {
                null
            }
        }
    }

    fun getUserSign(userSignStr: String?): String? {
        return userSignStr?.let {
            if (it.startsWith("(") && it.endsWith(")")) {
                it.substring(1, it.length - 1)
            } else {
                null
            }
        }
    }

    fun getUidFromBgStyle(topPostUserBgStyle: String) = if (topPostUserBgStyle[47] == 'i') {
        null
    } else {
        var tmp = topPostUserBgStyle.substring(57)
        tmp = tmp.substring(0, tmp.indexOf(".jpg"))
        tmp.toInt()
    }
}