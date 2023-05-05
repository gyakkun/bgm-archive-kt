package moe.nyamori.bgm.parser.subject

import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.model.Post.Companion.STATE_CLOSED
import moe.nyamori.bgm.model.Post.Companion.STATE_NORMAL
import moe.nyamori.bgm.model.Post.Companion.STATE_REOPEN
import moe.nyamori.bgm.model.Post.Companion.STATE_SILENT
import moe.nyamori.bgm.parser.Parser
import moe.nyamori.bgm.util.ParserHelper.getUidFromBgStyle
import moe.nyamori.bgm.util.ParserHelper.getUserSign
import moe.nyamori.bgm.util.ParserHelper.guessUidFromUsername
import moe.nyamori.bgm.util.PostToStateHelper
import moe.nyamori.bgm.util.XPathHelper.XP_TOPIC_CLOSED_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_TOPIC_DISABLED_FLOOR_DATE_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_TOPIC_DISABLED_FLOOR_AUTHOR_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_TOPIC_SILENT_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_404_MSG
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_CONTENT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_NAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_NICKNAME_ANCHOR_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_SIGN_SPAN_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_FLOOR_USER_STYLE_BG_SPAN
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_NAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_FOLLOW_POST_DIV_LIST
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TITLE_H1_TEXT
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TOP_POST_AVATAR_USERNAME_ANCHOR
import moe.nyamori.bgm.util.XPathHelper.XP_SUBJECT_TOPIC_TOP_POST_CONTENT_DIV
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
import java.text.SimpleDateFormat
import java.util.*

