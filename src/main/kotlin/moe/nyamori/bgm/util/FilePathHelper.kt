package moe.nyamori.bgm.util

import java.lang.IllegalArgumentException

object FilePathHelper {
    fun numberToPath(id: Int): String {
        if (id < 0) {
            throw IllegalArgumentException("Topic id should be greater or larger than zero!")
        }
        val tenThousand = id / 10000
        val hundred = (id % 10000) / 100
        return "%02d/%02d/$id".format(tenThousand, hundred)
    }
}
