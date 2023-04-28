package moe.nyamori.bgm

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.model.Space
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.util.SealedTypeAdapterFactory
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.*
import kotlin.streams.asStream

object BitSetTest {
    private val LOGGER = LoggerFactory.getLogger(BitSetTest::class.java)
    private val GSON = GsonBuilder()
        .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .registerTypeAdapterFactory(
            SealedTypeAdapterFactory.of(Space::class)
        ).create()
    const val HIDDEN_TOPIC_MASK_FILE_NAME = "hidden_topic_mask.txt"
    const val SPOT_CHECK_BITSET_FILE_NAME = "spot_check_bitset.txt"
    const val SPOT_CHECK_LIST_FILE_NAME = "sc.txt"
    const val MIN_SPOT_CHECK_SIZE = 7


    @JvmStatic
    fun main(argv: Array<String>) {
        LOGGER.info("max id for group ${getMaxId(SpaceType.GROUP)}")
        // generateTopicMaskFile(SpaceType.GROUP)
         System.err.println(randomSelectTopicIds(SpaceType.GROUP))
//        SpaceType.values().forEach {
//            genSpotCheckListFile(it)
//        }
    }

    fun genSpotCheckListFile(spaceType: SpaceType) {
        val scList = randomSelectTopicIds(spaceType)
        val scFile =
            File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_LIST_FILE_NAME")
        FileWriter(scFile).use { fw ->
            BufferedWriter(fw).use { bw ->
                scList.forEach {
                    bw.write(it.toString())
                    bw.newLine()
                }
                bw.flush()
            }
        }
    }

    fun randomSelectTopicIds(spaceType: SpaceType): List<Int> {
        val result = mutableListOf<Int>()
        val spotCheckedBs = getSpotCheckedTopicMask(spaceType)
        val hiddenBs = getHiddenTopicMask(spaceType)
        val fakeMaxId = hiddenBs.size() - 1

         // Test
         spotCheckedBs.set(0, fakeMaxId)
         spotCheckedBs.clear(fakeMaxId - 10, fakeMaxId)

        spotCheckedBs.or(hiddenBs)
        val remainZeroCount = spotCheckedBs.size() - spotCheckedBs.cardinality()
        // System.err.println(remainZeroCount)
        if (remainZeroCount <= Long.SIZE_BITS /*Bitset alloc unit*/) {
            var tmpId: Int = 0
            while (spotCheckedBs.nextClearBit(tmpId).also { tmpId = it } < fakeMaxId) {
                spotCheckedBs.set(tmpId)
                result.add(tmpId)
            }
            // Reset the bitset file
            writeBitsetToFile(
                BitSet(),
                File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_BITSET_FILE_NAME")
            )
            // Reset the mask file
            generateTopicMaskFile(spaceType)
            return result
        }
        val samplingSize = (remainZeroCount / (30 * 24 * 4)).coerceAtLeast(MIN_SPOT_CHECK_SIZE)
        val r = Random()

        repeat(samplingSize) {
            var victim: Int? = null
            while (victim == null) {
                val seed = (r.nextDouble() * spotCheckedBs.size()).toInt()
                if (seed >= fakeMaxId) continue
                victim = spotCheckedBs.nextClearBit(seed)
                spotCheckedBs.set(victim)
            }
            result.add(victim)
        }

        writeBitsetToFile(
            spotCheckedBs,
            File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_BITSET_FILE_NAME")
        )

        return result
    }

    fun getSpotCheckedTopicMask(spaceType: SpaceType): BitSet {
        val maskFile =
            File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_BITSET_FILE_NAME")
        if (!maskFile.exists()) return BitSet()
        return getBitsetFromLongPlaintextFile(maskFile)
    }

    fun getHiddenTopicMask(spaceType: SpaceType): BitSet {
        val maskFile =
            File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$HIDDEN_TOPIC_MASK_FILE_NAME")
        if (!maskFile.exists()) generateTopicMaskFile(spaceType)
        assert(maskFile.exists())
        return getBitsetFromLongPlaintextFile(maskFile)
    }

    fun getBitsetFromLongPlaintextFile(maskFile: File): BitSet {
        val maskStr = FileUtil.getFileContent(maskFile)
        val longList = mutableListOf<Long>()
        maskStr!!.lines().mapNotNull { it.toLongOrNull() }.forEach { longList.add(it) }
        val longArr = longList.toLongArray()
        return BitSet.valueOf(longArr)
    }

    fun generateTopicMaskFile(spaceType: SpaceType) {
        val maxId = getMaxId(spaceType)
        val bs = BitSet(maxId + 1)
        walkThroughJson { file ->
            if (file.isDirectory) return@walkThroughJson false
            if (!file.absolutePath.contains(spaceType.name.lowercase())) return@walkThroughJson false
            if (!file.extension.equals("json", ignoreCase = true)) return@walkThroughJson false
            if ((file.nameWithoutExtension.toIntOrNull() ?: -1) > maxId) return@walkThroughJson false
            if (file.nameWithoutExtension.hashCode() and 127 == 127) LOGGER.info("$file is processing")
            val fileStr = FileUtil.getFileContent(file)!!
            val topic = GSON.fromJson(fileStr, Topic::class.java)
            if (!topic.display) {
                bs.set(topic.id)
            }
            return@walkThroughJson true
        }
        val hiddenTopicMaskFile =
            File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$HIDDEN_TOPIC_MASK_FILE_NAME")
        writeBitsetToFile(bs, hiddenTopicMaskFile)
    }

    private fun writeBitsetToFile(bs: BitSet, file: File) {
        val longs = bs.toLongArray()
        FileWriter(file).use { fw ->
            BufferedWriter(fw).use { bw ->
                longs.forEach { l ->
                    bw.write(l.toString())
                    bw.newLine()
                }
                bw.flush()
            }
        }
    }

    fun getMaxId(spaceType: SpaceType): Int {
        val prevProcessed = GitHelper.getPrevProcessedArchiveCommitRef() // archive repo
        val topiclist = GitHelper.archiveRepoSingleton.getFileContentAsStringInACommit(
            prevProcessed,
            "${spaceType.name.lowercase()}/topiclist.txt"
        )
        return topiclist.lines().map { it.toIntOrNull() }.filterNotNull().max()
    }


    fun walkThroughJson(parallel: Boolean = false, handler: (File) -> Boolean) {
        val jsonRepoFolders = ArrayList<String>()
        Config.BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST.split(",")
            .map {
                jsonRepoFolders.add(it.trim())
            }
        Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR.let { jsonRepoFolders.add(it) }


        jsonRepoFolders.forEach outer@{
            val folder = File(it)
            val fileStream = folder.walkBottomUp().asStream().let {
                if (parallel) it.parallel()
                it
            }
            fileStream.forEach inner@{ file ->
                runCatching {
                    handler(file)
                }.onFailure {
                    LOGGER.error("Ex: ", it)
                }
            }
            LOGGER.info("Finished walking through json folders.")
        }
    }
}