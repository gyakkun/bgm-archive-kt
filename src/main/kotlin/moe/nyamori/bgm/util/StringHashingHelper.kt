package moe.nyamori.bgm.util

import kotlin.math.abs

object StringHashingHelper {
    private fun hashFunGenerator(radix: Int, mod: Int = 1000000007 /* alternative: 19260817 */): (String) -> Int {
        val res = fun(str: String): Int {
            val ca = str.toCharArray()
            var working = 0L
            // From 0 to len
            ca.forEach {
                working *= radix
                working %= mod
                working += it.code
                working %= mod
            }
            return working.toInt()
        }
        return res
    }

    private val hashFun1 = hashFunGenerator(17)
    private val hashFun2 = hashFunGenerator(19260817)
    private const val mask1 = Short.MAX_VALUE.toInt()
    private const val mask2 = Int.MAX_VALUE xor Short.MAX_VALUE.toInt()
    fun stringHash(str: String): Int {
        if (str.toIntOrNull() != null && str.toInt().toString() == str) return str.toInt()
        val hash1 = hashFun1(str) and mask1
        val hash2 = hashFun2(str) and mask2
        return -abs(hash1 xor hash2)
    }
}