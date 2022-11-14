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
import org.seimicrawler.xpath.JXDocument
import org.seimicrawler.xpath.JXNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

object GroupTopicParser {
    private val LOGGER: Logger = LoggerFactory.getLogger(GroupTopicParser.javaClass)
    private val SDF_YYYY_M_D_HH_MM = SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINA)
    private val SUB_FLOOR_FLOOR_NUM_REGEX = Regex("#\\d+-(\\d+)")

    fun parseGroupTopic(htmlFile: File): Pair<GroupTopic?, Boolean> {
        try {
            val htmlContent: String =
                FileUtil.getFileContent(htmlFile)!!
            val doc: JXDocument = JXDocument.create(htmlContent)
            val topicId = kotlin.runCatching {
                htmlFile.nameWithoutExtension.toInt()
            }.onFailure {
                return Pair(null, false)
            }.getOrThrow()

            if (doc.selNOne(XP_404_MSG) != null) {
                return Pair(
                    GroupTopic(
                        topicId, null, null, null, null, false, null, null
                    ), true
                )
            }

            val groupNameAnchor: JXNode = doc.selNOne(XP_GROUP_NAME_ANCHOR)
            val topicTitle: JXNode = doc.selNOne(XP_GROUP_TOPIC_TITLE_H1_TEXT)

            val topPostDiv = doc.selNOne(XP_GROUP_TOPIC_TOP_POST_DIV)
            val topPostDivSmallText = doc.selNOne(XP_GROUP_TOPIC_TOP_POST_SMALL_TEXT)
            val topPostUsernameAnchor = doc.selNOne(XP_GROUP_TOPIC_TOP_POST_USERNAME_ANCHOR)
            val topPostUidSpan = doc.selNOne(XP_GROUP_TOPIC_TOP_POST_UID_SPAN)
            val topPostUserNicknameAnchorText =
                doc.selNOne(XP_GROUP_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT)
            val topPostUserSignSpanText = doc.selNOne(XP_GROUP_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT)
            val topPostContentDiv = doc.selNOne(XP_GROUP_TOPIC_TOP_POST_CONTENT_DIV)

            val followPostDivList = doc.selN(XP_GROUP_TOPIC_FOLLOW_POST_DIV_LIST)

            // group name: /group/{groupName}
            val groupName = groupNameAnchor.asElement().attr("href").substring(7)
            // group display name: {groupDisplayName}
            val groupDisplayName = groupNameAnchor.asElement().text()
            // title
            val title = topicTitle.asString()

            // top post div small : #1 - {yyyy-M-d HH:mm}
            val topPostDateStr = topPostDivSmallText.asString()
            val topPostDate = SDF_YYYY_M_D_HH_MM.parse(topPostDateStr.substring(5))
            val topPostDateline = topPostDate.toInstant().epochSecond
            // username: /user/{username}
            val topPostUserNameLine = topPostUsernameAnchor.asElement().attr("href")
            val topPostUserUsername = topPostUserNameLine.substring(6)
            // user nickname: {nickname}
            val topPostUserNickname = topPostUserNicknameAnchorText.asString()
            // uid: background-image:url\\('//lain.bgm.tv/pic/user/l/\\d+/\\d+/\\d+/(\\d+)\\.jpg\\?r=\\d+'\\)
            // FIXME: Special handling for background-image:url('//lain.bgm.tv/pic/user/l/icon.jpg')
            val topPostUserBgStyle = topPostUidSpan.asElement().attr("style")
            val topPostUserUid = getUidFromBgStyle(topPostUserBgStyle) ?: guessUidFromUsername(topPostUserUsername)
            // user sign: ({sign})
            val topPostUserSignStr = topPostUserSignSpanText?.asString()
            val topPostUserSign = getUserSign(topPostUserSignStr)
            // top post id: post_{id}
            val topPostPid = topPostDiv.asElement().attr("id").substring(5).toInt()
            // top post inner html: {}
            val topPostContentHtml = topPostContentDiv.asElement().html()


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
                // Workaround for bug
                if (outerIdx != floor.asElement().elementSiblingIndex()) return@outer
                // follow post id: post_{id}
                val floorPid = floor.asElement().attr("id").substring(5).toInt()
                // follow post floor: #{floor}
                val floorNum = floor.selOne(XP_FLOOR_ANCHOR).asElement().text().substring(1).toInt()
                // follow post date: ' - {yyyy-M-d HH:mm}'
                val floorDateStr = floor.selOne(XP_FLOOR_DATE_SMALL_TEXT).asString().substring(2)
                val floorDate = SDF_YYYY_M_D_HH_MM.parse(floorDateStr).toInstant().epochSecond
                // follow post user anchor - username: /user/{username}
                val floorUserUsername = floor.selOne(XP_FLOOR_USER_NAME_ANCHOR).asElement().attr("href").substring(6)
                // follow post user anchor span - uid (plan B): background...
                // FIXME: Special handling for background-image:url('//lain.bgm.tv/pic/user/l/icon.jpg')
                val floorUserBgStyle = floor.selOne(XP_FLOOR_USER_STYLE_BG_SPAN).asElement().attr("style")
                val floorUserUid = getUidFromBgStyle(floorUserBgStyle) ?: guessUidFromUsername(floorUserUsername)
                // follow post user nickname: {nickname}
                val floorUserNickname = floor.selOne(XP_FLOOR_USER_NICKNAME_ANCHOR_TEXT).asString()
                // follow post user sign: ({sign})
                val floorUserSignStr = floor.selOne(XP_FLOOR_USER_SIGN_SPAN_TEXT)?.asString()
                val floorUserSign = getUserSign(floorUserSignStr)

                // follow post content div
                val floorContentHtml = floor.selOne(XP_FLOOR_CONTENT).asElement().html()

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
                val subFloorDivList: MutableList<JXNode> = floor.sel(XP_SUB_FLOOR_DIV_LIST)
                val subFloorList = ArrayList<GroupPost>()
                subFloorDivList.forEachIndexed inner@{ innerIdx, subFloor ->
                    if (innerIdx != subFloor.asElement().elementSiblingIndex()) return@inner
                    // sub floor pid: post_{pid}
                    val subFloorPid = subFloor.asElement().attr("id").substring(5).toInt()
                    // sub floor floor number: #{floor}-#{subFloor}
                    val subFloorFloorNum = SUB_FLOOR_FLOOR_NUM_REGEX
                        .findAll(subFloor.selOne(XP_FLOOR_ANCHOR).asElement().text()).iterator()
                        .next().groupValues[1].toInt()
                    // follow post date: ' - {yyyy-M-d HH:mm}'
                    val subFloorDateStr = subFloor.selOne(XP_FLOOR_DATE_SMALL_TEXT).asString().substring(2)
                    val subFloorDate = SDF_YYYY_M_D_HH_MM.parse(subFloorDateStr).toInstant().epochSecond
                    // follow post user anchor - username: /user/{username}
                    val subFloorUserUsername =
                        subFloor.selOne(XP_FLOOR_USER_NAME_ANCHOR).asElement().attr("href").substring(6)
                    // follow post user anchor span - uid (plan B): background...
                    // FIXME: Special handling for background-image:url('//lain.bgm.tv/pic/user/l/icon.jpg')
                    val subFloorUserBgStyle = subFloor.selOne(XP_FLOOR_USER_STYLE_BG_SPAN).asElement().attr("style")
                    val subFloorUserUid =
                        getUidFromBgStyle(subFloorUserBgStyle) ?: guessUidFromUsername(subFloorUserUsername)
                    // follow post user nickname: {nickname}
                    val subFloorUserNickname = subFloor.selOne(XP_SUB_FLOOR_USER_NICKNAME_ANCHOR_TEXT).asString()

                    // follow post content div
                    val subFloorContentHtml = subFloor.selOne(XP_SUB_FLOOR_CONTENT).asElement().html()
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
            LOGGER.error("Ex: ", ex)
//            LOGGER.error("Ex: ${htmlFile.nameWithoutExtension}")
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