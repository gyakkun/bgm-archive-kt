package moe.nyamori.bgm.util

import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.model.SpaceType

object RangeHelper {
    fun summaryRanges(nums: List<Int>): List<IntArray> {
        val result = mutableListOf<IntArray>()
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
        val spaceType = if (argv.isNotEmpty()) kotlin.runCatching { SpaceType.valueOf(argv[0].uppercase()) }
            .getOrDefault(SpaceType.BLOG) else SpaceType.BLOG
        val ng = checkHolesForType(spaceType)
        for (i in ng) {
            System.err.println(i)
            println(i)
        }
        System.err.println("Size: ${ng.size}")
    }

    fun checkHolesForType(st: SpaceType): List<Int> {
        // Concrete type for better performance?
        var allTopicId: ArrayList<Int>? = Dao.bgmDao.getAllTopicIdByType(st.id)
        val max = Dao.bgmDao.getMaxTopicIdByType(st.id)
        var real: HashSet<Int>? = HashSet<Int>(allTopicId!!.size).apply { allTopicId!!.forEach { add(it) } }
        allTopicId!!.clear();
        allTopicId = null // help gc?
        System.gc()
        val fake = HashSet<Int>().apply { for (c in 1..max) add(c) }
        fake.removeAll(real!!)
        real.clear()
        real = null
        System.gc()
        val result = summaryRanges(fake.sorted())
        val ng = mutableListOf<Int>()
        result.forEach { topicId ->
            if (topicId.size == 1) {
                ng.add(topicId[0])
            } else {
                (topicId[0]..topicId[1]).forEach {
                    ng.add(it)
                }
            }
        }
        return ng
    }
}