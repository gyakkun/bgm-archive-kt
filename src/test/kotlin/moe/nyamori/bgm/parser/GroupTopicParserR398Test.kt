package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.ParserHelper
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import kotlin.text.Charsets.UTF_8

class GroupTopicParserR398Test {
    @Test
    fun parseTopic() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/group/398.html")
            ?: throw FileNotFoundException("Not found 398.html for group parser!")
        ins.use {
            val htmlString = String(it.readAllBytes(), UTF_8)
            val (_, success) = GroupTopicParserR398.parseTopic(htmlString, 888, SpaceType.GROUP)
            assert(success)
        }
    }
}