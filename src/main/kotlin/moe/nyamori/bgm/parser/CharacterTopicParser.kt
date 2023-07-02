package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.parser.character.CharacterTopicParserR430
import moe.nyamori.bgm.parser.ep.EpTopicParserR416
import moe.nyamori.bgm.util.ParserHelper
import org.slf4j.LoggerFactory
import java.util.*

object CharacterTopicParser : Parser {
    private val LOGGER = LoggerFactory.getLogger(CharacterTopicParser::class.java)
    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        val rev = ParserHelper.getStyleRevNumberFromHtmlString(htmlFileString)
        val parser: Parser? = RevToParserTreeMap.floorEntry(rev)?.value
        if (parser != null) {
            val res = runCatching { parser.parseTopic(htmlFileString, topicId, spaceType) }
                .onFailure {
                    LOGGER.error("Ex when using ${parser.javaClass.simpleName} : ", it)
                    Pair(null, false)
                }
                .getOrDefault(Pair(null, false))
            if (res.second) return res
        }

        for (i in RevToParserTreeMap.values.reversed()) {
            val res = runCatching { i.parseTopic(htmlFileString, topicId, spaceType) }
                .onFailure {
                    LOGGER.error("Ex when using ${i.javaClass.simpleName} : ", it)
                    Pair(null, false)
                }
                .getOrDefault(Pair(null, false))
            if (res.second) return res
        }
        throw IllegalStateException("No parser support: $spaceType-$topicId")
    }

    val RevToParserTreeMap: TreeMap<Int, Parser> = run {
        val treeMap = TreeMap<Int, Parser>()
        treeMap[430] = CharacterTopicParserR430
        return@run treeMap
    }
}