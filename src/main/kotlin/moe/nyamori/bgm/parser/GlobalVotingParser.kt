package moe.nyamori.bgm.parser

import org.seimicrawler.xpath.JXDocument
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object GlobalVotingParser {
    private val LOGGER: Logger = LoggerFactory.getLogger(GlobalVotingParser.javaClass)
    const val HORIZONTAL_CHART_XPATH = "//div[@id=\"ChartWarpper\"]/ul[@class=\"horizontalChart\"]"
    const val TITLE_XPATH = "//div[@id=\"headerSubject\"]/h1/a"
    fun parseSubject(htmlFileString: String, subjectId: Int): VotingResult {
        try {
            val doc: JXDocument = JXDocument.create(htmlFileString)
            val bodyNode = doc.selNOne("body")
            val horizontalChartUl = bodyNode.selOne(HORIZONTAL_CHART_XPATH)
            val titleNode = bodyNode.selOne(TITLE_XPATH)

            val titleOrig = titleNode.asElement().text()
            val titleChn = titleNode.asElement().attr("title")
            LOGGER.info("Orig title: {}, chn title: {}", titleOrig, titleChn)

            val liList = horizontalChartUl.sel("/li")
            val result = IntArray(11)
            for (i in liList) {
                val spanList = i.sel("/a/span")
                var grading = -1
                for (j in spanList.indices) {
                    if (j == 0) {
                        grading = Integer.parseInt(spanList[j].asElement().html())
                    } else {
                        val parenthesed = spanList[j].asElement().html()
                        val count = parenthesed.substring(1, parenthesed.length - 1)
                        result[grading] = Integer.parseInt(count)
                    }
                }
            }
            return VotingResult(subjectId, titleOrig, titleChn, result)
        } catch (ex: Exception) {
            LOGGER.error("Ex: ", ex)
        }
        return VotingResult(-1, "invalid", "invalid", IntArray(0))
    }
}

data class VotingResult(val subjectId: Int, val titleOrig: String, val titleChn: String, val voting: IntArray)