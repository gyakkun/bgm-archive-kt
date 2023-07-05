package moe.nyamori.bgm.util

import kotlin.math.ceil
import kotlin.math.ln

object BinarySearchHelper {

    @JvmStatic
    fun main(args: Array<String>) {
        val list = (1..100 step 3).toList()
        System.err.println(list)
        val bsFun = binarySearchFunctionGenerator<Int>()
        val result = bsFun(list) { it >= 10 }
        System.err.println(result)
    }


    enum class BSType {
        CEILING,
        FLOOR
    }

    fun <T> binarySearchFunctionGenerator(
        bsType: BSType = BSType.FLOOR
    ): (List<T>, (T) -> Boolean) -> T? {
        return fun(list: List<T>, judge: (T) -> Boolean): T? {
            if (list.isEmpty()) return null
            if (list !is RandomAccess) {
                System.err.println("WARNING: Binary searching a non-random-accessible collection may result in poor performance.")
            }
            if (list.size == 1) return when (judge(list[0])) {
                true -> list[0]
                else -> null
            }
            var lo = 0
            var hi = list.size - 1
            var mid = 0
            var ctr = 0
            val loopMaxLimit = ceil(ln(list.size.toDouble()) / ln(2.0)) + 1
            var result = list[mid]
            while (lo < hi) {
                ctr++
                if (ctr > loopMaxLimit) {
                    throw InfiniteLoopException("Loop counter $ctr is over loop max limit $loopMaxLimit!")
                }
                mid = lo + (hi - lo + (if (bsType == BSType.CEILING) 1 else 0)) / 2
                if (judge(list[mid])) {
                    if (bsType == BSType.CEILING) {
                        lo = mid
                    } else { // FLOOR, EXACT
                        hi = mid
                    }
                } else {
                    if (bsType == BSType.CEILING) {
                        hi = mid - 1
                    } else { // FLOOR, EXACT
                        lo = mid + 1
                    }
                }
            }
            if (bsType == BSType.CEILING) {
                if (!judge(list[lo])) return null
                return list[lo]
            } else if (bsType == BSType.FLOOR) {
                if (!judge(list[hi])) return null
                return list[hi]
            }
            return null
        }
    }

    class InfiniteLoopException(msg: String) : Throwable()
}

