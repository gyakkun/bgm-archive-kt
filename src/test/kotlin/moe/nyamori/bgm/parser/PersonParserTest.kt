package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.ParserHelper
import org.junit.jupiter.api.Test

class PersonParserTest {
    @Test
    fun testPersonMisc() {
        for (i in listOf(
            "person_1",
            "not_found",
        )) {
            val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/person/$i.html") ?: continue
            ins.use {
                System.err.println("Parsing $i")
                val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
                val (topic, success) = PersonTopicParser.parseTopic(htmlString, 888, SpaceType.PERSON)
                assert(success)
            }
        }
    }
}