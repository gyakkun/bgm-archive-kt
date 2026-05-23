package moe.nyamori.bgm.parser.mono

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.parser.Parser
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object PersonTopicParserR430 : Parser {
    private val LOGGER: Logger = LoggerFactory.getLogger(PersonTopicParserR430::class.java)
    private val _parser = MonoTopicParserR430(SpaceType.PERSON)
    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        require(spaceType == SpaceType.PERSON)
        return _parser.parseTopic(htmlFileString, topicId, spaceType)
    }


}