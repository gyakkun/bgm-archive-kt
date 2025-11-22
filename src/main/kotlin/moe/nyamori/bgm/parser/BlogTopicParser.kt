package moe.nyamori.bgm.parser

import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.parser.blog.BlogTopicParserR398
import moe.nyamori.bgm.parser.blog.BlogTopicParserR400
import moe.nyamori.bgm.parser.blog.BlogTopicParserR547
import moe.nyamori.bgm.parser.blog.BlogTopicParserR649
import moe.nyamori.bgm.parser.blog.BlogTopicParserR698
import moe.nyamori.bgm.util.ParserHelper
import java.util.*

object BlogTopicParser : Parser {
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
        treeMap[398] = BlogTopicParserR398
        treeMap[400] = BlogTopicParserR400
        treeMap[547] = BlogTopicParserR547
        treeMap[649] = BlogTopicParserR649
        treeMap[698] = BlogTopicParserR698
        return@run treeMap
    }
}