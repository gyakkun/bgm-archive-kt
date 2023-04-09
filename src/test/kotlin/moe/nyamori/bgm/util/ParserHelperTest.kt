package moe.nyamori.bgm.util

import moe.nyamori.bgm.git.FileHistoryLookup
import moe.nyamori.bgm.git.GitHelper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileWriter
import kotlin.text.Charsets.UTF_8

class ParserHelperTest {
    @Test
    fun getStyleRevNumberFromHtmlString() {
        for (i in 398..412) {
            val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/group/$i.html") ?: continue
            ins.use {
                val htmlString = String(it.readAllBytes(), UTF_8)
                assertEquals(i, ParserHelper.getStyleRevNumberFromHtmlString(htmlString))
            }
        }
    }

     @Test
    fun writeSampleHtml() {
        val filePath = "blog/" + FilePathHelper.numberToPath(317322) + ".html"
        val commitList = FileHistoryLookup.getRevCommitList(filePath, GitHelper.getArchiveRepo())
        for (i in commitList) {
            val htmlString = GitHelper.getFileContentInACommit(GitHelper.getArchiveRepo(), i, filePath)
            val revId = ParserHelper.getStyleRevNumberFromHtmlString(htmlString)
            val htmlFile =
                File("E:\\SOURCE_ROOT\\bgm-archive-kt\\src\\test\\resources\\html_samples\\blog\\$revId.html")
            FileWriter(htmlFile).use {
                it.write(htmlString)
                it.flush()
            }
        }
    }


}