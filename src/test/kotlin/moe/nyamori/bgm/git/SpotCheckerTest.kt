package moe.nyamori.bgm.git

import moe.nyamori.bgm.model.SpaceType
import org.junit.jupiter.api.Test
import java.util.BitSet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals

class SpotCheckerTest {

    @AfterEach
    fun tearDown() {
        SpotChecker.clearHoleCheckedSetByType(SpaceType.BLOG)
        SpotChecker.clearHoleCheckedSetByType(SpaceType.EP)
        SpotChecker.clearHoleCheckedSetByType(SpaceType.SUBJECT)
        SpotChecker.clearHoleCheckedSetByType(SpaceType.GROUP)
    }

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
        assertFalse(result.get(0))
        assertTrue(result.get(1))
        assertFalse(result.get(2))
        assertTrue(result.get(3))
    }

    @Test
    fun testHolecheck() {
        var res = SpotChecker.checkIfHolesInTopicListRange(SpaceType.BLOG, topiclist)
        assertTrue(res.contains(322413))
    }

    @Test
    fun testHolecheckNoHoles() {
        val noHolesList = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val res = SpotChecker.checkIfHolesInTopicListRange(SpaceType.BLOG, noHolesList)
        assertTrue(res.isEmpty())
    }

    @Test
    fun testHolecheckEmptyList() {
        val res = SpotChecker.checkIfHolesInTopicListRange(SpaceType.BLOG, emptyList())
        assertTrue(res.isEmpty())
    }
    
    @Test
    fun testHolecheckSkipType() {
        // SpaceType.EP is in HOLE_CHECK_SKIP_TYPE
        val res = SpotChecker.checkIfHolesInTopicListRange(SpaceType.EP, topiclist)
        assertTrue(res.isEmpty())
    }
    
    @Test
    fun testHoleCheckLimit() {
        // Create 26 holes (limit is 25)
        val listWithManyHoles = mutableListOf<Int>()
        for (i in 1..26) {
            listWithManyHoles.add(i * 2) // Holes are 1, 3, 5...
        }
        
        var res = SpotChecker.checkIfHolesInTopicListRange(SpaceType.SUBJECT, listWithManyHoles)
        
        // Next check should clear the limit 
        res = SpotChecker.checkIfHolesInTopicListRange(SpaceType.SUBJECT, listWithManyHoles)
        assertTrue(res.isNotEmpty())
    }

    @Test
    fun testGetBitsetFromLongListStr() {
        val expected = BitSet()
        expected.set(1)
        expected.set(64)
        val longArray = expected.toLongArray()
        val str = longArray.joinToString("\n")
        val actual = SpotChecker.getBitsetFromLongListStr(str)
        assertEquals(expected, actual)
    }

    @Test
    fun testGetBitsetFromLongListStrEmpty() {
        val expected = BitSet()
        val str = ""
        val actual = SpotChecker.getBitsetFromLongListStr(str)
        assertEquals(expected, actual)
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