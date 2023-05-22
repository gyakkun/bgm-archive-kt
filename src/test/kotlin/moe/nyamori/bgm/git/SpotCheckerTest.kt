package moe.nyamori.bgm.git

import org.junit.jupiter.api.Test
import java.util.BitSet

class SpotCheckerTest {
    @Test
    fun testMergeOldNewVisited() {
        val len = 4//00_000
        val visited = BitSet(len)
        val new = BitSet(len)
        val old = BitSet(len)

        listOf(0, 1, 2).forEach { visited.set(it) }
        listOf(1).forEach { new.set(it) }
        listOf(2, 3).forEach { old.set(it) }


        val result = SpotChecker.mergeOldNewVisited(old, new, visited)
        assert(!result.get(0))
        assert(result.get(1))
        assert(!result.get(2))
        assert(result.get(3))
    }
}