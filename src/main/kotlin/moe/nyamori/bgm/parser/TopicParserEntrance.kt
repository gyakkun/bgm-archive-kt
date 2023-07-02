package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic

object TopicParserEntrance : Parser {
    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        return when (spaceType) {
            SpaceType.GROUP -> GroupTopicParser.parseTopic(htmlFileString, topicId, spaceType)
            SpaceType.SUBJECT -> SubjectTopicParser.parseTopic(htmlFileString, topicId, spaceType)
            SpaceType.BLOG -> BlogTopicParser.parseTopic(htmlFileString, topicId, spaceType)
            SpaceType.EP -> EpTopicParser.parseTopic(htmlFileString, topicId, spaceType)
            SpaceType.CHARACTER -> CharacterTopicParser.parseTopic(htmlFileString, topicId, spaceType)
            else -> {
                throw IllegalArgumentException("${spaceType.name} not supported for now!")
            }
        }
    }

}