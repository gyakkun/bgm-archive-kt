package moe.nyamori.bgm.git

import moe.nyamori.bgm.model.SpaceType
import org.junit.jupiter.api.Test
import java.util.BitSet

class SpotCheckerTest {
    @Test
    fun testMergeOldNewVisitedForHidden() {
        val len = 4//00_000
        val visited = BitSet(len)
        val new = BitSet(len)
        val old = BitSet(len)

        listOf(0, 1, 2).forEach { visited.set(it) }
        listOf(1).forEach { new.set(it) }
        listOf(2, 3).forEach { old.set(it) }


        val result = SpotChecker.mergeOldNewVisitedForHidden(old, new, visited)
        assert(!result.get(0))
        assert(result.get(1))
        assert(!result.get(2))
        assert(result.get(3))
    }

    @Test
    fun testHolecheck() {
        var res = SpotChecker.checkIfHolesInTopicListRange(SpaceType.BLOG, topiclist)
        assert(res.contains(322413))
    }


    val topiclist = listOf(
        322415,
        322414,
        309475,
        308773,
        322392,
        322328,
        321363,
        322306,
        322099,
        322404,
        322063,
        322373,
        322407,
        320927,
        322405,
        322406,
        322353,
        322188,
        322403,
        322402,
        322401,
        322400,
        322285,
        322399,
        322398,
        314543,
        322397,
        295446,
        321160,
        322396,
        322123,
        318363,
        321261,
        320903,
        320607,
        322390,
        320794,
        322293,
        304317,
        321896,
        322053,
        322391,
        295093,
        322263,
        313524,
        317692,
        322379,
        275771,
        322387,
        322386,

        )
}