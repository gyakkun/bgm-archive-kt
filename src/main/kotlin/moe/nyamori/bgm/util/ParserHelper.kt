package moe.nyamori.bgm.util

import moe.nyamori.bgm.model.Reserved
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic
import org.seimicrawler.xpath.JXNode
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*

object ParserHelper {
    private val LOGGER = LoggerFactory.getLogger(ParserHelper.javaClass)
    fun guessUidFromUsername(username: String?): Int? {
        return username?.let { name ->
            if (name.all { ch -> Character.isDigit(ch) }) {
                name.toInt()
            } else {
                null
            }
        }
    }

    fun getUserSign(userSignStr: String?): String? {
        return userSignStr?.let {
            if (it.startsWith("(") && it.endsWith(")")) {
                it.substring(1, it.length - 1)
            } else {
                null
            }
        }
    }

    fun getUidFromBgStyle(topPostUserBgStyle: String) = if (topPostUserBgStyle[47] == 'i') {
        null
    } else {
        var tmp = topPostUserBgStyle.substring(57)
        tmp = tmp.substring(0, tmp.indexOf(".jpg"))
        tmp.toInt()
    }

    private val CSS_REV_REGEX = Regex("css\\?r(\\d+)+")
    private const val DEFAULT_REV = 398
    fun getStyleRevNumberFromHtmlString(htmlString: String): Int {
        val limitedHtml = htmlString.substring(0, htmlString.length.coerceAtMost(5000))
        val matchResult = CSS_REV_REGEX.findAll(limitedHtml)
        val it = matchResult.iterator()
        if (!it.hasNext()) return DEFAULT_REV
        return it.next().groupValues[1].toInt()
    }

    private val SDF_YYYY_M_D_HH_MM =
        SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("GMT+08:00") }

    fun parsePostDate(dateStr: String): Date {
        return SDF_YYYY_M_D_HH_MM.parse(dateStr)
    }

    fun precheck(
        topicId: Int,
        bodyNode: JXNode,
        spaceType: SpaceType
    ): Pair<Topic?, Boolean>? {
        if (bodyNode.selOne(XPathHelper.XP_404_MSG) != null) {
            return Pair(
                Topic(
                    id = topicId, space = Reserved(type = spaceType), display = false
                ), true
            )
        }

        if (bodyNode.selOne("/div[1]/div[2]/div[1]/h1") != null) {
            LOGGER.info("Redirect post $spaceType-$topicId")
            return Pair(
                Topic(
                    id = topicId, space = Reserved(type = spaceType), display = false
                ), true
            )
        }

        if (bodyNode.selOne("/div[@id=\"wrapperClub\"]/div[contains(@class,\"clubMain\")]") != null
        ) {
            return Pair(
                Topic(
                    id = topicId, space = Reserved(type = spaceType), display = false
                ), true
            )
        }

        return null
    }

}