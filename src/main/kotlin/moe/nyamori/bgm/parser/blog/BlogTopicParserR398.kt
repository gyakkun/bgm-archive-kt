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

object BlogTopicParserR398 : Parser {
    private val LOGGER: Logger = LoggerFactory.getLogger(BlogTopicParserR398.javaClass)
    private val SDF_YYYY_M_D_HH_MM =
        SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("GMT+08:00") }
    private val SUB_FLOOR_FLOOR_NUM_REGEX = Regex("#\\d+-(\\d+)")

    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        if (spaceType != SpaceType.BLOG) throw IllegalStateException("Should parse a blog topic but got $spaceType")
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
            userAvatarImgSrc.substring(userAvatarImgSrc.lastIndexOf("/") + 1, userAvatarImgSrc.lastIndexOf("?"))
                .toCharArray()
                .filter { it.isDigit() }.joinToString().let {
                    if (it.isBlank()) {
                        return@let -1
                    }
                    it.toInt()
                }
        val uidGuessing = ParserHelper.guessUidFromUsername(usernameFromHref)
        return User(
            id = if (uidFromAvatar > 0) {
                uidFromAvatar
            } else uidGuessing,
            nickname = userNickname,
            username = usernameFromHref
        )
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
        val commentList: List<Post> = extractCommentList(blogCommentListDiv)
        result.add(blogPost)
        result.addAll(commentList)
        return result
    }

    private fun extractCommentList(blogCommentListDiv: JXNode): List<Post> {
        val divList = blogCommentListDiv.sel("/div")
        return divList.mapIndexed { idx, div ->
            if (idx != div.asElement().elementSiblingIndex()) return@mapIndexed null
            return@mapIndexed extractCommentFromCommentPostDiv(div)
        }.filterNotNull()
    }

    private fun extractCommentFromCommentPostDiv(div: JXNode): Post {
        val postId = div.asElement().attr("id").substring("post_".length+1).toInt()
        val floorId = div.asElement().attr("name").substring("floor-".length+1).toInt()
        TODO("TBC")
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