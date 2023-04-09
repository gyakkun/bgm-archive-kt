package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.model.Post.Companion.STATE_CLOSED
import moe.nyamori.bgm.model.Post.Companion.STATE_NORMAL
import moe.nyamori.bgm.model.Post.Companion.STATE_REOPEN
import moe.nyamori.bgm.model.Post.Companion.STATE_SILENT
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
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_NAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_FOLLOW_POST_DIV_LIST
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TITLE_H1_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TOP_POST_AVATAR_USERNAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TOP_POST_CONTENT_DIV
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TOP_POST_DATE_SMALL_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TOP_POST_DIV
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TOP_POST_UID_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_SUB_FLOOR_CONTENT
import moe.nyamori.bgm.util.XPathHelper.XP_SUB_FLOOR_DIV_LIST
import moe.nyamori.bgm.util.XPathHelper.XP_SUB_FLOOR_USER_NICKNAME_ANCHOR_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_TOPIC_REOPEN_SPAN
import org.seimicrawler.xpath.JXDocument
import org.seimicrawler.xpath.JXNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.*

object TopicParserR398 {
    fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        return when (spaceType) {
            SpaceType.GROUP -> GroupTopicParserR398.parseTopic(htmlFileString, topicId, spaceType)
            SpaceType.SUBJECT -> SubjectTopicParserR398.parseTopic(htmlFileString, topicId, spaceType)
            else -> throw IllegalArgumentException("Not support in this R398 parser: topicId=$topicId, spaceType=$spaceType")
        }
    }

}