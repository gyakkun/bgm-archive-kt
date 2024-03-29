package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.parser.subject.SubjectTopicParserR398
import moe.nyamori.bgm.parser.subject.SubjectTopicParserR400
import moe.nyamori.bgm.parser.subject.SubjectTopicParserR402
import moe.nyamori.bgm.parser.subject.SubjectTopicParserR412
import moe.nyamori.bgm.util.ParserHelper
import java.util.*

object SubjectTopicParser : Parser {
    override fun parseTopic(htmlFileString: String, topicId: Int, spaceType: SpaceType): Pair<Topic?, Boolean> {
        val rev = ParserHelper.getStyleRevNumberFromHtmlString(htmlFileString)
        val parser: Parser? = RevToParserTreeMap.floorEntry(rev)?.value
        if (parser != null) {
            val res = parser.parseTopic(htmlFileString, topicId, spaceType)
            if (res.second) return res
        }

        for (i in RevToParserTreeMap.values.reversed()) {
            val res = i.parseTopic(htmlFileString, topicId, spaceType)
            if (res.second) {
                return res
            }
        }
        throw IllegalStateException("No parser support: $spaceType-$topicId")
    }

    val RevToParserTreeMap: TreeMap<Int, Parser> = run {
        val treeMap = TreeMap<Int, Parser>()
        treeMap[398] = SubjectTopicParserR398
        treeMap[400] = SubjectTopicParserR400
        treeMap[402] = SubjectTopicParserR402
        treeMap[412] = SubjectTopicParserR412
        return@run treeMap
    }
}