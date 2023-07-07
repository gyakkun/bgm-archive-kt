package moe.nyamori.bgm.parser

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.Group
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.parser.group.*
import moe.nyamori.bgm.util.ParserHelper
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.streams.asStream

class GroupParserTest {

    @Test
    fun testR403toR412() {
        for (i in 403..412) {
            val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/group/$i.html") ?: continue
            ins.use {
                System.err.println("Parsing $i")
                val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
                val (topic, success) = GroupTopicParserR403.parseTopic(htmlString, 888, SpaceType.GROUP)
                assert(success)
                assert((topic!!.space as Group).meta!!.containsKey("data_likes_list"))
            }
        }
    }

    @Test
    fun testR402() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/group/402.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (_, success) = GroupTopicParserR402.parseTopic(htmlString, 888, SpaceType.GROUP)
            assert(success)
        }
    }

    @Test
    fun testR400() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/group/400.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (_, success) = GroupTopicParserR400.parseTopic(htmlString, 888, SpaceType.GROUP)
            assert(success)
        }
    }

    @Test
    fun testR398() {
        val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/group/398.html")
        if (ins == null) return
        ins.use {
            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val (_, success) = GroupTopicParserR398.parseTopic(htmlString, 888, SpaceType.GROUP)
            assert(success)
        }
    }

    // @Test
    fun compareR430ParserAndR403Parser() {
        val skip2 = setOf(342457, 352892, 371602, 372780, 374301, 374687, 381676, 381750)
        val skip = emptySet<Int>()
        val ng = mutableSetOf<Int>()
        File(Config.BGM_ARCHIVE_GIT_REPO_DIR).walkBottomUp().asStream().forEach {
//            for (i in ids) {
//            val ins = ParserHelper.javaClass.getResourceAsStream("/html_samples/group/r430/$i.html") ?: continue
            if (it.isDirectory) return@forEach
            if (it.extension != "html") return@forEach
            it.nameWithoutExtension.toIntOrNull() ?: return@forEach
            val topicId = it.nameWithoutExtension.toInt()
//            if (topicId <= skip.max()) return@forEach
            // if (topicId !in skip) return@forEach
            if (topicId % 100 == 0) System.err.println("Parsing $topicId")
//            ins.use {
//            val htmlString = String(it.readAllBytes(), Charsets.UTF_8)
            val htmlString = it.readText()
            val (r403result, success403) = runCatching {
                GroupTopicParserR403.parseTopic(
                    htmlString,
                    topicId,
                    SpaceType.GROUP
                )
            }.onFailure {
                System.err.println("R403 failed at $topicId!! $it")
            }
                .getOrDefault(null to false)
            val (r430result, success430) = runCatching {
                GroupTopicParserR430.parseTopic(
                    htmlString,
                    topicId,
                    SpaceType.GROUP
                )
            }
                .onFailure {
                    System.err.println("R430 failed at $topicId!! $it")
                    it.printStackTrace()
                }.getOrDefault(null to false)
            if (!success403) return@forEach
            // assert(success403)
            assert(success430)

            val json403 = GitHelper.GSON.toJson(r403result)
            val json430 = GitHelper.GSON.toJson(r430result)

            val j403l = json403.lines()
            val j430l = json430.lines()

            var failed = json403 != json430
            //for (j in j403l.indices) {
            //    if (j403l[j] != j430l[j]) {
            //        failed = true
            //        // System.err.println("//// v 403 v /////")
            //        // System.err.println(j403l[j])
            //        // System.err.println("//// ^ 403 ^ v 430 v  /////")
            //        // System.err.println(j430l[j])
            //        // System.err.println("//// ^ 430 ^ /////")
            //    }
            //}

            if (failed) {
                // System.err.println(json403)
                // System.err.println("///// ^403^ v430v //////")
                // System.err.println(json430)
                // throw IllegalStateException("Failed at topic $topicId")
                if (r403result!!.getAllPosts().size >= r430result!!.getAllPosts().size) {
                    ng.add(topicId)
                    System.err.println("vvvv Failed at $topicId vvvv")
                    for (j in j403l.indices) {
                        if (j403l[j] != j430l[j]) {
                            failed = true
                            System.err.println("//// v 403 v /////")
                            System.err.println(j403l[j])
                            System.err.println("//// ^ 403 ^ v 430 v  /////")
                            System.err.println(j430l[j])
                            System.err.println("//// ^ 430 ^ /////")
                        }
                    }
                    System.err.println("^^^^ Failed at $topicId ^^^^")
                } else {
                    failed = false
                }
            }
            // assert(GitHelper.GSON.toJson(r403result) == GitHelper.GSON.toJson(r430result))

        }
//            }
//        }
        System.err.println("NG: $ng")
    }

}