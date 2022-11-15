package moe.nyamori.bgm

import com.google.gson.GsonBuilder
import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.TopicParser
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong
import kotlin.streams.asStream

class Launcher {
    companion object {
        val LOGGER = LoggerFactory.getLogger(Launcher.javaClass)
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
//            val converter: FlexmarkHtmlConverter = FlexmarkHtmlConverter.builder().build()
            val archiveFolder = File(Config.BGM_ARCHIVE_GIT_REPO_DIR)
//            val sampleFile1 = File("E:\\SOURCE_ROOT\\bgm-archive-sh\\sample_html\\group_topic_sample.html")
//            val sampleFile2 = File("E:\\SOURCE_ROOT\\bgm-archive-sh\\sample_html\\group_topic_sample_2.html")
//            val sampleFile2 = File("C:\\Users\\Steve\\source\\bgm-archive\\group\\36\\27\\362716.html")
//            val sampleFile2 = File("C:\\Users\\Steve\\source\\bgm-archive-historical\\group\\00\\22\\2226.html")
            val sampleFile2 = File("E:\\[ToBak]\\Desktop_Win10\\23629.html")
            val str = FileUtil.getFileContent(sampleFile2)!!
            val (parseGroupTopic, result) = TopicParser.parseTopic(str, 23629,SpaceType.SUBJECT)
            if (result) {
                val toJson = gson.toJson(parseGroupTopic)
                val jsonFile = File(
                    sampleFile2.absolutePath.replace("bgm-archive", "bgm-archive-json")
                        .replace(".html", ".json")
                )
                jsonFile.parentFile.mkdirs()
                val fileWriter =
                    FileWriter(
                        jsonFile
                    )
                fileWriter.write(toJson)
                fileWriter.flush()
                fileWriter.close()
                System.err.println(parseGroupTopic)
            }
//
            if (true) return

            val ts = ConcurrentSkipListSet<Pair<Long, File>>(Comparator.comparingLong { -it.first })
            val ng = ConcurrentHashMap.newKeySet<File>()
            val ngCounter = AtomicLong()
            val totalCounter = AtomicLong()
            val _404Counter = AtomicLong()
            archiveFolder.walk().asStream().parallel().forEach allMatch@{ htmlFile ->
                if (htmlFile.isDirectory) return@allMatch
                if (htmlFile.extension != "html") return@allMatch
                totalCounter.incrementAndGet()
                var timing = System.currentTimeMillis()
                val fileName = htmlFile.nameWithoutExtension
                val fileStr = FileUtil.getFileContent(htmlFile)!!
                val topicId = fileName.toInt()
                val (topic, result) = TopicParser.parseTopic(fileStr, topicId,SpaceType.GROUP)
                if (!result) {
                    ng.add(htmlFile)
                    ngCounter.incrementAndGet()
//                    LOGGER.error("NG: ${htmlFile.absolutePath}")
                    return@allMatch
                } else {
                    if (topic == null) {
                        _404Counter.incrementAndGet()
                        return@allMatch
                    }
                    timing = System.currentTimeMillis() - timing
                    // LOGGER.info("$timing ms : ${htmlFile.absolutePath}")
                    ts.add(Pair(timing, htmlFile))
                    if (!topic!!.display!!) return@allMatch
//                    val toJson = gson.toJson(topic)
//                    val jsonFile = File(
//                        htmlFile.absolutePath.replace("bgm-archive", "bgm-archive-json")
//                            .replace(".html", ".json")
//                    )
//                    jsonFile.parentFile.mkdirs()
//                    val fileWriter =
//                        FileWriter(
//                            jsonFile
//                        )
//                    fileWriter.write(toJson)
//                    fileWriter.flush()
//                    fileWriter.close()
                    return@allMatch
                }
            }

            LOGGER.info("NG COUNT: ${ngCounter.get()}")
            LOGGER.info("404 COUNT: ${_404Counter.get()}")
            LOGGER.info("TOT COUNT: ${totalCounter.get()}")
            LOGGER.info("Top 10 time consumed: ${ts.toList().subList(0, 10)}")
            LOGGER.info("NG: ${ng.map { i -> i.nameWithoutExtension }.sorted().toList()}")
            /*
            val htmlContent: String =
                FileUtil.getFileContent(sampleFile2)!!
            val doc: JXDocument = JXDocument.create(htmlContent)

            val groupNameAnchor: JXNode = doc.selNOne(XPathHelper.XP_GROUP_NAME_ANCHOR)
            val topicTitle: JXNode = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TITLE_H1_TEXT)

            val topPostDiv = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_DIV)
            val topPostDivSmallText = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_SMALL_TEXT)
            val topPostUsernameAnchor = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_USERNAME_ANCHOR)
            val topPostUidSpan = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_UID_SPAN)
            val topPostUserNicknameAnchorText =
                doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT)
            val topPostUserSignSpanText = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT)
            val topPostContentDiv = doc.selNOne(XPathHelper.XP_GROUP_TOPIC_TOP_POST_CONTENT_DIV)

            val followPostDivList = doc.selN(XPathHelper.XP_GROUP_TOPIC_FOLLOW_POST_DIV_LIST)


            // group name: /group/{groupName}
            System.err.println(groupNameAnchor.asElement().attr("href"))
            // group display name: {groupDisplayName}
            System.err.println(groupNameAnchor.asElement().text())
            // title
            System.err.println(topicTitle.asString())
            // top post div small : #1 - {yyyy-M-d HH:mm}
            System.err.println(topPostDivSmallText.asString())
            // username: /user/{username}
            System.err.println(topPostUsernameAnchor.asElement().attr("href"))
            // user nickname: {nickname}
            System.err.println(topPostUserNicknameAnchorText.asString())
            // uid: background-image:url\\('//lain.bgm.tv/pic/user/l/\\d+/\\d+/\\d+/(\\d+)\\.jpg\\?r=\\d+'\\)
            // FIXME: Special handling for background-image:url('//lain.bgm.tv/pic/user/l/icon.jpg')
            System.err.println(topPostUidSpan.asElement().attr("style"))
            // user sign: ({sign})
            System.err.println(topPostUserSignSpanText.asString())
            // top post id: post_{id}
            System.err.println(topPostDiv.asElement().attr("id"))
            // top post inner html: {}
            System.err.println(topPostContentDiv.asElement().html())

            // subReply('group'|'subject'|..., mid,floorPid,subFloorPid,floorUid,subFloorUid,isReplySubFloor(sub-1, floor-0)

            System.err.println("###############################################################")

            // follow post div list:
            // System.err.println(followPostDivList[0])
            followPostDivList.forEachIndexed outer@{ outerIdx, floor ->
                // Workaround for bug
                if (outerIdx != floor.asElement().elementSiblingIndex()) return@outer
                // follow post id: post_{id}
                System.err.println(floor.asElement().attr("id"))
                // follow post floor: #{floor}
                System.err.println(
                    floor.selOne("div[@class=\"re_info\"]/small/a[@class=\"floor-anchor\"]").asElement().text()
                )
                // follow post date: ' - {yyyy-M-d HH:mm}'
                System.err.println(floor.selOne("div[@class=\"re_info\"]/small/text()").asString())
                // follow post user anchor - username: /user/{username}
                System.err.println(floor.selOne("a").asElement().attr("href"))
                // follow post user anchor span - uid (plan B): background...
                // FIXME: Special handling for background-image:url('//lain.bgm.tv/pic/user/l/icon.jpg')
                System.err.println(floor.selOne("a/span").asElement().attr("style"))
                // follow post user nickname: {nickname}
                System.err.println(floor.selOne("div[2]/span[@class=\"userInfo\"]/strong/a/text()").asString())
                // follow post user sign: ({sign})
                System.err.println(floor.selOne("div[2]/span[@class=\"userInfo\"]/span/text()")?.asString())

                // follow post content div
                System.err.println(floor.selOne("div/div/div[@class=\"message\"]").asElement().html())

                // sub floor
                val subFloorList: MutableList<JXNode> = floor.sel("div/div/div[@class=\"topic_sub_reply\"]/div")
                subFloorList.forEachIndexed inner@{ innerIdx, subFloor ->
                    if (innerIdx != subFloor.asElement().elementSiblingIndex()) return@inner
                    // sub floor pid: post_{pid}
                    System.err.println(subFloor.asElement().attr("id"))
                    // sub floor floor number: #{floor}-#{subFloor}
                    System.err.println(
                        subFloor.selOne("div[@class=\"re_info\"]/small/a[@class=\"floor-anchor\"]").asElement().text()
                    )
                    // follow post date: ' - {yyyy-M-d HH:mm}'
                    System.err.println(subFloor.selOne("div[@class=\"re_info\"]/small/text()").asString())
                    // follow post user anchor - username: /user/{username}
                    System.err.println(subFloor.selOne("a").asElement().attr("href"))
                    // follow post user anchor span - uid (plan B): background...
                    // FIXME: Special handling for background-image:url('//lain.bgm.tv/pic/user/l/icon.jpg')
                    System.err.println(subFloor.selOne("a/span").asElement().attr("style"))
                    // follow post user nickname: {nickname}
                    System.err.println(subFloor.selOne("div[2]/strong[@class=\"userName\"]/a/text()").asString())

                    // follow post content div
                    System.err.println(subFloor.selOne("div[2]/div[@class=\"cmt_sub_content\"]").asElement().html())
                }

            }


//            val outputFile = File("E:\\SOURCE_ROOT\\bgm-archive-sh\\sample_html\\group_topic_sample.md")
//            outputFile.createNewFile()
//            val fw = FileWriter(outputFile)
//            val bfw = BufferedWriter(fw)
//            val converted: String = converter.convert(htmlContent)
//            bfw.write(converted)
//            bfw.flush()
//            bfw.close()
//            fw.close()
             */

        }
    }
}