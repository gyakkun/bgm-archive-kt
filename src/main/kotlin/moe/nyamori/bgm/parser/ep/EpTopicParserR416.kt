package moe.nyamori.bgm.parser.ep

import com.google.gson.JsonObject
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.parser.Parser
import moe.nyamori.bgm.util.ParserHelper
import moe.nyamori.bgm.util.PostToStateHelper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*

object EpTopicParserR416 : Parser {
    private val LOGGER: Logger = LoggerFactory.getLogger(EpTopicParserR416::class.java)

    const val PSEUDO_TOPIC_AUTHOR_UID = 0
    const val PSEUDO_TOPIC_AUTHOR_USERNAME = "_pseudo_writer"
    val PSEUDO_TOPIC_AUTHOR = User(
        id = PSEUDO_TOPIC_AUTHOR_UID,
        username = PSEUDO_TOPIC_AUTHOR_USERNAME,
        nickname = PSEUDO_TOPIC_AUTHOR_USERNAME
    )
    private val SDF_YYYY_MM_DD_UTC =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("GMT+00:00") }
    private val SDF_YYYY_M_D_HH_MM =
        SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("GMT+08:00") }

    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        require(spaceType == SpaceType.EP) {
            "${spaceType.name} not supported by this parser!"
        }

        val document = Jsoup.parse(htmlFileString)
        val notFound = document.select("#colunmNotice > div > p.text").first() != null
        if (notFound) {
            return Pair(
                Topic(
                    id = topicId, space = Reserved(type = spaceType), display = false, state = Post.STATE_DELETED
                ), true
            )
        }
        val subjectTitleAnchor = document.select("#headerSubject > h1 > a").first()
        val episodeTitleH2 = document.select("#columnEpA > h2").first()
        val realEpisodeIdAnchor = episodeTitleH2?.select("small > a")?.first()
        val episodeDescDiv = document.select("#columnEpA > div.epDesc").first()
        val episodeOnAirDateTip = episodeDescDiv?.select("span.tip")?.first()

        val commentListDiv = document.select("#comment_list").first()

        val meta = mutableMapOf<String, Any>()

        val onAirDateStr = extractOnAirDateFromTip(episodeOnAirDateTip)
        val onAirDateLine: Long? = runCatching { SDF_YYYY_MM_DD_UTC.parse(onAirDateStr).time / 1000 }.onFailure { ex ->
            LOGGER.debug("Error parsing date $onAirDateStr", ex)
        }.getOrNull()
        val spaceName = extractSubjectId(subjectTitleAnchor)
        val spaceDisplayName = extractSubjectTitle(subjectTitleAnchor)
        val epTitle = extractEpisodeTitle(episodeTitleH2)
        val epId = extractEpisodeRealId(realEpisodeIdAnchor) ?: topicId
        val epDescHtmlPost = extractEpisodeDescHtmlToPost(episodeDescDiv)
        val commentPostList = extractCommentPostList(commentListDiv, epId)
        val dataLikesList = extractDataLikeList(htmlFileString)

        if (onAirDateStr != null) {
            meta.put("onAirDate", onAirDateStr)
        }
        if (dataLikesList != null) {
            val dataLikesListJson = GitHelper.GSON.fromJson(dataLikesList, JsonObject::class.java)
            meta.put("data_likes_list", dataLikesListJson)
        }
        val postList = mutableListOf<Post>()
        postList.add(
            epDescHtmlPost.copy(
                dateline = onAirDateLine ?: 0L,
                id = -epId,
                mid = epId
            )
        )
        postList.addAll(commentPostList)


        return Pair(
            Topic(
                id = epId,
                space = Ep(
                    meta = meta.ifEmpty { null },
                    name = spaceName?.toString(),
                    displayName = spaceDisplayName,
                ),
                uid = PSEUDO_TOPIC_AUTHOR_UID,
                dateline = onAirDateLine,
                title = epTitle,
                display = true,
                topPostPid = -epId,
                state = Post.STATE_NORMAL,
                postList = postList
            ),
            true
        )

    }

    private fun extractDataLikeList(htmlFileString: String): String? {
        return htmlFileString.lineSequence().filter { it.startsWith("var data_likes_list = {") && it.endsWith("};") }
            .firstOrNull()
            ?.substringAfter("=")
            ?.substringBeforeLast(";")
    }

    private fun extractCommentPostList(commentListDiv: Element?, epId: Int): List<Post> {
        if (commentListDiv == null) return emptyList()
        return commentListDiv.children().mapNotNull {
            extractCommentPostRecursively(it, epId)
        }.toList()
    }

    private fun extractCommentPostRecursively(
        postDiv: Element?,
        epId: Int,
        isSubReply: Boolean = false,
        mainPostPid: Int? = null
    ): Post? {
        if (postDiv == null) return null
        require((isSubReply && mainPostPid != null) || (!isSubReply && mainPostPid == null))
        if (!postDiv.attr("id").startsWith("post_")) return null; // comment folded
        val postId = postDiv.attr("id").substring("post_".length).trim().toInt()
        val username = postDiv.attr("data-item-user")
        val floorNum = postDiv.attr("name").substring("floor-".length)
            .split("-")[0].trim().toInt()
        val subFloorNum = if (!isSubReply) null else postDiv.attr("name").substring("floor-".length)
            .split("-")[1].trim().toInt()
        val uid: Int? = guessUid(postDiv, username)
        val userNickname = postDiv.select("div.inner > strong > a").first()?.text()
        val userSign = postDiv.select("div.inner > span.tip_j").first()?.text().let {
            if (it == null) return@let null
            if (it.length >= 2) return@let it.substring(1, it.length - 1)
            return@let null
        }
        val contentHtml =
            if (isSubReply) {
                postDiv.select("div.inner > div.cmt_sub_content")
                    .first()?.html()
            } else {
                postDiv.select("div.inner > div.reply_content > div.message")
                    .first()?.html()
            }

        val dateline = postDiv.select("div.post_actions.re_info > div:nth-child(1) > small").text().let {
            val datelineStr = it.split(" ").filterIndexed { idx, _ -> idx > 1 }.joinToString(separator = " ")
            return@let SDF_YYYY_M_D_HH_MM.parse(datelineStr).time / 1000
        }
        val state = PostToStateHelper.fromPostHtmlToState(contentHtml ?: "")

        val subReplies = postDiv.select("div.inner > div.reply_content > div.topic_sub_reply").first()?.children()
        val subReplyPostList =
            if (!isSubReply && subReplies != null) {
                subReplies.mapNotNull {
                    extractCommentPostRecursively(it, epId, isSubReply = true, mainPostPid = postId)
                }.toList()
            } else null

        return Post(
            id = postId,
            floorNum = floorNum,
            subFloorNum = subFloorNum,
            mid = epId,
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
            val uidStr = style.split("/").let inner@{ splitArr ->
                if (splitArr.isEmpty()) return@inner null
                val charArr = splitArr[splitArr.size - 1].toCharArray()
                if (charArr.isEmpty() || !charArr[0].isDigit()) return@inner null
                val anotherSplit = splitArr[splitArr.size - 1].split(".")
                if (anotherSplit.isEmpty()) return@inner null
                return@inner anotherSplit[0].trim()
            }
            return@outer uidStr?.toIntOrNull()
        }
        // if (uidFromReplyAction != null) return uidFromReplyAction
        if (uidFromAvatarBg != null) return uidFromAvatarBg
        return ParserHelper.guessUidFromUsername(username)
    }


    private fun extractEpisodeDescHtmlToPost(episodeDescDiv: Element?): Post {
        return Post(
            id = -1,
            floorNum = 0,
            mid = -1,
            dateline = -1L,
            contentHtml = episodeDescDiv?.html() ?: "",
            user = PSEUDO_TOPIC_AUTHOR,
            state = Post.STATE_NORMAL
        )
    }

    private fun extractEpisodeRealId(realEpisodeIdAnchor: Element?): Int? {
        if (realEpisodeIdAnchor == null) return null
        return realEpisodeIdAnchor.attr("href").split("/").firstOrNull {
            it.isNotBlank() && it.toCharArray().all { it.isDigit() }
        }?.toIntOrNull()
    }

    private fun extractEpisodeTitle(episodeTitleH2: Element?): String? {
        if (episodeTitleH2 == null) return null
        return episodeTitleH2.textNodes().firstOrNull()?.text()?.trim()
    }

    private fun extractSubjectTitle(subjectTitleAnchor: Element?): String? {
        if (subjectTitleAnchor == null) return null
        return subjectTitleAnchor.text().trim()
    }

    private fun extractSubjectId(subjectTitleAnchor: Element?): Int? {
        if (subjectTitleAnchor == null) return null
        return subjectTitleAnchor.attr("href").split("/").last().toIntOrNull()
    }

    val ON_AIR_DATE_REGEX = Regex("首播:(.+)$")
    private fun extractOnAirDateFromTip(episodeOnAirDateTip: Element?): String? {
        if (episodeOnAirDateTip == null) return null
        val iter = ON_AIR_DATE_REGEX.findAll(episodeOnAirDateTip.text())
            .iterator()
        if (!iter.hasNext()) return null
        return iter.next().groupValues[1]
    }

}