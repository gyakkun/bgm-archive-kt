package moe.nyamori.bgm.parser.group

import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.model.Post.Companion.STATE_CLOSED
import moe.nyamori.bgm.model.Post.Companion.STATE_DELETED
import moe.nyamori.bgm.model.Post.Companion.STATE_NORMAL
import moe.nyamori.bgm.model.Post.Companion.STATE_REOPEN
import moe.nyamori.bgm.model.Post.Companion.STATE_SILENT
import moe.nyamori.bgm.parser.Parser
import moe.nyamori.bgm.util.ParserHelper.getUidFromBgStyle
import moe.nyamori.bgm.util.ParserHelper.getUserSign
import moe.nyamori.bgm.util.ParserHelper.guessUidFromUsername
import moe.nyamori.bgm.util.PostToStateHelper
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*

object GroupTopicParserR400 : Parser {
    private val LOGGER: Logger = LoggerFactory.getLogger(GroupTopicParserR400.javaClass)
    private val SDF_YYYY_M_D_HH_MM =
        SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("GMT+08:00") }
    private val SUB_FLOOR_FLOOR_NUM_REGEX = Regex("#\\d+-(\\d+)")

    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        if (spaceType != SpaceType.GROUP) throw IllegalStateException("Should parse a group topic but got $spaceType")
        try {
            val document = Jsoup.parse(htmlFileString)
            val body = document.body()!!

            if (body.selectFirst("div#colunmNotice > div > p.text") != null || 
                body.selectFirst("div.mainWrapper > div#column_container > div > div > div > p") != null ||
                body.selectFirst("> div:nth-child(1) > div:nth-child(2) > div:nth-child(1) > div > div > p:nth-child(1)") != null) {
                return Pair(
                    Topic(id = topicId, space = Reserved(type = spaceType), display = false, state = STATE_DELETED),
                    true
                )
            }

            val groupNameAnchor = body.selectFirst("div#column_container > div.mainWrapper > div.columns > div > div#pageHeader > h1 > span > a.avatar")
                ?: body.selectFirst("h1 > span > a") ?: body.selectFirst("div > h1 > span > a")!!
            val topicTitleH1 = body.selectFirst("div#column_container > div.mainWrapper > div.columns > div > div#pageHeader > h1")
                ?: body.selectFirst("h1#pageHeader") ?: body.selectFirst("div > h1")!!
            val topPostDiv = body.selectFirst("div#column_container > div.mainWrapper > div.columns > div > div#post_list > div.postTopic")
                ?: body.selectFirst("div#post_list > div.postTopic") ?: body.selectFirst("div#post_list > div") ?: body.selectFirst("div.postTopic")!!
            // R400: div.inner > div > small
            val topPostDivSmall = topPostDiv.selectFirst("div.inner > div > small") 
                ?: topPostDiv.selectFirst("small")!!
            val topPostUsernameAnchor = topPostDiv.selectFirst("a.avatar")!!
            val topPostUidSpan = topPostUsernameAnchor.selectFirst("span")!!
            val topPostUserNicknameAnchor = topPostDiv.selectFirst("div.inner > strong > a")!!
            val topPostUserSignSpan = topPostDiv.selectFirst("div.inner > span.tip_j")
            val topPostContentDiv = topPostDiv.selectFirst("div.inner > div.topic_content")!!
            val followPostDivList = body.select("div#comment_list > div.row_reply")

            val groupName = groupNameAnchor.attr("href").split("/").last()
            val groupDisplayName = groupNameAnchor.text()
            val title = topicTitleH1.ownText()

            val topPostDateStr = topPostDivSmall.ownText()
            val topPostDate = SDF_YYYY_M_D_HH_MM.parse(topPostDateStr.substring(5))
            val topPostDateline = topPostDate.toInstant().epochSecond
            val topPostUserUsername = topPostUsernameAnchor.attr("href").substring(6)
            val topPostUserNickname = topPostUserNicknameAnchor.text()
            val topPostUserBgStyle = topPostUidSpan.attr("style")
            val topPostUserUid = getUidFromBgStyle(topPostUserBgStyle) ?: guessUidFromUsername(topPostUserUsername)
            val topPostUserSignStr = topPostUserSignSpan?.ownText()
            val topPostUserSign = getUserSign(topPostUserSignStr)
            val topPostPid = topPostDiv.attr("id").substring(5).toInt()
            val topPostContentHtml = topPostContentDiv.html()

            val thisTopic = Topic(
                id = topicId,
                space = Group(type = spaceType, name = groupName, displayName = groupDisplayName),
                uid = topPostUserUid, title = title, dateline = topPostDateline,
                display = true, topPostPid = topPostPid, postList = null
            )

            val postList = ArrayList<Post>()
            postList.add(
                Post(
                    id = topPostPid, floorNum = 1, mid = topicId,
                    contentHtml = topPostContentHtml, state = STATE_NORMAL, dateline = topPostDateline,
                    user = User(id = topPostUserUid, username = topPostUserUsername,
                        nickname = topPostUserNickname, sign = topPostUserSign),
                    subFloorNum = null, related = null, contentBbcode = null, subFloorList = null
                )
            )

            followPostDivList.forEachIndexed outer@{ outerIdx, floor ->
                if (outerIdx != floor.elementSiblingIndex()) return@outer
                val floorPid = floor.attr("id").substring(5).toInt()

                val isSilent = floor.selectFirst("span.badgeStateSilent") != null
                val isClosed = floor.selectFirst("span.badgeStateClosed") != null
                val isReopen = floor.selectFirst("span.badgeStateReopen") != null
                val isTopicDisabled = isSilent || isClosed
                val isSpecialBadge = isSilent || isClosed || isReopen

                val floorNum: Int
                val floorDate: Long

                if (isSpecialBadge) {
                    thisTopic.display = !isTopicDisabled
                    val dateSpan = floor.selectFirst("span.tip_j")!!
                    floorNum = postList.size + 1
                    floorDate = SDF_YYYY_M_D_HH_MM.parse(dateSpan.html()).toInstant().epochSecond
                } else {
                    val floorAnchor = floor.selectFirst("div.post_actions > div.action > small > a.floor-anchor")
                        ?: floor.selectFirst("div.re_info > div.action > small > a.floor-anchor")
                        ?: floor.selectFirst("div.re_info > small > a.floor-anchor")!!
                    floorNum = floorAnchor.text().substring(1).toInt()
                    val floorDateSmall = floor.selectFirst("div.post_actions > div.action > small")
                        ?: floor.selectFirst("div.re_info > div.action > small")
                        ?: floor.selectFirst("div.re_info > small")!!
                    floorDate = SDF_YYYY_M_D_HH_MM.parse(floorDateSmall.ownText().substring(2)).toInstant().epochSecond
                }

                val floorUserUsername = floor.selectFirst("a")!!.attr("href").substring(6)
                val floorUserBgStyle = floor.selectFirst("a > span")!!.attr("style")
                val floorUserUid = getUidFromBgStyle(floorUserBgStyle) ?: guessUidFromUsername(floorUserUsername)
                val floorUserNickname: String
                var floorUserSign: String? = null

                if (!isSpecialBadge) {
                    floorUserNickname = floor.selectFirst("span.userInfo > strong > a")?.text()
                        ?: floor.selectFirst("div.inner > strong > a")!!.text()
                    val floorUserSignStr = floor.selectFirst("span.userInfo > span.tip_j")?.ownText()
                        ?: floor.selectFirst("span.userInfo > span")?.ownText()
                        ?: floor.selectFirst("div.inner > span.tip_j")?.ownText()
                    floorUserSign = getUserSign(floorUserSignStr)
                } else {
                    floorUserNickname = floor.selectFirst("a[class*=post_author_]")!!.html()
                }

                val floorContentHtml = if (!isSpecialBadge) {
                    floor.selectFirst("div.message")!!.html()
                } else {
                    floor.selectFirst("div.inner")!!.html()
                }

                val thisFloor = Post(
                    id = floorPid, floorNum = floorNum, subFloorNum = null, mid = topicId,
                    related = null, contentHtml = floorContentHtml, contentBbcode = null,
                    state = when {
                        isSilent -> STATE_SILENT; isClosed -> STATE_CLOSED; isReopen -> STATE_REOPEN
                        else -> PostToStateHelper.fromPostHtmlToState(floorContentHtml)
                    },
                    dateline = floorDate,
                    user = User(id = floorUserUid, username = floorUserUsername,
                        nickname = floorUserNickname, sign = floorUserSign),
                    subFloorList = null
                )

                val subFloorDivList = floor.select("div.topic_sub_reply > div")
                val subFloorList = ArrayList<Post>()
                subFloorDivList.forEachIndexed inner@{ innerIdx, subFloor ->
                    if (innerIdx != subFloor.elementSiblingIndex()) return@inner
                    val subFloorPid = subFloor.attr("id").substring(5).toInt()
                    val subFloorAnchor = subFloor.selectFirst("div.post_actions > div.action > small > a.floor-anchor")
                        ?: subFloor.selectFirst("div.re_info > div.action > small > a.floor-anchor")
                        ?: subFloor.selectFirst("div.re_info > small > a.floor-anchor")!!
                    val subFloorFloorNum = SUB_FLOOR_FLOOR_NUM_REGEX
                        .findAll(subFloorAnchor.text()).iterator().next().groupValues[1].toInt()
                    val subFloorDateSmall = subFloor.selectFirst("div.post_actions > div.action > small")
                        ?: subFloor.selectFirst("div.re_info > div.action > small")
                        ?: subFloor.selectFirst("div.re_info > small")!!
                    val subFloorDate = SDF_YYYY_M_D_HH_MM.parse(subFloorDateSmall.ownText().substring(2)).toInstant().epochSecond
                    val subFloorUserUsername = subFloor.selectFirst("a")!!.attr("href").substring(6)
                    val subFloorUserBgStyle = subFloor.selectFirst("a > span")!!.attr("style")
                    val subFloorUserUid = getUidFromBgStyle(subFloorUserBgStyle) ?: guessUidFromUsername(subFloorUserUsername)
                    val subFloorUserNickname = subFloor.selectFirst("strong.userName > a")!!.text()
                    val subFloorContentHtml = subFloor.selectFirst("div.cmt_sub_content")!!.html()
                    subFloorList.add(
                        Post(
                            id = subFloorPid, floorNum = floorNum, subFloorNum = subFloorFloorNum,
                            mid = topicId, related = floorPid, contentHtml = subFloorContentHtml,
                            contentBbcode = null, state = PostToStateHelper.fromPostHtmlToState(subFloorContentHtml),
                            dateline = subFloorDate,
                            user = User(id = subFloorUserUid, username = subFloorUserUsername,
                                nickname = subFloorUserNickname, sign = null),
                            subFloorList = null
                        )
                    )
                }
                if (subFloorList.isNotEmpty()) thisFloor.subFloorList = subFloorList
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
            return Pair(null, false)
        }
    }
}