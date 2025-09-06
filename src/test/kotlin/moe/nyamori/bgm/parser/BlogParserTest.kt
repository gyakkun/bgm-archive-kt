package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.blog.BlogTopicParserR398
import moe.nyamori.bgm.parser.blog.BlogTopicParserR400
import moe.nyamori.bgm.parser.blog.BlogTopicParserR649
import moe.nyamori.bgm.util.ParserHelper
import org.junit.jupiter.api.Test

class BlogParserTest {

    @Test
    fun testMisc() {
        for (i in listOf(
            "no_avatar",
            "not_found",
            "self_post",
            "doujin",
            "doujin_2",
            "no_nickname",
            "redirect"
        )) {
            val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/blog/$i.html") ?: continue
            ins.use {
                System.err.println("Parsing $i")
                val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
                val (topic, success) = BlogTopicParserR400.parseTopic(htmlString, 888, SpaceType.BLOG)
                assert(success)
            }
        }
    }

    @Test
    fun testR649() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/blog/649.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (blogPost, success) = BlogTopicParserR649.parseTopic(htmlString, 313741, SpaceType.BLOG)
            assert(success)
        }
    }

    @Test
    fun testR412() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/blog/412.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (blogPost, success) = BlogTopicParserR400.parseTopic(htmlString, 888, SpaceType.BLOG)
            assert(success)
        }
    }

    @Test
    fun testR403toR411() {
        for (i in 403..411) {
            val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/blog/$i.html") ?: continue
            ins.use {
                System.err.println("Parsing $i")
                val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
                val (topic, success) = BlogTopicParserR400.parseTopic(htmlString, 888, SpaceType.BLOG)
                assert(success)
            }
        }
    }

    @Test
    fun testR402() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/blog/402.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (blogPost, success) = BlogTopicParserR400.parseTopic(htmlString, 888, SpaceType.BLOG)
            assert(success)
        }
    }

    @Test
    fun testR400() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/blog/400.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (blogPost, success) = BlogTopicParserR400.parseTopic(htmlString, 888, SpaceType.BLOG)
            assert(success)
        }
    }

    @Test
    fun testR398() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/blog/398.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (blogPost, success) = BlogTopicParserR398.parseTopic(htmlString, 888, SpaceType.BLOG)
            assert(success)
            // System.err.println(blogPost)
        }
    }

}