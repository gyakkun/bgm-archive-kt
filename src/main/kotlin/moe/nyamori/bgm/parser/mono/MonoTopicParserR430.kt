package moe.nyamori.bgm.parser.mono

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
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.*

class MonoTopicParserR430(
    val monoSpaceType: SpaceType
) : Parser {
    private val LOGGER: Logger = LoggerFactory.getLogger(MonoTopicParserR430::class.java)

    init {
        require(monoSpaceType in listOf(SpaceType.PERSON, SpaceType.CHARACTER)) {
            "$monoSpaceType should be one of PERSON and CHARACTER"
        }
    }

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

        val document = Jsoup.parse(htmlFileString)
        val notFound = document.select("#colunmNotice > div > p.text").first() != null
        if (notFound) {
            return Pair(
                Topic(
                    id = topicId, space = Reserved(type = spaceType), display = false, state = Post.STATE_DELETED
                ), true
            )
        }

        val characterNameAnchor = document.select("#headerSubject > h1 > a").first()
            ?: return Pair(null, false) // 500 or other empty html
        val characterDescDiv = document.select("#columnCrtB > div.detail").first()
        val commentListDiv = document.select("#comment_list").first()
        val meta = mutableMapOf<String, Any>()

        val characterId = extractCharacterId(characterNameAnchor)
        val spaceName = characterId!!.toString()
        val characterName = extractCharacterName(characterNameAnchor)
        val spaceDisplayName = characterName!!
        val characterDescHtmlPost = extractCharacterDescHtmlToPost(characterDescDiv)
        val commentPostList = extractCommentPostList(commentListDiv, characterId)
        val dataLikesList = ParserHelper.extractDataLikeList(htmlFileString) // may be introduced in the future, keep it for now

        if (dataLikesList != null) {
            val dataLikesListJson = GitHelper.GSON.fromJson(dataLikesList, JsonObject::class.java)
            meta.put("data_likes_list", dataLikesListJson)
        }
        val postList = mutableListOf<Post>()
        postList.add(
            characterDescHtmlPost.copy(
                dateline = 0L,
                id = -characterId,
                mid = characterId
            )
        )
        postList.addAll(commentPostList)


        return Pair(
            Topic(
                id = characterId,
                space = when (monoSpaceType) {
                    SpaceType.CHARACTER -> Character(
                        meta = meta.ifEmpty { null },
                        name = spaceName,
                        displayName = spaceDisplayName,
                    )

                    SpaceType.PERSON -> Person(
                        meta = meta.ifEmpty { null },
                        name = spaceName,
                        displayName = spaceDisplayName,
                    )

                    else -> throw IllegalStateException("$monoSpaceType not supported for mono parser!")
                },
                uid = PSEUDO_TOPIC_AUTHOR_UID,
                dateline = null,
                title = characterName,
                display = true,
                topPostPid = -characterId,
                state = Post.STATE_NORMAL,
                postList = postList
            ),
            true
        )

    }

    private fun extractCommentPostList(commentListDiv: Element?, characterId: Int): List<Post> {
        if (commentListDiv == null) return emptyList()
        return commentListDiv.children().mapNotNull {
            extractCommentPostRecursively(it, characterId)
        }.toList()
    }

    private fun extractCommentPostRecursively(
        postDiv: Element?,
        characterId: Int,
        isSubReply: Boolean = false,
        mainPostPid: Int? = null
    ): Post? {
        if (postDiv == null) return null
        require((isSubReply && mainPostPid != null) || (!isSubReply && mainPostPid == null))
        if (!postDiv.attr("id").startsWith("post_")) return null; // comment folded
        val postId = postDiv.attr("id").substring("post_".length).trim().toInt()
        val username = postDiv.attr("data-item-user")
        if (username.isBlank()) return null // Bad empty user
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

        val dateline = postDiv.select("div.post_actions.re_info > div.action").first()!!.text().let {
            val datelineStr =
                it.split(" ").filterIndexed { idx, _ -> idx > 1 }.joinToString(separator = " ")
            return@let SDF_YYYY_M_D_HH_MM.parse(datelineStr).time / 1000
        }
        val state = PostToStateHelper.fromPostHtmlToState(contentHtml ?: "")

        val subReplies =
            postDiv.select("div.inner > div.reply_content > div.topic_sub_reply").first()?.children()
        val subReplyPostList =
            if (!isSubReply && subReplies != null) {
                subReplies.mapNotNull {
                    extractCommentPostRecursively(it, characterId, isSubReply = true, mainPostPid = postId)
                }.toList()
            } else null

        return Post(
            id = postId,
            floorNum = floorNum,
            subFloorNum = subFloorNum,
            mid = characterId,
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

    private fun extractCharacterName(characterNameAnchor: Element?): String? {
        if (characterNameAnchor == null) return null
        return characterNameAnchor.text().trim()
    }

    private fun extractCharacterId(characterNameAnchor: Element?): Int? {
        if (characterNameAnchor == null) return null
        return characterNameAnchor.attr("href").split("/").last().toIntOrNull()
    }

}