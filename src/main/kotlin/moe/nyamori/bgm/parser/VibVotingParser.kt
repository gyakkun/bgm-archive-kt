package moe.nyamori.bgm.parser

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import moe.nyamori.bgm.git.GitHelper
import org.seimicrawler.xpath.JXDocument
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException

object VibVotingParser {
    private val LOGGER: Logger = LoggerFactory.getLogger(VibVotingParser.javaClass)

    private const val CHART_SETS = "CHART_SETS"
    fun parseSubject(htmlFileString: String, subjectId: Int): VotingResult {
        try {
            val doc: JXDocument = JXDocument.create(htmlFileString)
            val bodyNode = doc.selNOne("body")
            val titleNode = bodyNode.selOne(GlobalVotingParser.TITLE_XPATH)
            val titleOrig = titleNode.asElement().text()
            val titleChn = titleNode.asElement().attr("title")

            val lines = htmlFileString.lines()
            var found = false
            for (line in lines) {
                if (!line.contains(CHART_SETS)) continue
                found = true

                val objStr = line.substring(line.indexOf("{"), line.lastIndexOf("}") + 1)
                val obj = GitHelper.GSON.fromJson(objStr, JsonObject::class.java)

                if (obj["vib"] == null) throw IllegalStateException("Should found vib object but got nothing!")
                val result = IntArray(11)
                obj["vib"].asJsonObject["data"].asJsonArray.forEach {
                    val point = it.asJsonObject["title"].asInt
                    val people = if (it.asJsonObject.has("vib")) it.asJsonObject["vib"].asInt else 0
                    result[point] = people
                }
                return VotingResult(subjectId, titleOrig, titleChn, result)
            }
            if (!found) throw IllegalStateException("Should found char set but got nothing!")
        } catch (ex: Exception) {
            LOGGER.error("Ex: ", ex)
        }
        return VotingResult(-1, "invalid", "invalid", IntArray(0))
    }

}