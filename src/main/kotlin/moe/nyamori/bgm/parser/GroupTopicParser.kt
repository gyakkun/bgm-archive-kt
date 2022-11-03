package moe.nyamori.bgm.parser

import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.model.Group
import moe.nyamori.bgm.model.GroupPost
import moe.nyamori.bgm.model.GroupPost.Companion.STATE_NORMAL
import moe.nyamori.bgm.model.GroupTopic
import moe.nyamori.bgm.model.User
import moe.nyamori.bgm.util.XPathHelper.XP_404_MSG
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_CONTENT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_DATE_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_DATE_SMALL_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_NAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_NICKNAME_ANCHOR_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_SIGN_SPAN_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_STYLE_BG_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_SUB_FLOOR_CONTENT
import moe.nyamori.bgm.util.XPathHelper.XP_SUB_FLOOR_DIV_LIST
import moe.nyamori.bgm.util.XPathHelper.XP_SUB_FLOOR_USER_NICKNAME_ANCHOR_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_NAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_FOLLOW_POST_DIV_LIST
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TITLE_H1_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_CONTENT_DIV
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_DIV
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_SMALL_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_UID_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_USERNAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_GROUP_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import us.codecraft.xsoup.Xsoup
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

object GroupTopicParser {
    val LOGGER = LoggerFactory.getLogger(GroupTopicParser.javaClass)
    val SDF_YYYY_M_D_HH_MM = SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINA)
    val SUB_FLOOR_FLOOR_NUM_REGEX = Regex("#\\d+-(\\d+)")

    fun parseGroupTopic(htmlFile: File): Pair<GroupTopic?, Boolean> {
        try {
            val htmlContent: String =
                FileUtil.getFileContent(htmlFile)!!
            val doc = Jsoup.parse(htmlContent)
            val topicId = run {
                try {
                    return@run htmlFile.nameWithoutExtension.toInt()
                } catch (ex: Exception) {
                    return@run -1
                }
            }
            if (Xsoup.compile(XP_404_MSG).evaluate(doc).elements.isNotEmpty()) {
                return Pair(
                    GroupTopic(
                        topicId, null, null, null, null, false, null, null
                    ), true
                )
            }

            val groupNameAnchor = Xsoup.compile(XP_GROUP_NAME_ANCHOR + "/@href").evaluate(doc)
            val groupDisplayNameAnchorText = Xsoup.compile(XP_GROUP_NAME_ANCHOR + "/text()").evaluate(doc)
            val topicTitle = Xsoup.compile(XP_GROUP_TOPIC_TITLE_H1_TEXT).evaluate(doc)

            val topPostDivId = Xsoup.compile(XP_GROUP_TOPIC_TOP_POST_DIV + "/@id").evaluate(doc)
            val topPostDivSmallText = Xsoup.compile(XP_GROUP_TOPIC_TOP_POST_SMALL_TEXT).evaluate(doc)
            val topPostUsernameAnchorHref =
                Xsoup.compile(XP_GROUP_TOPIC_TOP_POST_USERNAME_ANCHOR + "/@href").evaluate(doc)
            val topPostUidSpanStyle = Xsoup.compile(XP_GROUP_TOPIC_TOP_POST_UID_SPAN + "/@style").evaluate(doc)
            val topPostUserNicknameAnchorText =
                Xsoup.compile(XP_GROUP_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT).evaluate(doc)
            val topPostUserSignSpanText = Xsoup.compile(XP_GROUP_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT).evaluate(doc)
            val topPostContentDivHtml = Xsoup.compile(XP_GROUP_TOPIC_TOP_POST_CONTENT_DIV + "/html()").evaluate(doc)

            val followPostDivList =
                Xsoup.compile(XP_GROUP_TOPIC_FOLLOW_POST_DIV_LIST + "[@id^=\"post_\"]").evaluate(doc).elements

            // group name: /group/{groupName}
            val groupName = groupNameAnchor.get().substring(7)
            // group display name: {groupDisplayName}
            val groupDisplayName = groupNameAnchor.get()
            // title
            val title = topicTitle.get()

            // top post div small : #1 - {yyyy-M-d HH:mm}
            val topPostDateStr = topPostDivSmallText.get()
            val topPostDate = SDF_YYYY_M_D_HH_MM.parse(topPostDateStr.substring(5))
            val topPostDateline = topPostDate.toInstant().epochSecond
            // username: /user/{username}
            val topPostUserNameLine = topPostUsernameAnchorHref.get()
            val topPostUserUsername = topPostUserNameLine.substring(6)
            // user nickname: {nickname}
            val topPostUserNickname = topPostUserNicknameAnchorText.get()
            // uid: background-image:url\\('//lain.bgm.tv/pic/user/l/\\d+/\\d+/\\d+/(\\d+)\\.jpg\\?r=\\d+'\\)
            // FIXME: Special handling for background-image:url('//lain.bgm.tv/pic/user/l/icon.jpg')
            val topPostUserBgStyle = topPostUidSpanStyle.get()
            val topPostUserUid = getUidFromBgStyle(topPostUserBgStyle) ?: guessUidFromUsername(topPostUserUsername)
            // user sign: ({sign})
            val topPostUserSignStr = topPostUserSignSpanText?.get()
            val topPostUserSign = getUserSign(topPostUserSignStr)
            // top post id: post_{id}
            val topPostPid = topPostDivId.get().substring(5).toInt()
            // top post inner html: {}
            val topPostContentHtml = topPostContentDivHtml.get()


            val thisTopic = GroupTopic(
                id = topicId,
                group = Group(
                    groupName = groupName,
                    groupDisplayName = groupDisplayName
                ),
                uid = topPostUserUid,
                title = title,
                dateline = topPostDateline,
                display = true,
                topPostPid = topPostPid,
                postList = null
            )

            val postList = ArrayList<GroupPost>()

            val topPost = GroupPost(
                id = topPostPid,
                floorNum = 1,
                mid = topicId,
                contentHtml = topPostContentHtml,
                state = STATE_NORMAL,
                dateline = topPostDateline,
                user = User(
                    id = topPostUserUid,
                    username = topPostUserUsername,
                    nickname = topPostUserNickname,
                    sign = topPostUserSign
                ),
                subFloorNum = null,
                related = null,
                contentBbcode = null,
                subFloorList = null
            )

            postList.add(topPost)

            // follow post div list:
            // followPostDivList[0]
            followPostDivList.forEachIndexed outer@{ outerIdx, floor ->

//                LOGGER.error(floor.html())

                // Workaround for bug
                if (outerIdx != floor.elementSiblingIndex()) return@outer
                // follow post id: post_{id}
                val floorPid = floor.attr("id").substring(5).toInt()
                // follow post floor: #{floor}
                val floorNum = floor.selectXpath(XP_FLOOR_ANCHOR).text().substring(1).toInt()
                // follow post date: ' - {yyyy-M-d HH:mm}'
                val floorDateStr = Xsoup.compile(XP_FLOOR_DATE_SMALL_TEXT).evaluate(floor).get().substring(3)
                val floorDate = SDF_YYYY_M_D_HH_MM.parse(floorDateStr).toInstant().epochSecond
                // follow post user anchor - username: /user/{username}
                val floorUserUsername =
                    floor.selectXpath(XP_FLOOR_USER_NAME_ANCHOR).attr("href").substring(6)
                // follow post user anchor span - uid (plan B): background...
                // FIXME: Special handling for background-image:url('//lain.bgm.tv/pic/user/l/icon.jpg')
                val floorUserBgStyle = floor.selectXpath(XP_FLOOR_USER_STYLE_BG_SPAN).attr("style")
                val floorUserUid = getUidFromBgStyle(floorUserBgStyle) ?: guessUidFromUsername(floorUserUsername)
                // follow post user nickname: {nickname}
                val floorUserNickname = Xsoup.compile("//" + XP_FLOOR_USER_NICKNAME_ANCHOR_TEXT).evaluate(floor).get()
                // follow post user sign: ({sign})
                val floorUserSignStr = Xsoup.compile("//" + XP_FLOOR_USER_SIGN_SPAN_TEXT).evaluate(floor).let {
                    if (it.elements.size == 0) null
                    it.get()
                }
                val floorUserSign = getUserSign(floorUserSignStr)

                // follow post content div
                val floorContentHtml = floor.selectXpath(XP_FLOOR_CONTENT).html()

                val thisFloor = GroupPost(
                    id = floorPid,
                    floorNum = floorNum,
                    subFloorNum = null,
                    mid = topicId,
                    related = null,
                    contentHtml = floorContentHtml,
                    contentBbcode = null,
                    state = STATE_NORMAL,
                    dateline = floorDate,
                    user = User(
                        id = floorUserUid,
                        username = floorUserUsername,
                        nickname = floorUserNickname,
                        sign = floorUserSign
                    ),
                    subFloorList = null
                )


                // sub floor
                val subFloorDivList = floor.selectXpath(XP_SUB_FLOOR_DIV_LIST)
                val subFloorList = ArrayList<GroupPost>()
                subFloorDivList.forEachIndexed inner@{ innerIdx, subFloor ->

                    if (innerIdx != subFloor.elementSiblingIndex()) return@inner
                    // sub floor pid: post_{pid}
                    val subFloorPid = subFloor.attr("id").substring(5).toInt()
                    LOGGER.info("$subFloorPid")
                    // sub floor floor number: #{floor}-#{subFloor}
                    val subFloorNumsStr = Xsoup.compile(XP_FLOOR_ANCHOR).evaluate(subFloor).get()
                    LOGGER.info(subFloorNumsStr)
                    val subFloorFloorNum = SUB_FLOOR_FLOOR_NUM_REGEX
                        .findAll(subFloorNumsStr).iterator()
                        .next().groupValues[1].toInt()
                    // follow post date: ' - {yyyy-M-d HH:mm}'
                    val subFloorDateStr = Xsoup.compile(XP_FLOOR_DATE_SMALL_TEXT).evaluate(subFloor).get().substring(3)
                    val subFloorDate = SDF_YYYY_M_D_HH_MM.parse(subFloorDateStr).toInstant().epochSecond
                    // follow post user anchor - username: /user/{username}
                    val subFloorUserUsername =
                        subFloor.selectXpath(XP_FLOOR_USER_NAME_ANCHOR).attr("href").substring(6)
                    // follow post user anchor span - uid (plan B): background...
                    // FIXME: Special handling for background-image:url('//lain.bgm.tv/pic/user/l/icon.jpg')
                    val subFloorUserBgStyle = subFloor.selectXpath(XP_FLOOR_USER_STYLE_BG_SPAN).attr("style")
                    val subFloorUserUid =
                        getUidFromBgStyle(subFloorUserBgStyle) ?: guessUidFromUsername(subFloorUserUsername)
                    // follow post user nickname: {nickname}
                    val subFloorUserNickname = Xsoup.compile("//" + XP_SUB_FLOOR_USER_NICKNAME_ANCHOR_TEXT).evaluate(subFloor).get()

                    // follow post content div
                    val subFloorContentHtml = subFloor.selectXpath(XP_SUB_FLOOR_CONTENT).html()
                    val thisSubFloor = GroupPost(
                        id = subFloorPid,
                        floorNum = floorNum,
                        subFloorNum = subFloorFloorNum,
                        mid = topicId,
                        related = floorPid,
                        contentHtml = subFloorContentHtml,
                        contentBbcode = null,
                        state = STATE_NORMAL,
                        dateline = subFloorDate,
                        user = User(
                            id = subFloorUserUid,
                            username = subFloorUserUsername,
                            nickname = subFloorUserNickname,
                            sign = null
                        ),
                        subFloorList = null
                    )
                    subFloorList.add(thisSubFloor)
                }
                if (subFloorList.isNotEmpty()) {
                    thisFloor.subFloorList = subFloorList
                }
                postList.add(thisFloor)
            }

            thisTopic.postList = postList
            return Pair(thisTopic, true)
        } catch (ex: Exception) {
            LOGGER.error("Ex: ${htmlFile.absolutePath}", ex)
            return Pair(null, false)
        }

    }

    private fun guessUidFromUsername(username: String?): Int? {
        return username?.let {
            if (username.all { Character.isDigit(it) }) {
                username.toInt()
            }
            null
        }
    }

    private fun getUserSign(userSignStr: String?): String? {
        return userSignStr?.let {
            if (it.startsWith("(") && it.endsWith(")")) {
                it.substring(1, it.length - 1)
            }
            null
        }
    }

    private fun getUidFromBgStyle(topPostUserBgStyle: String) = if (topPostUserBgStyle[47] == 'i') {
        null
    } else {
        var tmp = topPostUserBgStyle.substring(57)
        tmp = tmp.substring(0, tmp.indexOf(".jpg"))
        tmp.toInt()
    }
}