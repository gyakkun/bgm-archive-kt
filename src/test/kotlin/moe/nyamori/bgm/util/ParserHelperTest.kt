package moe.nyamori.bgm.util

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.RepoType
import moe.nyamori.bgm.git.FileHistoryLookup.getRevCommitList
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import org.junit.jupiter.api.Assertions.assertEquals
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

    // @Test
    fun writeSampleHtml() {
        // remember to init config first
        val filePath = "blog/" + FilePathHelper.numberToPath(313741) + ".html"
        val archiveRepo = Config.repoList.first { it.type == RepoType.HTML && !it.repo.isBare }.repo
        val commitList = archiveRepo.getRevCommitList(filePath)
        for (i in commitList) {
            val htmlString = archiveRepo.getFileContentAsStringInACommit(i.hash, filePath)
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