package moe.nyamori.bgm.parser.subject

import com.google.gson.JsonObject
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.parser.Parser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.HashMap

object SubjectTopicParserR412 : Parser {

    private val LOGGER: Logger = LoggerFactory.getLogger(SubjectTopicParserR412.javaClass)


    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        val res = SubjectTopicParserR402.parseTopic(htmlFileString, topicId, spaceType)
        if (!res.second || res.first!!.space!! is Reserved) return res
        val metaMap = HashMap<String, Any>()
        val dataLikesList: String? =
            htmlFileString.lineSequence().filter { it.startsWith("var data_likes_list = {") && it.endsWith("};") }
                .firstOrNull()
                ?.substringAfter("=")
                ?.substringBeforeLast(";")
        if (dataLikesList != null) {
            val dataLikesListJson = GitHelper.GSON.fromJson(dataLikesList, JsonObject::class.java)
            metaMap["data_likes_list"] = dataLikesListJson
        }
        return Pair(
            res.first!!.copy(
                space = Subject(
                    meta = metaMap.ifEmpty { null },
                    name = (res.first!!.space as Subject).name,
                    displayName = (res.first!!.space as Subject).displayName
                )
            ), true
        )

    }

}