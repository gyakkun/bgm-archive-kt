package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.mono.CharacterTopicParserR430
import moe.nyamori.bgm.util.ParserHelper
import org.junit.jupiter.api.Test

class CharacterParserTest {
    @Test
    fun testCharacterMisc() {
        for (i in listOf(
            "bad_date_after_tidy",
            "character_1",
            "not_found",
        )) {
            val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/character/$i.html") ?: continue
            ins.use {
                System.err.println("Parsing $i")
                val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
                val (topic, success) = CharacterTopicParser.parseTopic(htmlString, 888, SpaceType.CHARACTER)
                assert(success)
            }
        }
    }
}