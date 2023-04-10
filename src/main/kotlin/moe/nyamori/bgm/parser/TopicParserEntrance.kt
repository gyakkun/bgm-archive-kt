package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.parser.group.GroupTopicParserR398
import moe.nyamori.bgm.parser.subject.SubjectTopicParserR398
import java.lang.IllegalArgumentException

object TopicParserEntrance : Parser {
    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        return when (spaceType) {
            SpaceType.GROUP -> GroupTopicParser.parseTopic(htmlFileString, topicId, spaceType)
            SpaceType.SUBJECT -> SubjectTopicParser.parseTopic(htmlFileString, topicId, spaceType)
            SpaceType.BLOG -> BlogTopicParser.parseTopic(htmlFileString, topicId, spaceType)
        }
    }

}