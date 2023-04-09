package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.parser.group.GroupTopicParserR398
import moe.nyamori.bgm.parser.subject.SubjectTopicParserR398
import java.lang.IllegalArgumentException

object TopicParserEntranceR398 : Parser {
    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        return when (spaceType) {
            SpaceType.GROUP -> GroupTopicParserR398.parseTopic(htmlFileString, topicId, spaceType)
            SpaceType.SUBJECT -> SubjectTopicParserR398.parseTopic(htmlFileString, topicId, spaceType)
            else -> throw IllegalArgumentException("Not support in this R398 parser: topicId=$topicId, spaceType=$spaceType")
        }
    }

}