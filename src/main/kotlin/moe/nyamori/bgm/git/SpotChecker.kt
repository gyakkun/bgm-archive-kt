package moe.nyamori.bgm.git

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.config.Config
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

object SpotChecker {
    private val LOGGER = LoggerFactory.getLogger(SpotChecker::class.java)
    private val GSON = GsonBuilder()
        .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .registerTypeAdapterFactory(
            SealedTypeAdapterFactory.of(Space::class)
        ).create()
    const val HIDDEN_TOPIC_MASK_FILE_NAME = "hidden_topic_mask.txt"
    const val SPOT_CHECK_BITSET_FILE_NAME = "spot_check_bitset.txt"
    const val SPOT_CHECK_LIST_FILE_NAME = "sc.txt"
    const val MIN_SPOT_CHECK_SIZE = 10


    @JvmStatic
    fun main(argv: Array<String>) {
        LOGGER.info("max id for group ${getMaxId(SpaceType.GROUP)}")
        // generateTopicMaskFile(SpaceType.GROUP)
//         System.err.println(randomSelectTopicIds(SpaceType.GROUP))
        SpaceType.values().forEach {
            genHiddenTopicMaskFile(it)
        }
    }

    fun genSpotCheckListFile(spaceType: SpaceType) {
        LOGGER.info("Generating spot check list file $SPOT_CHECK_BITSET_FILE_NAME for $spaceType.")
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

    private fun randomSelectTopicIds(spaceType: SpaceType): List<Int> {
        val result = mutableListOf<Int>()
        val spotCheckedBs = getSpotCheckedTopicMask(spaceType)
        val hiddenBs = getHiddenTopicMask(spaceType)

        spotCheckedBs.or(hiddenBs)
        val remainZeroCount = spotCheckedBs.size() - spotCheckedBs.cardinality()
        val fakeTotalCount = hiddenBs.size() - hiddenBs.cardinality()
        LOGGER.info(
            "Current approximate not spot-checked count: $spaceType - $remainZeroCount / $fakeTotalCount ," +
                    " spot-checked ratio: ${
                        String.format(
                            "%.2f",
                            (fakeTotalCount - remainZeroCount).toDouble() / fakeTotalCount.toDouble() * 100.0
                        )
                    }%"
        )
        // System.err.println(remainZeroCount)
        if (remainZeroCount <= Long.SIZE_BITS /*Bitset alloc unit*/) {
            LOGGER.warn("$spaceType remain zero count less than bitset alloc unit 64. Wrap them all to sc.txt")
            var tmpId = 0
            while (spotCheckedBs.nextClearBit(tmpId).also { tmpId = it } < spotCheckedBs.size()) {
                spotCheckedBs.set(tmpId)
                result.add(tmpId)
            }
            LOGGER.info("The last batch to spot check for $spaceType: $result")
            // Reset the bitset file
            LOGGER.info("Writing empty spot check bitset file for $spaceType.")
            writeBitsetToFile(
                BitSet(),
                File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_BITSET_FILE_NAME")
            )
            // Reset the mask file
            LOGGER.info("Going to re generate hidden topic mask file for $spaceType.")
            genHiddenTopicMaskFile(spaceType)
            return result
        }
        val samplingSize = (fakeTotalCount / (30 * 24 * 4)).coerceAtLeast(MIN_SPOT_CHECK_SIZE)
        val r = Random()

        repeat(samplingSize) {
            var victim: Int
            do {
                victim = spotCheckedBs.nextClearBit(r.nextInt(0, spotCheckedBs.size()))
            } while (victim >= spotCheckedBs.size())
            spotCheckedBs.set(victim)
            result.add(victim)
        }

        writeBitsetToFile(
            spotCheckedBs,
            File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_BITSET_FILE_NAME")
        )

        LOGGER.info("Spot check id selected: $spaceType - $result")
        return result.sorted()
    }

    private fun getSpotCheckedTopicMask(spaceType: SpaceType): BitSet {
        val maskFile =
            File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_BITSET_FILE_NAME")
        if (!maskFile.exists()) {
            LOGGER.warn("Spot checked mask bitset file not found for $spaceType. Creating one.")
            return BitSet()
        }
        return getBitsetFromLongPlaintextFile(maskFile)
    }

    private fun getHiddenTopicMask(spaceType: SpaceType): BitSet {
        val maskFile =
            File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$HIDDEN_TOPIC_MASK_FILE_NAME")
        if (!maskFile.exists()) {
            LOGGER.warn("Hidden topic mask file not found for $spaceType. Creating one.")
            genHiddenTopicMaskFile(spaceType)
        }
        assert(maskFile.exists())
        return getBitsetFromLongPlaintextFile(maskFile)
    }

    private fun getBitsetFromLongPlaintextFile(maskFile: File): BitSet {
        val maskStr = FileUtil.getFileContent(maskFile)
        val longList = mutableListOf<Long>()
        maskStr!!.lines().mapNotNull { it.toLongOrNull() }.forEach { longList.add(it) }
        val longArr = longList.toLongArray()
        return BitSet.valueOf(longArr)
    }

    private fun genHiddenTopicMaskFile(spaceType: SpaceType) {
        LOGGER.info("Generating hidden topic mask file for $spaceType.")
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
            if (topic.isEmptyTopic()) {
                bs.set(topic.id)
            }
            return@walkThroughJson true
        }
        LOGGER.info("New bitset size: ${bs.size()}. Cardinality: ${bs.cardinality()}. Zero count: ${bs.size() - bs.cardinality()}")
        val hiddenTopicMaskFile =
            File(Config.BGM_ARCHIVE_GIT_REPO_DIR).resolve("${spaceType.name.lowercase()}/$HIDDEN_TOPIC_MASK_FILE_NAME")
        if (hiddenTopicMaskFile.exists()) {
            LOGGER.warn("Old hidden topic mask file exists. Perform OR bitwise operation to gen a new one.")
            val oldBs = getBitsetFromLongPlaintextFile(hiddenTopicMaskFile)
            LOGGER.info("Old bitset size: ${oldBs.size()}. Cardinality: ${oldBs.cardinality()}. Zero count: ${oldBs.size() - bs.cardinality()}")
            bs.or(oldBs)
            LOGGER.info("After OR op, new bitset size: ${bs.size()}. Cardinality: ${bs.cardinality()}. Zero count: ${bs.size() - bs.cardinality()}")
        }
        writeBitsetToFile(bs, hiddenTopicMaskFile)
    }

    private fun writeBitsetToFile(bs: BitSet, file: File) {
        LOGGER.info("Writing bitset to file: ${file.absolutePath}")
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
        val result = topiclist.lines().mapNotNull { it.toIntOrNull() }.max()
        LOGGER.info("Max id for $spaceType: $result")
        return result
    }


    private fun walkThroughJson(parallel: Boolean = false, handler: (File) -> Boolean) {
        LOGGER.info("Walking through json repo folders.")
        val jsonRepoFolders = ArrayList<String>()
        Config.BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST.split(",")
            .map {
                jsonRepoFolders.add(it.trim())
            }
        Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR.let { jsonRepoFolders.add(it) }
        LOGGER.info("Json repo folders: $jsonRepoFolders")

        jsonRepoFolders.forEach outer@{
            val folder = File(it)
            val fileStream = folder.walkBottomUp().asStream().let { stream ->
                if (parallel) stream.parallel()
                stream
            }
            fileStream.forEach inner@{ file ->
                runCatching {
                    handler(file)
                }.onFailure { th ->
                    LOGGER.error("Ex: ", th)
                }
            }
            LOGGER.info("Finished walking through json folders.")
        }
    }
}