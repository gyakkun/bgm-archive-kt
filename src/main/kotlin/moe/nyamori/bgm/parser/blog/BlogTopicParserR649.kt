package moe.nyamori.bgm.parser.blog

import com.google.gson.JsonObject
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.parser.Parser
import moe.nyamori.bgm.util.ParserHelper
import org.seimicrawler.xpath.JXDocument
import org.seimicrawler.xpath.JXNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

object BlogTopicParserR649 : Parser {
    private val LOGGER: Logger = LoggerFactory.getLogger(BlogTopicParserR649.javaClass)
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
            val blogTitleH1: JXNode = bodyNode.selOne("/div[1]/div[2]/div[1]/div[1]/div[1]/div[2]/h1[contains(@class,\"title\")]")!!
            val authorUserCardDiv: JXNode = bodyNode.selOne("/div[1]/div[2]/div[1]/div[1]/div[1]/div[1][contains(@class,\"author\")][contains(@class,\"user-card\")]")
            // blog post date
            val blogPostDateSmallText: JXNode =
                bodyNode.selOne("/div[1]/div[2]/div[1]/div[1]/div[1]/div[2]/div/div[1][contains(@class,\"time\")]")
            // main content
            val blogEntryDiv: JXNode =
                bodyNode.selOne("/div[1]/div[2]/div[1]/div[1]/div[1]/div[3][@id=\"entry_content\"]")
            val blogTagDiv =
                bodyNode.selOne("/div[1]/div[2]/div[1]/div[1]/div[1]/div[2]/div/div[2][contains(@class,\"tags\")]") // nullable
            val blogRelatedSubjectDiv = bodyNode.selOne("/div[1]/div[2]/div[1]/div[2]/div[contains(@class,\"entry-related-subjects\")]") // nullable, many div.card div.subject-card inside

            // comment list R547
            val blogCommentListDiv: JXNode = bodyNode.selOne("//*[@id=\"comment_list\"]") // nullable

            var meta: Map<String, Any>? = null
            val dataLikesList = extractDataLikeList(htmlFileString) // may be introduced in the future, keep it for now

            if (dataLikesList != null) {
                val dataLikesListJson = GitHelper.GSON.fromJson(dataLikesList, JsonObject::class.java)
                meta = mutableMapOf()
                meta.put("data_likes_list", dataLikesListJson)
            }

