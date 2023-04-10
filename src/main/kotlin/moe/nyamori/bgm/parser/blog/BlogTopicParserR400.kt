package moe.nyamori.bgm.parser.blog

import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.parser.Parser
import moe.nyamori.bgm.util.ParserHelper
import moe.nyamori.bgm.util.XPathHelper.XP_404_MSG
import org.seimicrawler.xpath.JXDocument
import org.seimicrawler.xpath.JXNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

object BlogTopicParserR400 : Parser {
    private val LOGGER: Logger = LoggerFactory.getLogger(BlogTopicParserR400.javaClass)
    private val SDF_YYYY_M_D_HH_MM =
        SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("GMT+08:00") }

    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        if (spaceType != SpaceType.BLOG) throw IllegalStateException("Should parse a blog topic but got $spaceType")
        try {
            val doc: JXDocument = JXDocument.create(htmlFileString)
            val bodyNode = doc.selNOne("body")
            var precheckResult: Pair<Topic?, Boolean>?
            if (ParserHelper.precheck(topicId, bodyNode, spaceType).also { precheckResult = it } != null) {
                return precheckResult!!
            }

            // user nickname, blog title
            val blogTitleH1: JXNode = bodyNode.selOne("/div[1]/div[2]/div[1]/div[1]/div[1]/h1")!!
            // blog post date
            val blogPostDateSmallText: JXNode =
                bodyNode.selOne("/div[1]/div[2]/div[1]/div[1]/div[2][@class=\"re_info\"]/small/text()")
            // main content
            val blogEntryDiv: JXNode =
                bodyNode.selOne("/div[1]/div[2]/div[1]/div[1]/div[3]/div[1][@id=\"entry_content\"]")
            val blogTagDiv = bodyNode.selOne("/div[1]/div[2]/div[1]/div[1]/div[3]/div[2][@class=\"tags\"]") // nullable
            val blogRelatedSubjectUl = bodyNode.selOne("/div[1]/div[2]/div[1]/div[2]/div/ul[1]") // nullable

            // comment list
            val blogCommentListDiv: JXNode = bodyNode.selOne("/div[1]/div[2]/div[1]/div[1]/div[4]") // nullable

            val (blogTopicWithoutCommentList, blogPostUser) =
                extractBlogInfo(topicId, blogTitleH1, blogPostDateSmallText, blogTagDiv, blogRelatedSubjectUl)

            val blogPostList: MutableList<Post> =
                extractBlogEntryAndCommentList(
                    topicId,
                    blogPostUser,
                    blogTopicWithoutCommentList.dateline!!,
                    blogEntryDiv,
                    blogCommentListDiv
                )

            blogTopicWithoutCommentList.postList = blogPostList

            return Pair(blogTopicWithoutCommentList, true)
        } catch (ex: Exception) {
            LOGGER.error("Ex: ", ex)
            return Pair(null, false)
        }
    }

    private fun extractBlogInfo(
        blogId: Int,
        blogTitleH1: JXNode,
        blogPostDateSmallText: JXNode,
        blogTagDiv: JXNode?,
        blogRelatedSubjectUl: JXNode?
    ): Pair<Topic, User> {
        val blogTitle = blogTitleH1.selOne("/text()").asString()
        val avatarAnchor: JXNode = blogTitleH1.selOne("/span/a[1]")

        val dateSmallText = blogPostDateSmallText.asString()
        val dateline = ParserHelper.parsePostDate(dateSmallText).time / 1000

        val blogPostUser = extractBlogPostUser(avatarAnchor)

        var tagList: List<String>? = null
        var relatedSubjectIdList: List<Int>? = null

        if (blogTagDiv != null) {
            val tagAnchorList = blogTagDiv.sel("/a")
            tagList = extractTags(tagAnchorList)
        }

        if (blogRelatedSubjectUl != null) {
            val relatedSubjectLiList = blogRelatedSubjectUl.sel("/li")
            relatedSubjectIdList = extractRelatedSubjectIdList(relatedSubjectLiList)
        }

        return Pair(
            Topic(
                id = blogId,
                space = Blog(
                    tags = tagList,
                    relatedSubjectIds = relatedSubjectIdList
                ),

                uid = blogPostUser.id,
                title = blogTitle,
                dateline = dateline,
                display = true,
                topPostPid = -blogId
            ), blogPostUser
        )
    }

    private fun extractBlogPostUser(avatarAnchor: JXNode): User {
        val userNickname = avatarAnchor.selOne("/text()").asString()
        val userHref = avatarAnchor.asElement().attr("href")
        val userAvatarImgSrc = avatarAnchor.selOne("/img").asElement().attr("src")
        val usernameFromHref = userHref.substring(userHref.lastIndexOf("/") + 1)
        val uidFromAvatar =
            getUidFromAvatarBgOrSrc(userAvatarImgSrc)
        val uidGuessing = ParserHelper.guessUidFromUsername(usernameFromHref)
        return User(
            id = if (uidFromAvatar > 0) {
                uidFromAvatar
            } else uidGuessing,
            nickname = userNickname,
            username = usernameFromHref
        )
    }

    private fun getUidFromAvatarBgOrSrc(userAvatarImgSrc: String): Int {
        return userAvatarImgSrc.substring(userAvatarImgSrc.lastIndexOf("/") + 1, userAvatarImgSrc.lastIndexOf("jpg"))
            .toCharArray()
            .filter { it.isDigit() }.joinToString(separator = "").let {
                if (it.isBlank()) {
                    return@let -1
                }
                it.toInt()
            }
    }

    private fun extractRelatedSubjectIdList(relatedSubjectLiList: List<JXNode>): List<Int> {
        return relatedSubjectLiList.mapIndexed { idx, li ->
            if (idx != li.asElement().elementSiblingIndex()) return@mapIndexed null

            val innerAnchor = li.selOne("/a")
            val subjectHref = innerAnchor.asElement().attr("href")
            val subjectId = subjectHref.substring(subjectHref.lastIndexOf("/") + 1).toInt()

            return@mapIndexed subjectId
        }
            .filterNotNull()
    }

    private fun extractTags(tagAnchorList: List<JXNode>): List<String> {
        return tagAnchorList.mapIndexed() { idx, anchor ->
            // Workaround for bug
            if (idx != anchor.asElement().elementSiblingIndex()) return@mapIndexed null
            return@mapIndexed anchor.asElement().text()
        }
            .filterNotNull()
    }


    private fun extractBlogEntryAndCommentList(
        blogId: Int,
        blogPostUser: User,
        blogDateline: Long,
        blogEntryDiv: JXNode,
        blogCommentListDiv: JXNode
    ): MutableList<Post> {
        val result = ArrayList<Post>()
        val blogPost: Post = extractBlogEntry(blogId, blogPostUser, blogDateline, blogEntryDiv)
        val commentList: List<Post> = extractCommentList(blogId, blogCommentListDiv)
        result.add(blogPost)
        result.addAll(commentList)
        return result
    }

    private fun extractCommentList(blogId: Int, blogCommentListDiv: JXNode): List<Post> {
        val divList = blogCommentListDiv.sel("/div")
        return divList.mapIndexed { idx, div ->
            if (idx != div.asElement().elementSiblingIndex()) return@mapIndexed null
            return@mapIndexed extractCommentFromCommentPostDiv(blogId, null, div)
        }
            .filterNotNull()
    }

    private fun extractCommentFromCommentPostDiv(
        blogId: Int,
        mainPostId: Int?,
        postDiv: JXNode,
        isSubReply: Boolean = false
    ): Post {
        var possibleSubReplyPostList: List<Post>? = null

        val possibleSubReplyListDiv =
            postDiv.selOne("/div[2]/div[@class=\"reply_content\"]/div[@class=\"topic_sub_reply\"]")
        val postId = postDiv.asElement().attr("id").substring("post_".length).toInt()

        if (possibleSubReplyListDiv != null) {
            if(isSubReply) throw IllegalStateException("Sub reply should not have its sub replies!")
            possibleSubReplyPostList = ArrayList()
            val subReplyDivList = possibleSubReplyListDiv.sel("/div")
            subReplyDivList.forEachIndexed { idx, innerDiv ->
                if (idx != innerDiv.asElement().elementSiblingIndex()) return@forEachIndexed
                possibleSubReplyPostList.add(
                    extractCommentFromCommentPostDiv(
                        blogId,
                        postId,
                        innerDiv,
                        isSubReply = true
                    )
                )
            }
        }

        val reInfoDiv = postDiv.selOne("/div[1][contains(@class,\"re_info\")]")
        val reInfoSmall = reInfoDiv.selOne("/div[@class=\"action\"]/small")
        val reInfoAnchor = reInfoSmall.selOne("/a")
        val avatarAnchor = postDiv.selOne("/a")
        val avatarAnchorBgSpan = avatarAnchor.selOne("/span")
        val innerDiv = postDiv.selOne("/div[@class=\"inner\"]")
        val userStrong = innerDiv.selOne("/strong")
        val userSignSpan = innerDiv.selOne("/span[@class=\"tip_j\"]")

        val floorText = reInfoAnchor.asElement().text()
        var mainFloorNum: Int = -1
        var subFloorNum: Int? = null

        if (floorText.indexOf("-") >= 0) {
            subFloorNum = floorText.substring(floorText.indexOf("-") + 1).toInt()
            mainFloorNum = floorText.substring(floorText.indexOf("#") + 1, floorText.indexOf("-")).toInt()
        } else {
            if (isSubReply) throw IllegalStateException("Should be a sub reply but got no sub floor number!")
            mainFloorNum = floorText.substring(floorText.indexOf("#") + 1).toInt()
        }

        val dateTextWithoutTrimming = reInfoSmall.asElement().text()
        val dateText = dateTextWithoutTrimming.split(" ").filterIndexed { idx, _ -> idx > 1 }.joinToString(separator = " ")
        val dateline = SDF_YYYY_M_D_HH_MM.parse(dateText).time / 1000

        val avatarAnchorHref = avatarAnchor.asElement().attr("href")
        val usernameFromAvatarAnchor = avatarAnchorHref.substring(avatarAnchorHref.lastIndexOf("/") + 1)
        val avatarBgSpanStyle = avatarAnchorBgSpan.asElement().attr("style")
        val uidFromAvatarBg = getUidFromAvatarBgOrSrc(avatarBgSpanStyle)
        var userSign: String? = null
        if (userSignSpan != null) {
            val userSignSpanText = userSignSpan.asElement().text()
            userSign = userSignSpanText.substring(
                userSignSpanText.indexOf("(").let { if (it < 0) 0 else it } + 1,
                userSignSpanText.lastIndexOf(")").let { if (it < 0) userSignSpanText.length - 1 else it }
            )
        }
        val userNickname = userStrong.asElement().text()
        val commentUser: User = makeCommentUser(usernameFromAvatarAnchor, uidFromAvatarBg, userNickname, userSign)


        val possibleMainReplyDiv = innerDiv.selOne("/div[@class=\"reply_content\"]/div[contains(@class,\"message\")]")
        val possibleSubReplyDiv = innerDiv.selOne("/div[@class=\"cmt_sub_content\"]")

        val replyContentHtml: String
        if (possibleMainReplyDiv != null) {
            replyContentHtml = possibleMainReplyDiv.asElement().html()
        } else {
            if (!isSubReply) throw IllegalStateException("Should be a main reply but can not found main reply div!")
            replyContentHtml = possibleSubReplyDiv.asElement().html()
        }

        return Post(
            id = postId,
            user = commentUser,
            floorNum = mainFloorNum,
            subFloorNum = subFloorNum,
            mid = blogId,
            related = mainPostId,
            contentHtml = replyContentHtml,
            state = Post.STATE_NORMAL,
            dateline = dateline,
            subFloorList = possibleSubReplyPostList
        )
    }

    private fun makeCommentUser(
        usernameFromAvatarAnchor: String,
        uidFromAvatarBg: Int,
        userNickname: String,
        userSign: String?
    ): User {
        val uidGuessing = ParserHelper.guessUidFromUsername(usernameFromAvatarAnchor)
        return User(
            id = if (uidFromAvatarBg > 0) {
                uidFromAvatarBg
            } else uidGuessing,
            nickname = userNickname,
            username = usernameFromAvatarAnchor,
            sign = userSign
        )
    }

    private fun extractBlogEntry(blogId: Int, blogPostUser: User, blogDateline: Long, blogEntryDiv: JXNode): Post {
        return Post(
            id = -blogId,
            user = blogPostUser,
            floorNum = 0,
            mid = blogId,
            contentHtml = blogEntryDiv.asElement().html(),
            state = Post.STATE_NORMAL,
            dateline = blogDateline
        )
    }

}