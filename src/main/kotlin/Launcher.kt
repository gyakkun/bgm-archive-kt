import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.util.misc.FileUtil
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

class Launcher {

    companion object {
        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            var timing = System.currentTimeMillis()
            val converter: FlexmarkHtmlConverter = FlexmarkHtmlConverter.builder().build()
            val htmlContent: String =
                FileUtil.getFileContent(File("E:\\SOURCE_ROOT\\bgm-archive-sh\\sample_html\\group_topic_sample.html"))!!
            val outputFile = File("E:\\SOURCE_ROOT\\bgm-archive-sh\\sample_html\\group_topic_sample.md")
            outputFile.createNewFile()
            val fw = FileWriter(outputFile)
            val bfw = BufferedWriter(fw)
            val converted: String = converter.convert(htmlContent)
            bfw.write(converted)
            bfw.flush()
            bfw.close()
            fw.close()
            timing = System.currentTimeMillis() - timing
            System.err.println("TOTAL: " + timing + "ms.")
        }
    }
}