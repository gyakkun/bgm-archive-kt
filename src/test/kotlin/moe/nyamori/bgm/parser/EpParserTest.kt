package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.ParserHelper
import org.junit.jupiter.api.Test

class EpParserTest {
    @Test
    fun testEpMisc() {
        for (i in listOf(
            "music",
            "anime_1",
            "anime_3",
            "anime_4",
            "not_found",
            "anime_2",
            "anime_0",
        )) {
            val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/ep/$i.html") ?: continue
            ins.use {
                System.err.println("Parsing $i")
                val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
                val (topic, success) = EpTopicParser.parseTopic(htmlString, 888, SpaceType.EP)
                assert(success)
            }
        }
    }
}