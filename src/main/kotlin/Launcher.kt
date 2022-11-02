import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.util.XPathHelper
import org.seimicrawler.xpath.JXDocument
import org.seimicrawler.xpath.JXNode
import java.io.File
import java.io.IOException

class Launcher {

    companion object {
        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            var timing = System.currentTimeMillis()
//            val converter: FlexmarkHtmlConverter = FlexmarkHtmlConverter.builder().build()
            val htmlContent: String =
                FileUtil.getFileContent(File("E:\\SOURCE_ROOT\\bgm-archive-sh\\sample_html\\group_topic_sample.html"))!!

            val doc: JXDocument = JXDocument.create(htmlContent)
            val groupNameAnchor: JXNode = doc.selNOne(XPathHelper.XP_GROUP_NAME_ANCHOR)
            val topicTitle: JXNode = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TITLE_H1_TEXT)
            val topPostDivSmallText = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_SMALL_TEXT)
            val topPostUsernameAnchor = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_USERNAME_ANCHOR)
            val topPostUidSpan = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_UID_SPAN)
            val topPostUserNicknameAnchorText = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT)
            val topPostUserSignSpanText = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT)
            val topPostContentDiv = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_CONTENT_DIV)



            // group name: /group/{groupName}
            System.err.println(groupNameAnchor.asElement().attr("href"))
            // group display name: {groupDisplayName}
            System.err.println(groupNameAnchor.asElement().text())
            // title
            System.err.println(topicTitle.asString())
            // top post div small : #1 - {yyyy-MM-dd HH:mm}
            System.err.println(topPostDivSmallText.asString())
            // username: /user/{username}
            System.err.println(topPostUsernameAnchor.asElement().attr("href"))
            // user nickname: {nickname}
            System.err.println(topPostUserNicknameAnchorText.asString())
            // uid: background-image:url\\('//lain.bgm.tv/pic/user/l/\\d+/\\d+/\\d+/(\\d+)\\.jpg\\?r=\\d+'\\)
            System.err.println(topPostUidSpan.asElement().attr("style"))
            // user sign: ({sign})
            System.err.println(topPostUserSignSpanText.asString())
            // top post inner html: {}
            System.err.println(topPostContentDiv.asElement().html())



//            val outputFile = File("E:\\SOURCE_ROOT\\bgm-archive-sh\\sample_html\\group_topic_sample.md")
//            outputFile.createNewFile()
//            val fw = FileWriter(outputFile)
//            val bfw = BufferedWriter(fw)
//            val converted: String = converter.convert(htmlContent)
//            bfw.write(converted)
//            bfw.flush()
//            bfw.close()
//            fw.close()
            timing = System.currentTimeMillis() - timing
            System.err.println("TOTAL: " + timing + "ms.")
        }
    }
}