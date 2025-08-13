package moe.nyamori.bgm.parser.group

import com.google.gson.JsonObject
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.parser.Parser
import moe.nyamori.bgm.util.ParserHelper
import moe.nyamori.bgm.util.ParserHelper.getUidFromBgStyle
import moe.nyamori.bgm.util.PostToStateHelper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*

object GroupTopicParserR430 : Parser {
    private val LOGGER: Logger = LoggerFactory.getLogger(GroupTopicParserR430::class.java)

    val PSEUDO_TOPIC_AUTHOR_UID = 0
    val PSEUDO_TOPIC_AUTHOR_USERNAME = "_pseudo_writer"
    val PSEUDO_TOPIC_AUTHOR = User(
        id = PSEUDO_TOPIC_AUTHOR_UID,
        username = PSEUDO_TOPIC_AUTHOR_USERNAME,
        nickname = PSEUDO_TOPIC_AUTHOR_USERNAME
    )

    private val SDF_YYYY_M_D_HH_MM =
        SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("GMT+08:00") }

    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        require(spaceType == SpaceType.GROUP)
        val document = Jsoup.parse(htmlFileString)
        val notFound = document.select("#colunmNotice > div > p.text").first() != null
        if (notFound) {
            return Pair(
                Topic(
                    id = topicId, space = Reserved(type = spaceType), display = false, state = Post.STATE_DELETED
                ), true
            )
        }

        val titleH1 = document.select("#pageHeader > h1").first()
        val title = titleH1!!.textNodes().lastOrNull()?.text()?.trimStart()
        val groupNameAnchor = document.select("#pageHeader > h1 > span > a.avatar").first() ?: return Pair(
            null,
            false
        ) // 500 or other empty html
        val topPostDiv = document.select("body > div.rakuenTopicList > div.postTopic.light_odd.clearit").first()
        val commentListDiv = document.select("#comment_list").first()

        val meta = mutableMapOf<String, Any>()

        val groupName = extractGroupName(groupNameAnchor)
        val groupDisplayName = extractGroupDisplayName(groupNameAnchor)
        val topPost = extractCommentPostRecursively(topPostDiv, topicId)
        val commentPostList = extractCommentPostList(commentListDiv, topicId)
        val dataLikesList = extractDataLikeList(htmlFileString) // may be introduced in the future, keep it for now

        if (dataLikesList != null) {
            val dataLikesListJson = GitHelper.GSON.fromJson(dataLikesList, JsonObject::class.java)
            meta.put("data_likes_list", dataLikesListJson)
        }
        val postList = mutableListOf<Post>()
        topPost?.apply { postList.add(this) }
        postList.addAll(commentPostList)
        val topicState: Long = extractTopicStateFromPostList(postList)

        return Pair(
            Topic(
                id = topicId,
                space = Group(
                    name = groupName,
                    displayName = groupDisplayName,
                    meta = meta.ifEmpty { null },
                ),
                uid = topPost?.user?.id,
                dateline = topPost?.dateline,
                title = title,
                display = !(topicState == Post.STATE_SILENT || topicState == Post.STATE_CLOSED),
                topPostPid = topPost?.id,
                state = topicState,
                postList = postList
            ),
            true
        )

    }

    private fun extractTopicStateFromPostList(postList: MutableList<Post>): Long {
        var result = Post.STATE_NORMAL
        postList.reversed().forEach  {
            if (it.isReopen()) {
                result = result or Post.STATE_REOPEN
            }
            if (it.isClosed()) {
                result = result or Post.STATE_CLOSED
            }
            if (it.isSilent()) {
                result = result or Post.STATE_SILENT
            }
        }
        return result
    }

    private fun extractDataLikeList(htmlFileString: String): String? {
        return htmlFileString.lineSequence()
            .filter { it.startsWith("var data_likes_list = {") && it.endsWith("};") }
            .firstOrNull()
            ?.substringAfter("=")
            ?.substringBeforeLast(";")
    }

    private fun extractCommentPostList(commentListDiv: Element?, topicId: Int): List<Post> {
        if (commentListDiv == null) return emptyList()
        return commentListDiv.children().mapIndexedNotNull { idx, it ->
            extractCommentPostRecursively(it, topicId, nthCommentFloorFromZero = idx)
        }.toList()
    }

    private fun extractCommentPostRecursively(
        postDiv: Element?,
        topicId: Int,
        isSubReply: Boolean = false,
        mainPostPid: Int? = null,
        nthCommentFloorFromZero: Int = -1
    ): Post? {
        if (postDiv == null) return null
        require((isSubReply && mainPostPid != null) || (!isSubReply && mainPostPid == null))

        extractSpecialBadgePost(postDiv, topicId, nthCommentFloorFromZero)
            ?.apply { return@extractCommentPostRecursively this }

        if (!postDiv.attr("id").startsWith("post_")) return null; // comment folded
        val postId = postDiv.attr("id").substring("post_".length).trim().toInt()
        val username = postDiv.attr("data-item-user")
        if (username.isBlank()) return null // Bad empty user
        val floorAndDateText = postDiv.select("div.post_actions.re_info div.action").first()!!.text()
        val poundMarkFloorDashSubFloor = floorAndDateText.split(" - ").first()
        val floorNum = poundMarkFloorDashSubFloor.substring(1).split("-").first().toIntOrNull()
            ?: throw IllegalStateException("$poundMarkFloorDashSubFloor can't be converted to subfloor")
        val subFloorNum =
            if (!isSubReply) null else poundMarkFloorDashSubFloor.substring(1).split("-").last().toIntOrNull()
                ?: throw IllegalStateException("$poundMarkFloorDashSubFloor can't be converted to subfloor")
        val dateline = floorAndDateText.split(" - ").last().let { SDF_YYYY_M_D_HH_MM.parse(it).time / 1000 }
        val uid: Int? = guessUid(postDiv, username)
        val userNickname = postDiv.select("div.inner strong > a").first()?.text()
        val userSign = postDiv.select("div.inner span.tip_j").first()?.text().let {
            if (it == null) return@let null
            if (it.length >= 2) return@let it.substring(1, it.length - 1)
            return@let null
        }
        val contentHtml =
            if (isSubReply) {
                postDiv.select("div.inner > div.cmt_sub_content")
                    .first()?.html()
            } else {
                /* Comment list */
                postDiv.select("div.inner > div.reply_content > div.message")
                    .first()?.html()
                    ?:
                    /* Top post */
                    postDiv.select("div.inner > div.topic_content")
                        .first()?.html()
            }

        val state = PostToStateHelper.fromPostHtmlToState(contentHtml ?: "")

        val subReplies =
            postDiv.select("div.inner > div.reply_content > div.topic_sub_reply").first()?.children()
        val subReplyPostList =
            if (!isSubReply && subReplies != null) {
                subReplies.mapNotNull {
                    extractCommentPostRecursively(it, topicId, isSubReply = true, mainPostPid = postId)
                }.toList()
            } else null

        return Post(
            id = postId,
            floorNum = floorNum,
            subFloorNum = subFloorNum,
            mid = topicId,
            dateline = dateline,
            contentHtml = contentHtml,
            subFloorList = subReplyPostList,
            state = state,
            related = mainPostPid,
            user = User(
                id = uid,
                nickname = userNickname,
                sign = userSign,
                username = username
            )
        )
    }

    private fun extractDatelineFromDateString(str: String): Long {
        val datelineStr =
            str.split(" ").filterIndexed { idx, _ -> idx > 1 }.joinToString(separator = " ")
        return SDF_YYYY_M_D_HH_MM.parse(datelineStr).time / 1000
    }


    private fun extractSpecialBadgePost(postDiv: Element, topicId: Int, nthCommentFloorFromZero: Int): Post? {
        val badgeState = extractSpecialBadgePostState(postDiv) ?: return null
        if (!postDiv.attr("id").startsWith("post_")) return null // comment folded
        val postId = postDiv.attr("id").substring("post_".length).trim().toInt()
        val dateSpan = postDiv.select("div > span.tip_j").first()
        val dateline = dateSpan!!.text().let { SDF_YYYY_M_D_HH_MM.parse(it).time/1000 }
        val avatarAnchor = postDiv.select("a.avatar").first()

        val username = avatarAnchor!!.attr("href").split("/").last()
        val uid = guessUid(postDiv, username)
        val userNickname = postDiv.select("div.inner > strong > a").first()!!.text()

        val html = postDiv.select("div.inner").first()!!.html()

        return Post(
            id = postId,
            floorNum = nthCommentFloorFromZero + 2,
            mid = topicId,
            dateline = dateline,
            contentHtml = html,
            state = badgeState,
            user = User(
                id = uid,
                nickname = userNickname,
                username = username
            )
        )
    }

    private fun extractSpecialBadgePostState(postDiv: Element): Long? {
        if (postDiv.select("span.badgeStateSilent").isNotEmpty()) {
            return Post.STATE_SILENT
        }
        if (postDiv.select("span.badgeStateClosed").isNotEmpty()) {
            return Post.STATE_CLOSED
        }
        if (postDiv.select("span.badgeStateReopen").isNotEmpty()) {
            return Post.STATE_REOPEN
        }
        return null
    }

    private fun guessUid(postDiv: Element, username: String): Int? {
        // val uidFromReplyAction = postDiv.select("div.post_actions.re_info > div:nth-child(2) > a").first().let outer@{
        //     if (it == null) return@outer null
        //     val onclickJsFun = it.attr("onclick")
        //     val uidStr = onclickJsFun.split(",").let inner@{ splitArr ->
        //         if (splitArr.size < 2) return@inner null
        //         return@inner splitArr[splitArr.size - 2].trim()
        //     }
        //     return@outer uidStr?.toIntOrNull()
        // }
        val uidFromAvatarBg = postDiv.select("a.avatar > span").first().let outer@{
            if (it == null) return@outer null
            val style = it.attr("style")
            val uid = getUidFromBgStyle(style)
            return@outer uid
        }
        // if (uidFromReplyAction != null) return uidFromReplyAction
        if (uidFromAvatarBg != null) return uidFromAvatarBg
        return ParserHelper.guessUidFromUsername(username)
    }


    private fun extractCharacterDescHtmlToPost(characterDescDiv: Element?): Post {
        return Post(
            id = -1,
            floorNum = 0,
            mid = -1,
            dateline = -1L,
            contentHtml = characterDescDiv?.html() ?: "",
            user = PSEUDO_TOPIC_AUTHOR,
            state = Post.STATE_NORMAL
        )
    }

    private fun extractGroupDisplayName(characterNameAnchor: Element?): String? {
        if (characterNameAnchor == null) return null
        return characterNameAnchor.text().trim()
    }

    private fun extractGroupName(characterNameAnchor: Element?): String? {
        if (characterNameAnchor == null) return null
        return characterNameAnchor.attr("href").split("/").last()
    }

}