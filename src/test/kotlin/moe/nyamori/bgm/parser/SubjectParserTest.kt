package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.Group
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.ParserHelper
import org.junit.jupiter.api.Test

class SubjectParserTest {

    @Test
    fun testR403toR412() {
        for (i in 403..412) {
            val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/group/$i.html") ?: continue
            ins.use {
                System.err.println("Parsing $i")
                val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
                val (topic, success) = GroupTopicParserR403.parseTopic(htmlString, 888, SpaceType.GROUP)
                assert(success)
                assert((topic!!.space as Group).meta!!.containsKey("data_likes_list"))
            }
        }
    }

    @Test
    fun testR402() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/group/402.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (_, success) = GroupTopicParserR402.parseTopic(htmlString, 888, SpaceType.GROUP)
            assert(success)
        }
    }

    @Test
    fun testR400() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/subject/400.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (_, success) = SubjectTopicParserR400.parseTopic(htmlString, 888, SpaceType.SUBJECT)
            assert(success)
        }
    }

    @Test
    fun testR398() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/subject/398.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (_, success) = SubjectTopicParserR398.parseTopic(htmlString, 888, SpaceType.SUBJECT)
            assert(success)
        }
    }

}