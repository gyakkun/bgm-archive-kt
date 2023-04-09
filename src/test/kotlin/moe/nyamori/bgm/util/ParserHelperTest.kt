package moe.nyamori.bgm.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.text.Charsets.UTF_8

class ParserHelperTest {
    @Test
    fun getStyleRevNumberFromHtmlString() {
        for (i in 398..412) {
            val ins = ParserHelper.javaClass.getResourceAsStream("/htm_samples/group/$i.html") ?: continue
            ins.use {
                val htmlString = String(it.readAllBytes() , UTF_8)
                assertEquals(i, ParserHelper.getStyleRevNumberFromHtmlString(htmlString))
            }
        }
    }


}