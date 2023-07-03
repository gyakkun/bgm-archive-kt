package moe.nyamori.bgm

import com.vladsch.flexmark.util.misc.FileUtil
import io.javalin.http.sse.NEW_LINE
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.SpotChecker
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.FilePathHelper
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Vector
import java.util.concurrent.CountDownLatch

object ConsecutiveDetect {
    private val LOGGER = LoggerFactory.getLogger(ConsecutiveDetect::class.java)

    enum class NGType {
        LOST, DISPLAY
    }


    @JvmStatic
    fun main1(args: Array<String>) {
        val folder = File("E:\\SOURCE_ROOT\\bgm-archive-ep-historical\\ep")
        for (i in 1..151999) {
            val file = folder.resolve("${FilePathHelper.numberToPath(i)}.html")
            if (!file.exists()) {
                System.err.println(file)
            } else {
                val str = FileUtil.getFileContent(file)
                if (!str!!.contains("2008-2023 Bangumi")) {
                    System.err.println("Error page: ${file}")
                }
            }

        }

    }


    @JvmStatic
    fun main(args: Array<String>) {
        val spaceType = SpaceType.EP
        val repoDir = File(Config.BGM_ARCHIVE_GIT_REPO_DIR)
        val ngOrLostList = Vector<Int>()
        // val total = SpotChecker.getMaxId(SpaceType.GROUP)
        val numThread = 6
        val latchArr = Array(numThread) { CountDownLatch(1) }
        val total = SpotChecker.getMaxIdByVisitingAllFiles(spaceType)
        val part = (total / numThread) as Int
        val ngType = NGType.LOST
        for (t in 1..numThread) {
            val start = t
            var i = start
            Thread {
                while (i <= total) {
                    try {
                        if (i and 1023 == 1023) LOGGER.info("Processing $i")
                        val topicFile =
                            repoDir.resolve("${spaceType.name.lowercase()}/${FilePathHelper.numberToPath(i)}.html")
                        if (!topicFile.exists()) {
                            if (ngType == NGType.LOST) {
                                ngOrLostList.add(i)
                            }
                            continue
                        }
                        // if (ngType == NGType.DISPLAY) {
                        //     val topicFileContentStr = FileUtil.getFileContent(topicFile)
                        //     val topicObj: Topic = GitHelper.GSON.fromJson(topicFileContentStr, Topic::class.java)
                        //     if (!topicObj.display) {
                        //         ngOrLostList.add(i)
                        //     }
                        // }
                    } catch (ex: Exception) {
                        System.err.println("Ex: $ex ${ex.message} ${ex.cause} ${ex.stackTrace}")
                    } finally {
                        i += numThread
                    }
                }
                latchArr[t - 1].countDown()
            }.start()
        }
        for (t in 0 until numThread) latchArr[t].await()
        ngOrLostList.sort()
        val rangeSummary = summaryRanges(ngOrLostList)
        val noGoodFilePath =
            Config.BGM_ARCHIVE_GIT_REPO_DIR + "/${spaceType.name.lowercase()}/${ngType.name.lowercase()}_ng.txt"
        val noGoodFile = File(noGoodFilePath)
        FileWriter(noGoodFile).use { fw ->
            BufferedWriter(fw).use { bfw ->
                for(i in ngOrLostList){
                    bfw.write(i.toString())
                    bfw.write(NEW_LINE)
                }

                // for (i in rangeSummary) {
                //     for (j in i) {
                //         bfw.write(j.toString())
                //         bfw.write(NEW_LINE)
                //     }
                // }
                bfw.flush()
            }
        }
    }

    fun summaryRanges(nums: List<Int>): List<IntArray> {
        val result: MutableList<IntArray> = ArrayList()
        if (nums.isEmpty()) return result
        var prev = nums[0]
        var startVal = nums[0]
        val n = nums.size
        for (i in 1 until n) {
            if (nums[i] == prev + 1) {
                prev = nums[i]
            } else {
                if (startVal != prev) {
                    result.add(intArrayOf(startVal, prev))
                } else {
                    result.add(intArrayOf(prev))
                }
                prev = nums[i]
                startVal = prev
            }
        }
        if (startVal != prev) {
            result.add(intArrayOf(startVal, prev))
        } else {
            result.add(intArrayOf(prev))
        }
        return result
    }
}
