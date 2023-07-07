package moe.nyamori.bgm.util

import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.model.SpaceType

object RangeHelper {
    fun summaryRanges(nums: List<Int>): List<IntArray> {
        val result: MutableList<IntArray> = ArrayList()
        if (nums.isEmpty()) return result
        var prev = nums[0]
        var startVal = nums[0]
        val n = nums.size
        for (i in 1 until n) {
            if (nums[i] == prev + 1) {
                prev = nums[i]
            } else {
                if (startVal != prev) {
                    result.add(intArrayOf(startVal, prev))
                } else {
                    result.add(intArrayOf(prev))
                }
                prev = nums[i]
                startVal = prev
            }
        }
        if (startVal != prev) {
            result.add(intArrayOf(startVal, prev))
        } else {
            result.add(intArrayOf(prev))
        }
        return result
    }


    @JvmStatic
    fun main(argv: Array<String>) {
        val list = Dao.bgmDao().getAllTopicIdByType(SpaceType.EP.id)
        val max = list.max()
        val fake = (1..max).toMutableSet()
        fake.removeAll(list.toSet())
        val result = summaryRanges(fake.toList().sorted())
        val ng = mutableListOf<Int>()
        result.forEach {
            if (it.size == 1) {
                System.err.println(it[0])
                ng.add(it[0])
            } else {
                (it[0]..it[1]).forEach {
                    System.err.println(it)
                    ng.add(it)
                }
            }
        }
        System.err.println("Size: ${ng.size}")
    }
}