object SubjectTopicParserR400 : Parser {
    private val LOGGER: Logger = LoggerFactory.getLogger(SubjectTopicParserR400.javaClass)
    private val SDF_YYYY_M_D_HH_MM =
        SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("GMT+08:00") }
    private val SUB_FLOOR_FLOOR_NUM_REGEX = Regex("#\\d+-(\\d+)")

    val SPACE_NAME_ANCHOR_XPATH = XP_SUBJECT_NAME_ANCHOR
    val SPACE_TOPIC_TITLE_H1_TEXT_XPATH = XP_SUBJECT_TOPIC_TITLE_H1_TEXT
    val SPACE_TOPIC_TOP_POST_DIV_XPATH = XP_SUBJECT_TOPIC_TOP_POST_DIV
    val SPACE_TOPIC_TOP_POST_DATE_SMALL_TEXT_XPATH = "/div[2]/div[2]/div[1]/div[1]/div[1]/small/text()"
    val SPACE_TOPIC_TOP_POST_AVATAR_USERNAME_ANCHOR_XPATH = XP_SUBJECT_TOPIC_TOP_POST_AVATAR_USERNAME_ANCHOR
    val SPACE_TOPIC_TOP_POST_UID_SPAN_XPATH = XP_SUBJECT_TOPIC_TOP_POST_UID_SPAN
    val SPACE_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT_XPATH = XP_SUBJECT_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT
    val SPACE_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT_XPATH = XP_SUBJECT_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT
    val SPACE_TOPIC_TOP_POST_CONTENT_DIV_XPATH = XP_SUBJECT_TOPIC_TOP_POST_CONTENT_DIV
    val SPACE_TOPIC_FOLLOW_POST_DIV_LIST = XP_SUBJECT_TOPIC_FOLLOW_POST_DIV_LIST

    const val XP_FLOOR_ANCHOR_R400 = "div[contains(@class,\"post_actions\")]/div[contains(@class,\"action\")]/small/a[contains(@class,\"floor-anchor\")]"
    const val XP_FLOOR_DATE_SMALL_TEXT_R400 = "div[contains(@class,\"post_actions\")]/div[contains(@class,\"action\")]/small/text()"


    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        if (spaceType != SpaceType.SUBJECT) throw IllegalStateException("Should parse a subject topic but got $spaceType")
        try {
            val doc: JXDocument = JXDocument.create(htmlFileString)
            val bodyNode = doc.selNOne("body")

            if (bodyNode.selOne(XP_404_MSG) != null) {
                return Pair(
                    Topic(
                        id = topicId, space = Reserved(type = spaceType), display = false
                    ), true
                )
            }

            val groupNameAnchor: JXNode = bodyNode.selOne(SPACE_NAME_ANCHOR_XPATH)!!

            val topicTitle: JXNode = bodyNode.selOne(SPACE_TOPIC_TITLE_H1_TEXT_XPATH)!!


            val topPostDiv = bodyNode.selOne(SPACE_TOPIC_TOP_POST_DIV_XPATH)
            val topPostDivSmallText = bodyNode.selOne(SPACE_TOPIC_TOP_POST_DATE_SMALL_TEXT_XPATH)
            val topPostUsernameAnchor = bodyNode.selOne(SPACE_TOPIC_TOP_POST_AVATAR_USERNAME_ANCHOR_XPATH)
            val topPostUidSpan = bodyNode.selOne(SPACE_TOPIC_TOP_POST_UID_SPAN_XPATH)
            val topPostUserNicknameAnchorText = bodyNode.selOne(XP_SUBJECT_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT)
            val topPostUserSignSpanText = bodyNode.selOne(SPACE_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT_XPATH)
            val topPostContentDiv = bodyNode.selOne(SPACE_TOPIC_TOP_POST_CONTENT_DIV_XPATH)

            val followPostDivList = bodyNode.sel(SPACE_TOPIC_FOLLOW_POST_DIV_LIST)

            // group name: /group/{groupName}
            val groupName = groupNameAnchor.asElement().attr("href").split("/").last()
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


            val thisTopic = Topic(
                id = topicId,
                space = Subject(
                    type = spaceType,
                    name = groupName,
                    displayName = groupDisplayName
                ),
                uid = topPostUserUid,
                title = title,
                dateline = topPostDateline,
                display = true,
                topPostPid = topPostPid,
                postList = null
            )

            val postList = ArrayList<Post>()

            val topPost = Post(
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

                // floor is slient or close
                val isSilent = floor.selOne(XP_TOPIC_SILENT_SPAN) != null
                val isClosed = floor.selOne(XP_TOPIC_CLOSED_SPAN) != null
                val isReopen = floor.selOne(XP_TOPIC_REOPEN_SPAN) != null
                val isTopicDisabled = isSilent || isClosed
                val isSpecialBadge = isSilent || isClosed || isReopen


                val floorNum: Int
                val floorDateStr: String
                val floorDate: Long

                if (isTopicDisabled) {
                    thisTopic.display = false
                    val dateSpan = floor.selOne(XP_TOPIC_DISABLED_FLOOR_DATE_SPAN)
                    floorNum = postList.size + 1
                    floorDateStr = dateSpan.asElement().html()
                    floorDate = SDF_YYYY_M_D_HH_MM.parse(floorDateStr).toInstant().epochSecond
                } else {
                    // follow post floor: #{floor}
                    floorNum = floor.selOne(XP_FLOOR_ANCHOR_R400).asElement().text().substring(1).toInt()
                    // follow post date: ' - {yyyy-M-d HH:mm}'
                    floorDateStr = floor.selOne(XP_FLOOR_DATE_SMALL_TEXT_R400).asString().substring(2)
                    floorDate = SDF_YYYY_M_D_HH_MM.parse(floorDateStr).toInstant().epochSecond
                }
                // follow post user anchor - username: /user/{username}
                val floorUserUsername = floor.selOne(XP_FLOOR_USER_NAME_ANCHOR).asElement().attr("href").substring(6)
                // follow post user anchor span - uid (plan B): background...
                // FIXME: Special handling for background-image:url('//lain.bgm.tv/pic/user/l/icon.jpg')
                val floorUserBgStyle = floor.selOne(XP_FLOOR_USER_STYLE_BG_SPAN).asElement().attr("style")
                val floorUserUid = getUidFromBgStyle(floorUserBgStyle) ?: guessUidFromUsername(floorUserUsername)
                val floorUserNickname: String
                val floorUserSignStr: String?
                var floorUserSign: String? = null
                if (!isSpecialBadge) {
                    // follow post user nickname: {nickname}
                    floorUserNickname = floor.selOne(XP_FLOOR_USER_NICKNAME_ANCHOR_TEXT).asString()
                    // follow post user sign: ({sign})
                    floorUserSignStr = floor.selOne(XP_FLOOR_USER_SIGN_SPAN_TEXT)?.asString()
                    floorUserSign = getUserSign(floorUserSignStr)
                } else {
                    floorUserNickname = floor.selOne(XP_TOPIC_DISABLED_FLOOR_AUTHOR_ANCHOR).asElement().html()
                }

                // follow post content div
                val floorContentHtml = if (!isSpecialBadge) floor.selOne(XP_FLOOR_CONTENT).asElement()
                    .html() else floor.selOne("//div[contains(@class,\"inner\")]").asElement().html()

                val thisFloor = Post(
                    id = floorPid,
                    floorNum = floorNum,
                    subFloorNum = null,
                    mid = topicId,
                    related = null,
                    contentHtml = floorContentHtml,
                    contentBbcode = null,
                    state = if (isSilent) {
                        STATE_SILENT
                    } else if (isClosed) {
                        STATE_CLOSED
                    } else if (isReopen) {
                        STATE_REOPEN
                    } else {
                        PostToStateHelper.fromPostHtmlToState(floorContentHtml)
                    },
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
                val subFloorList = ArrayList<Post>()
                subFloorDivList.forEachIndexed inner@{ innerIdx, subFloor ->
                    if (innerIdx != subFloor.asElement().elementSiblingIndex()) return@inner
                    // sub floor pid: post_{pid}
                    val subFloorPid = subFloor.asElement().attr("id").substring(5).toInt()
                    // sub floor floor number: #{floor}-#{subFloor}
                    val subFloorFloorNum = SUB_FLOOR_FLOOR_NUM_REGEX
                        .findAll(subFloor.selOne(XP_FLOOR_ANCHOR_R400).asElement().text()).iterator()
                        .next().groupValues[1].toInt()
                    // follow post date: ' - {yyyy-M-d HH:mm}'
                    val subFloorDateStr = subFloor.selOne(XP_FLOOR_DATE_SMALL_TEXT_R400).asString().substring(2)
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
                    val thisSubFloor = Post(
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
            thisTopic.state = thisTopic.getAllPosts().map { it.state }.distinct().let {
                var res = STATE_NORMAL
                if (it.contains(STATE_SILENT)) res = res or STATE_SILENT
                if (it.contains(STATE_CLOSED)) res = res or STATE_CLOSED
                if (it.contains(STATE_REOPEN)) res = res or STATE_REOPEN
                res
            }
            return Pair(thisTopic, true)
        } catch (ex: Exception) {
            LOGGER.error("Ex: ", ex)
//            LOGGER.error("Ex: ${htmlFile.nameWithoutExtension}")
            return Pair(null, false)
        }

    }
}