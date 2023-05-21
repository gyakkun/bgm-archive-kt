package moe.nyamori.bgm.util

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
}