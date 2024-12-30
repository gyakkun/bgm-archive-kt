package moe.nyamori.bgm.util

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.checkAndGetConfigDto
import moe.nyamori.bgm.db.DSProvider
import moe.nyamori.bgm.db.DaoHolder
import moe.nyamori.bgm.db.IBgmDao
import moe.nyamori.bgm.model.SpaceType

class RangeHelper(
    private val bgmDao: IBgmDao
) {
    fun checkHolesForType(st: SpaceType): List<Int> {
        // Concrete type for better performance?
        var allTopicId: ArrayList<Int>? = bgmDao.getAllTopicIdByType(st.id)
        val max = bgmDao.getMaxTopicIdByType(st.id)
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

    companion object {
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
            val baktConfig = checkAndGetConfigDto()
            Config.setConfigDtoDelegate(baktConfig)
            val dsProvider = DSProvider(
                baktConfig.jdbcUrl,
                baktConfig.jdbcUsername,
                baktConfig.jdbcPassword,
                baktConfig.hikariMinIdle,
                baktConfig.hikariMaxConn,
                baktConfig.dbIsEnableWal
            )
            val daoHolder = DaoHolder(dsProvider)
            daoHolder.runFlyway()
            val rh = RangeHelper(daoHolder.bgmDao)
            val spaceType = if (argv.isNotEmpty()) kotlin.runCatching { SpaceType.valueOf(argv[0].uppercase()) }
                .getOrDefault(SpaceType.BLOG) else SpaceType.BLOG
            val ng = rh.checkHolesForType(spaceType)
            for (i in ng) {
                System.err.println(i)
                println(i)
            }
            System.err.println("Size: ${ng.size}")
        }
    }

}