            val (blogTopicWithoutCommentList, blogPostUser) =
                extractBlogInfo(topicId, blogTitleH1, blogPostDateSmallText, blogTagDiv, blogRelatedSubjectDiv, authorUserCardDiv, meta)

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
        blogRelatedSubjectDiv: JXNode?,
        authorUserCardDiv: JXNode,
        meta: Map<String, Any>?
    ): Pair<Topic, User> {
        val blogTitle = blogTitleH1.selOne("/text()").asString()
        // val avatarAnchor: JXNode = blogTitleH1.selOne("/span/a[1]")

        var dateSmallText = blogPostDateSmallText.asElement().text()
        // 2025-9-6 14:45 · 24 分钟阅读
        if ("·" in dateSmallText) dateSmallText = dateSmallText.substringBefore("·").trim()
        val dateline = ParserHelper.parsePostDate(dateSmallText).time / 1000

        val blogPostUser = extractBlogPostUser(authorUserCardDiv)

        var tagList: List<String>? = null
        var relatedSubjectIdList: List<Int>? = null

        if (blogTagDiv != null) {
            val tagAnchorList = blogTagDiv.sel("/a")
            tagList = extractTags(tagAnchorList)
        }

        if (blogRelatedSubjectDiv != null) {
            val relatedSubjectDivList = blogRelatedSubjectDiv.sel("/div[contains(@class,\"subject-card\")][contains(@class,\"card\")]")
            relatedSubjectIdList = extractRelatedSubjectIdList(relatedSubjectDivList)
        }

        return Pair(
            Topic(
                id = blogId,
                space = Blog(
                    tags = tagList,
                    relatedSubjectIds = relatedSubjectIdList,
                    meta = meta
                ),

                uid = blogPostUser.id,
                title = blogTitle,
                dateline = dateline,
                display = true,
                topPostPid = -blogId
            ), blogPostUser
        )
    }

    private fun extractDataLikeList(htmlFileString: String): String? {
        return htmlFileString.lineSequence()
            .filter { it.startsWith("var data_likes_list = {") && it.endsWith("};") }
            .firstOrNull()
            ?.substringAfter("=")
            ?.substringBeforeLast(";")
    }

    private fun extractBlogPostUser(authorUserCardDiv: JXNode): User {
        val userNickname = authorUserCardDiv.selOne("/div[contains(@class,\"title\")]/p/a/text()").asString()
        val userHref = authorUserCardDiv.selOne("/div[contains(@class,\"title\")]/p/a").asElement().attr("href")
        val userAvatarImgSrc = authorUserCardDiv.selOne("/a/img").asElement().attr("src")
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
            .takeWhile { it.isDigit() }
            .joinToString(separator = "").let {
                if (it.isBlank()) {
                    return@let -1
                }
                it.toInt()
            }
    }

    private fun extractRelatedSubjectIdList(relatedSubjectDivList: List<JXNode>): List<Int> {
        return relatedSubjectDivList.mapIndexed { idx, cardDiv ->
            if (idx != cardDiv.asElement().elementSiblingIndex()) {
                // expected because it has a sister <h2 class="subtitle">关联条目</h2>
                // return@mapIndexed null
            }
            val containerDiv = cardDiv.selOne("/div[contains(@class,\"container\")]")
            val innerAnchor = containerDiv.selOne("/a[1]")
            val subjectHref = innerAnchor.asElement().attr("href")
            val subjectId = subjectHref.substring(subjectHref.lastIndexOf("/") + 1).toIntOrNull()
            // val debug = subjectId
            // System.err.println("subject id $debug")
            return@mapIndexed subjectId
        }.filterNotNull()
    }

    private fun extractTags(tagAnchorList: List<JXNode>): List<String> {
        return tagAnchorList.mapIndexed { idx, anchor ->
            // Workaround for bug
            if (idx != anchor.asElement().elementSiblingIndex()) return@mapIndexed null
            val tag = anchor.asElement().text()
            // val debug = tag
            // System.err.println("tag $debug")
            return@mapIndexed tag
        }.filterNotNull()
    }


    private fun extractBlogEntryAndCommentList(
        blogId: Int,
        blogPostUser: User,
        blogDateline: Long,
        blogEntryDiv: JXNode,
        blogCommentListDiv: JXNode
    ): MutableList<Post> {
        val result = mutableListOf<Post>()
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
        }.filterNotNull()
    }

    private fun extractCommentFromCommentPostDiv(
        blogId: Int,
        mainPostId: Int?,
        postDiv: JXNode,
        isSubReply: Boolean = false
    ): Post {
        var possibleSubReplyPostList: List<Post>? = null

        val possibleSubReplyListDiv =
            postDiv.selOne("/div[2]/div[contains(@class,\"reply_content\")]/div[contains(@class,\"topic_sub_reply\")]")
        val postId = postDiv.asElement().attr("id").substring("post_".length).toInt()

        if (possibleSubReplyListDiv != null) {
            if (isSubReply) throw IllegalStateException("Sub reply should not have its sub replies!")
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
            ?: postDiv.selOne("/div[1][contains(@class,\"post_actions\")]")
        val reInfoSmall = reInfoDiv.selOne("/div[contains(@class,\"action\")]//small")
        val reInfoAnchor = reInfoSmall.selOne("/a")
        val avatarAnchor = postDiv.selOne("//a[contains(@class,\"avatar\")]")
        val avatarAnchorBgSpan = avatarAnchor.selOne("/span")
        val innerDiv = postDiv.selOne("/div[contains(@class,\"inner\")]")
        val userStrong = innerDiv.selOne("/strong")
        val userSignSpan = innerDiv.selOne("/span[contains(@class,\"tip_j\")]")

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
        val dateText =
            dateTextWithoutTrimming.split(" ").filterIndexed { idx, _ -> idx > 1 }.joinToString(separator = " ")
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


        val possibleMainReplyDiv =
            innerDiv.selOne("/div[contains(@class,\"reply_content\")]/div[contains(@class,\"message\")]")
        val possibleSubReplyDiv = innerDiv.selOne("/div[contains(@class,\"cmt_sub_content\")]")

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

    @JvmStatic
    fun main(args: Array<String>) {
        val htmlStr = File("E:\\[ToBak]\\Desktop_Win10\\360398.html").readText(StandardCharsets.UTF_8)
        val some = BlogTopicParserR649.parseTopic(htmlStr,360398, SpaceType.BLOG)
        System.err.println("debug")
    }
}