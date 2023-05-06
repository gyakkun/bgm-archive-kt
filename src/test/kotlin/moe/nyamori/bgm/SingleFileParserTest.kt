  package moe.nyamori.bgm

import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.TopicParserEntrance
import java.io.File

object SingleFileParserTest {
    @JvmStatic
    fun main(argv: Array<String>) {

        val f = File("E:\\SOURCE_ROOT\\bgm-archive-230420\\subject\\02\\28\\22816.html")
        val topic = TopicParserEntrance.parseTopic(FileUtil.getFileContent(f)!!, 22816, SpaceType.SUBJECT)
        System.err.println(topic)
        System.err.println("end")
    }
}