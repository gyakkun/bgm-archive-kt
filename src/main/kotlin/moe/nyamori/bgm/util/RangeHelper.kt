package moe.nyamori.bgm.util

import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.model.SpaceType

object RangeHelper {
    fun summaryRanges(nums: List<Int>): List<IntArray> {
        val result: MutableList<IntArray> = ArrayList()
        if (nums.isEmpty()) return result
        var prev = nums[0]
        var rangeLeft = nums[0] // the left side of the range interval (inclusive)
        val n = nums.size
        for (i in 1 until n) {
            val cur = nums[i]
            if (cur == prev + 1) { // if it's incremented by one <=> continuous for current one
                prev = cur
            } else {               // otherwise, it's continuous until the previous one
                if (rangeLeft != prev) {
                    result.add(intArrayOf(rangeLeft, prev))
                } else {
                    result.add(intArrayOf(prev))
                }
                prev = cur
                rangeLeft = cur
            }
        }
        if (rangeLeft != prev) {
            result.add(intArrayOf(rangeLeft, prev))
        } else {
            result.add(intArrayOf(prev))
        }
        return result
    }


    @JvmStatic
    fun main(argv: Array<String>) {
        val st = if (argv.isNotEmpty()) kotlin.runCatching {  SpaceType.valueOf(argv[0].uppercase()) }.getOrDefault(SpaceType.BLOG)
                 else SpaceType.BLOG
        val list = Dao.bgmDao().getAllTopicIdByType(st.id).toSet()
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
                    println(it)
                    System.err.println(it)
                    ng.add(it)
                }
            }
        }
        System.err.println("Size: ${ng.size}")
    }
}