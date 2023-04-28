package moe.nyamori.bgm

import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.Post
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.asStream

class Rel {
    companion object {
        val scoreMap = ConcurrentHashMap<String, MutableMap<String, Double>>()
        val userIdToUsernameMap = ConcurrentHashMap<Int, String>()
        val usernameToUserIdMap = ConcurrentHashMap<String, Int>()
        const val WEIGHTING_TO_POST = 0.6
        const val WEIGHTING_TO_FLOOR = 0.8
        const val WEIGHTING_SUB_FLOOR_TO_POST = 0.4
        const val WEIGHTING_SUB_FLOORS_IN_ONE_FLOOR = 0.6
        const val WEIGHTING_SUB_FLOORS_IN_OTHER_FLOOR_TO_THIS_FLOOR = 0.2
        const val WEIGHTING_FLOOR_TO_FLOOR = 0.2
        const val WEIGHTING_UNRELATED_SUB_FLOORS = 0.1
        var DATE_BOTTOM_LINE = (System.currentTimeMillis() - 2 * 365 * 86400 * 1000L) / 1000

        @JvmStatic
        fun main(args: Array<String>) {
            val groupJsonDir = File(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR).resolve(SpaceType.SUBJECT.name.lowercase())
            groupJsonDir.walk().asStream().parallel().forEach fileIteration@{ file ->
                if (file.extension.lowercase() != "json") return@fileIteration
                val fileContent = FileUtil.getFileContent(file)
                try {
                    val topic = GitHelper.GSON.fromJson(fileContent, Topic::class.java)
                    if (!topic.display) return@fileIteration

                    if (topic.dateline!! < DATE_BOTTOM_LINE) return@fileIteration

                    val postList = topic.postList!!
                    val flatPostList = ArrayList<Post>()
                    val topPost = postList!!.first()
                    val topPostUsername = topPost.user!!.username
                    val topPostUserId = topPost.user!!.id
                    updateUsernameIdMapping(topPostUsername, topPostUserId)

                    postList.forEach {
                        if (it.dateline < DATE_BOTTOM_LINE) return@forEach
                        flatPostList.add(it)
                        if (it.subFloorList?.isNotEmpty() == true) flatPostList.addAll(it.subFloorList!!)
                    }

                    val mainFloorList: List<Post> = flatPostList.filter { it.subFloorNum == null }
                    mainFloorList.forEach { outerPost ->
                        updateScore(outerPost.user!!.username, topPostUsername, WEIGHTING_TO_POST)
                        mainFloorList.forEach innerLoop@{ innerPost ->
                            if (innerPost == outerPost) return@innerLoop
                            updateScore(outerPost.user!!.username, innerPost.user!!.username, WEIGHTING_FLOOR_TO_FLOOR)
                        }
                    }

                    flatPostList.filter { it.subFloorNum != null }.forEach {
                        updateScore(it.user!!.username, topPostUsername, WEIGHTING_SUB_FLOOR_TO_POST)
                    }
                    flatPostList.filter { it.subFloorList?.isNotEmpty() == true }.forEach { floor ->
                        val thisFloorUsername = floor.user!!.username
                        floor.subFloorList!!.forEach { subFloor ->
                            val subFloorUsername = subFloor.user!!.username
                            updateScore(thisFloorUsername, subFloorUsername, WEIGHTING_TO_FLOOR)
                            // floor.subFloorList!!.forEach inner@{ subsub ->
                            //     if (subsub == subFloor) return@inner
                            //     val subsubUsername = subsub.user!!.username
                            //     updateScore(subsubUsername, subFloorUsername, WEIGHTING_SUB_FLOORS_IN_ONE_FLOOR)
                            // }
                        }
                    }
                    val subFloorGroupByFloor: Map<Int, List<Post>> =
                        flatPostList.filter { it.subFloorNum != null }.groupBy { it.floorNum }
                    subFloorGroupByFloor.forEach { p ->
                        val subFloorList = p.value
                        subFloorList.forEach { i ->
                            subFloorList.forEach { j ->
                                if (i == j) return@forEach
                                updateScore(i.user!!.username, j.user!!.username, WEIGHTING_SUB_FLOORS_IN_ONE_FLOOR)
                            }
                            mainFloorList.forEach { j ->
                                if (j.floorNum == i.floorNum) return@forEach
                                updateScore(
                                    i.user!!.username, j.user!!.username,
                                    WEIGHTING_SUB_FLOORS_IN_OTHER_FLOOR_TO_THIS_FLOOR
                                )
                            }
                        }
                        subFloorGroupByFloor.forEach { q ->
                            val otherSubFloorList = q.value
                            if (otherSubFloorList == subFloorList) return@forEach
                            subFloorList.forEach { x ->
                                otherSubFloorList.forEach { y ->
                                    updateScore(x.user!!.username, y.user!!.username, WEIGHTING_UNRELATED_SUB_FLOORS)
                                }
                            }
                        }
                    }

                } catch (ignore: Exception) {

                }
            }
            val ppl = ArrayList<Pair<Pair<String, String>, Double>>()
            scoreMap.forEach { e ->
                val a = e.key
                e.value.forEach {
                    ppl.add(Pair(Pair(a, it.key), it.value))
                }
            }
            ppl.sortBy { -it.second }
            println(ppl.size)
            val output = File(System.getProperty("user.home")).resolve("bgm-archive-subject-rel-2yrs.csv")
            FileWriter(output).use { fw ->
                BufferedWriter(fw).use { bfw ->
                    ppl.forEach {
                        bfw.write("${it.first.first},${it.first.second},${it.second}")
                        bfw.newLine()
                    }
                    bfw.flush()
                }
            }
        }

        private inline fun updateScore(a: String, b: String, w: Double) {
            if (a == b) return
            val s = if (a > b) a else b
            val b = if (s == a) b else a
            scoreMap.putIfAbsent(s, ConcurrentHashMap())
            scoreMap.get(s)!!.put(b, scoreMap.get(s)!!.getOrDefault(b, 0.0) + w)
        }

        private inline fun updateUsernameIdMapping(topPostUsername: String, topPostUserId: Int?) {

        }
    }
}