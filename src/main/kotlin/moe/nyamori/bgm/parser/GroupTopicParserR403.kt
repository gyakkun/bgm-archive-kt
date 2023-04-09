package moe.nyamori.bgm.parser

import com.google.gson.JsonObject
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.model.Post.Companion.STATE_CLOSED
import moe.nyamori.bgm.model.Post.Companion.STATE_NORMAL
import moe.nyamori.bgm.model.Post.Companion.STATE_REOPEN
import moe.nyamori.bgm.model.Post.Companion.STATE_SILENT
import moe.nyamori.bgm.util.ParserHelper.getUidFromBgStyle
import moe.nyamori.bgm.util.ParserHelper.getUserSign
import moe.nyamori.bgm.util.ParserHelper.guessUidFromUsername
import moe.nyamori.bgm.util.XPathHelper.XP_TOPIC_CLOSED_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_TOPIC_DISABLED_FLOOR_DATE_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_TOPIC_DISABLED_FLOOR_AUTHOR_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_TOPIC_SILENT_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_404_MSG
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_CONTENT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_DATE_SMALL_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_NAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_NICKNAME_ANCHOR_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_SIGN_SPAN_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_STYLE_BG_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_NAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_FOLLOW_POST_DIV_LIST
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TITLE_H1_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_AVATAR_USERNAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_CONTENT_DIV
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_DATE_SMALL_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_DIV
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_UID_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_SUB_FLOOR_CONTENT
import moe.nyamori.bgm.util.XPathHelper.XP_SUB_FLOOR_DIV_LIST
import moe.nyamori.bgm.util.XPathHelper.XP_SUB_FLOOR_USER_NICKNAME_ANCHOR_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_TOPIC_REOPEN_SPAN
import org.seimicrawler.xpath.JXDocument
import org.seimicrawler.xpath.JXNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

object GroupTopicParserR403 : Parser {

    private val LOGGER: Logger = LoggerFactory.getLogger(GroupTopicParserR403.javaClass)


    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        val res = GroupTopicParserR402.parseTopic(htmlFileString, topicId, spaceType)
        if (!res.second) return res
        val metaMap = HashMap<String, Any>()
        val dataLikesList: String? =
            htmlFileString.lineSequence().filter { it.startsWith("var data_likes_list = {") && it.endsWith("};") }
                .firstOrNull()
                ?.substringAfter("=")
                ?.substringBeforeLast(";")
        if (dataLikesList != null) {
            val dataLikesListJson = GitHelper.GSON.fromJson(dataLikesList, JsonObject::class.java)
            metaMap["data_likes_list"] = dataLikesListJson
        }
        return Pair(
            res.first!!.copy(
                space = Group(
                    meta = metaMap.ifEmpty { null },
                    name = (res.first!!.space as Group).name,
                    displayName = (res.first!!.space as Group).displayName
                )
            ), true
        )

    }

}