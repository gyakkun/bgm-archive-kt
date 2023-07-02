package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.character.CharacterTopicParserR430
import moe.nyamori.bgm.util.ParserHelper
import org.junit.jupiter.api.Test

class CharacterParserTest {
    @Test
    fun testEpMisc() {
        for (i in listOf(
            "character_1",
            "not_found",
        )) {
            val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/character/$i.html") ?: continue
            ins.use {
                System.err.println("Parsing $i")
                val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
                val (topic, success) = CharacterTopicParserR430.parseTopic(htmlString, 888, SpaceType.CHARACTER)
                assert(success)
            }
        }
    }
}