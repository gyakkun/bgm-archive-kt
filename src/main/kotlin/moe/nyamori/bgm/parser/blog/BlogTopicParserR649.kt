package moe.nyamori.bgm.parser.blog

import com.google.gson.JsonObject
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.parser.Parser
import moe.nyamori.bgm.util.ParserHelper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*

object BlogTopicParserR649 : Parser {
    private val LOGGER: Logger = LoggerFactory.getLogger(BlogTopicParserR649.javaClass)
    private val SDF_YYYY_M_D_HH_MM =
        SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("GMT+08:00") }

    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        if (spaceType != SpaceType.BLOG) throw IllegalStateException("Should parse a blog topic but got $spaceType")
        try {
            val document = Jsoup.parse(htmlFileString)
            val body = document.body()!!
            val precheckResult = ParserHelper.precheck(topicId, body, spaceType)
            if (precheckResult != null) return precheckResult

            val blogTitleH1 = body.selectFirst("h1.title")!!
            val authorUserCardDiv = body.selectFirst("div.author.user-card")!!
            val blogPostDateDiv = body.selectFirst("div.time")
            val blogEntryDiv = body.selectFirst("div#entry_content")
            val blogTagDiv = body.selectFirst("div.tags")
            val blogRelatedSubjectDiv = body.selectFirst("div.entry-related-subjects")
            val blogCommentListDiv = document.selectFirst("#comment_list")

            var meta: Map<String, Any>? = null
            val dataLikesList = ParserHelper.extractDataLikeList(htmlFileString)
            if (dataLikesList != null) {
                val dataLikesListJson = GitHelper.GSON.fromJson(dataLikesList, JsonObject::class.java)
                meta = mutableMapOf("data_likes_list" to dataLikesListJson)
            }

            val (blogTopic, blogPostUser) = extractBlogInfo(
                topicId, blogTitleH1, blogPostDateDiv, blogTagDiv, blogRelatedSubjectDiv, authorUserCardDiv, meta
            )
            val blogPostList = extractBlogEntryAndCommentList(
                topicId, blogPostUser, blogTopic.dateline!!, blogEntryDiv, blogCommentListDiv
            )
            blogTopic.postList = blogPostList
            return Pair(blogTopic, true)
        } catch (ex: Exception) {
            LOGGER.error("Ex: ", ex)
            return Pair(null, false)
        }
    }

    private fun extractBlogInfo(
        blogId: Int, blogTitleH1: Element, blogPostDateDiv: Element?,
        blogTagDiv: Element?, blogRelatedSubjectDiv: Element?,
        authorUserCardDiv: Element, meta: Map<String, Any>?
    ): Pair<Topic, User> {
        val blogTitle = blogTitleH1.ownText()
        var dateSmallText = blogPostDateDiv!!.text()
        if ("·" in dateSmallText) dateSmallText = dateSmallText.substringBefore("·").trim()
        val dateline = ParserHelper.parsePostDate(dateSmallText).time / 1000
        val blogPostUser = extractBlogPostUser(authorUserCardDiv)

        val tagList = blogTagDiv?.select("a")?.map { it.text() }
        val relatedSubjectIdList = blogRelatedSubjectDiv?.select("div.subject-card.card")?.mapNotNull { cardDiv ->
            val href = cardDiv.selectFirst("div.container > a")?.attr("href") ?: return@mapNotNull null
            href.substring(href.lastIndexOf("/") + 1).toIntOrNull()
        }

        return Pair(
            Topic(
                id = blogId,
                space = Blog(tags = tagList, relatedSubjectIds = relatedSubjectIdList, meta = meta),
                uid = blogPostUser.id, title = blogTitle, dateline = dateline,
                display = true, topPostPid = -blogId
            ), blogPostUser
        )
    }

    private fun extractBlogPostUser(authorUserCardDiv: Element): User {
        val userNickname = authorUserCardDiv.selectFirst("div.title > p > a")!!.ownText()
        val userHref = authorUserCardDiv.selectFirst("div.title > p > a")!!.attr("href")
        val userAvatarImgSrc = authorUserCardDiv.selectFirst("a > img")!!.attr("src")
        val usernameFromHref = userHref.substring(userHref.lastIndexOf("/") + 1)
        val uidFromAvatar = getUidFromAvatarSrc(userAvatarImgSrc)
        return User(
            id = if (uidFromAvatar > 0) uidFromAvatar else ParserHelper.guessUidFromUsername(usernameFromHref),
            nickname = userNickname, username = usernameFromHref
        )
    }

    private fun getUidFromAvatarSrc(src: String): Int {
        return src.substring(src.lastIndexOf("/") + 1, src.lastIndexOf("jpg"))
            .toCharArray().takeWhile { it.isDigit() }.joinToString("").let {
                if (it.isBlank()) -1 else it.toInt()
            }
    }

    private fun extractBlogEntryAndCommentList(
        blogId: Int, blogPostUser: User, blogDateline: Long,
        blogEntryDiv: Element?, blogCommentListDiv: Element?
    ): MutableList<Post> {
        val result = mutableListOf<Post>()
        result.add(Post(
            id = -blogId, user = blogPostUser, floorNum = 0, mid = blogId,
            contentHtml = blogEntryDiv?.html() ?: "", state = Post.STATE_NORMAL, dateline = blogDateline
        ))
        if (blogCommentListDiv != null) {
            blogCommentListDiv.children().forEachIndexed { idx, div ->
                if (div.tagName() != "div") return@forEachIndexed
                result.add(extractCommentFromDiv(blogId, null, div))
            }
        }
        return result
    }

    private fun extractCommentFromDiv(
        blogId: Int, mainPostId: Int?, postDiv: Element, isSubReply: Boolean = false
    ): Post {
        var possibleSubReplyPostList: List<Post>? = null
        val possibleSubReplyListDiv = postDiv.selectFirst("div.reply_content > div.topic_sub_reply")
        val postIdStr = postDiv.attr("id")
        val postId = if (postIdStr.startsWith("post_")) postIdStr.substring("post_".length).toInt() else 0

        if (possibleSubReplyListDiv != null) {
            if (isSubReply) throw IllegalStateException("Sub reply should not have its sub replies!")
            val subList = ArrayList<Post>()
            possibleSubReplyListDiv.children().forEachIndexed { idx, innerDiv ->
                if (innerDiv.tagName() != "div") return@forEachIndexed
                subList.add(extractCommentFromDiv(blogId, postId, innerDiv, isSubReply = true))
            }
            possibleSubReplyPostList = subList
        }

        val reInfoDiv = postDiv.selectFirst("div.re_info")
            ?: postDiv.selectFirst("div.post_actions")!!
        val reInfoSmall = reInfoDiv.selectFirst("div.action > small")
            ?: reInfoDiv.selectFirst("small")!!
        val reInfoAnchor = reInfoSmall.selectFirst("a")!!
        val avatarAnchor = postDiv.selectFirst("a.avatar")!!
        val avatarBgSpan = avatarAnchor.selectFirst("span")!!
        val innerDiv = postDiv.selectFirst("div.inner")!!
        val userStrong = innerDiv.selectFirst("strong")!!
        val userSignSpan = innerDiv.selectFirst("span.tip_j")

        val floorText = reInfoAnchor.text()
        val mainFloorNum: Int
        val subFloorNum: Int?
        if (floorText.indexOf("-") >= 0) {
            subFloorNum = floorText.substring(floorText.indexOf("-") + 1).toInt()
            mainFloorNum = floorText.substring(floorText.indexOf("#") + 1, floorText.indexOf("-")).toInt()
        } else {
            subFloorNum = null
            mainFloorNum = floorText.substring(floorText.indexOf("#") + 1).toInt()
        }

        val dateText = reInfoSmall.text().split(" ").filterIndexed { idx, _ -> idx > 1 }.joinToString(" ")
        val dateline = SDF_YYYY_M_D_HH_MM.parse(dateText).time / 1000

        val avatarHref = avatarAnchor.attr("href")
        val username = avatarHref.substring(avatarHref.lastIndexOf("/") + 1)
        val uidFromBg = getUidFromAvatarSrc(avatarBgSpan.attr("style"))
        val userSign = userSignSpan?.text()?.let {
            val s = it.indexOf("(").let { i -> if (i < 0) 0 else i } + 1
            val e = it.lastIndexOf(")").let { i -> if (i < 0) it.length - 1 else i }
            if (s < e) it.substring(s, e) else null
        }
        val userNickname = userStrong.text()

        val possibleMainReplyDiv = innerDiv.selectFirst("div.reply_content > div.message")
        val possibleSubReplyDiv = innerDiv.selectFirst("div.cmt_sub_content")
        val replyContentHtml: String = when {
            possibleMainReplyDiv != null -> possibleMainReplyDiv.html()
            isSubReply && possibleSubReplyDiv != null -> possibleSubReplyDiv.html()
            else -> throw IllegalStateException("Cannot find reply content in post $postId")
        }

        return Post(
            id = postId, user = User(
                id = if (uidFromBg > 0) uidFromBg else ParserHelper.guessUidFromUsername(username),
                nickname = userNickname, username = username, sign = userSign
            ),
            floorNum = mainFloorNum, subFloorNum = subFloorNum,
            mid = blogId, related = mainPostId, contentHtml = replyContentHtml,
            state = Post.STATE_NORMAL, dateline = dateline, subFloorList = possibleSubReplyPostList
        )
    }
}