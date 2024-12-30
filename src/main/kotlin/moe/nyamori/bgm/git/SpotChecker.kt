package moe.nyamori.bgm.git

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.RepoDto
import moe.nyamori.bgm.config.getCouplingArchiveRepo
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.GitHelper.allJsonRepoListSingleton
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.git.GitHelper.getLastCommitSha1StrExtGit
import moe.nyamori.bgm.git.GitHelper.getRevCommitById
import moe.nyamori.bgm.git.GitHelper.simpleName
import moe.nyamori.bgm.model.Space
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.util.RangeHelper
import moe.nyamori.bgm.util.SealedTypeAdapterFactory
import moe.nyamori.bgm.util.TopicListHelper.getTopicList
import org.slf4j.LoggerFactory
import java.io.*
import java.time.Duration
import java.util.*
import java.util.function.Supplier
import kotlin.streams.toList
import kotlin.text.Charsets.UTF_8


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
    const val MAX_SPOT_CHECK_SIZE = 80
    const val RANGE_HOLE_INTERVAL_THRESHOLD = 35
    const val RANGE_HOLE_DETECT_DATE_BACK_LIMIT = 100
    const val RANGE_HOLE_DETECT_TAKE_LIMIT = 10
    val HOLE_CHECK_SKIP_TYPE = listOf(SpaceType.EP, SpaceType.CHARACTER, SpaceType.PERSON)
    private val HOLE_CHECKED_TYPE_SET = mutableSetOf<SpaceType>()
    val HOLE_CHECKED_SET_SIZE_LIMIT: Int
        get() {
            return HOLE_CHECKED_TYPE_SET.size.coerceAtLeast(1) * 25
        }

    val TYPE_MAX_ID_MAP = run {
        val result = mutableMapOf<SpaceType, Int>()
        SpaceType.entries.forEach { result[it] = -1 }
        result
    }


    fun genSpotCheckListFile(archiveRepo: RepoDto, spaceType: SpaceType) {
        if (Config.BGM_ARCHIVE_DISABLE_SPOT_CHECK) return
        LOGGER.info("Generating spot check list file $SPOT_CHECK_BITSET_FILE_NAME for $spaceType.")
        val scList = randomSelectTopicIds(archiveRepo, spaceType)
        val scFile =
            File(archiveRepo.repo.absolutePathWithoutDotGit()).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_LIST_FILE_NAME")
        if (!scFile.exists()) {
            LOGGER.error("Spot check list file for $spaceType not found! Creating one.")
            scFile.createNewFile()
        }
        val holesInTopicListRange = checkIfHolesInTopicListRange(spaceType, getTopicList(spaceType))

        FileWriter(scFile).use { fw ->
            BufferedWriter(fw).use { bw ->
                scList.forEach {
                    bw.write(it.toString())
                    bw.write("\n")
                }
                holesInTopicListRange.forEach {
                    bw.write(it.toString())
                    bw.write("\n")
                }
                bw.flush()
            }
        }
    }


    private val HOLE_CHECKED_SET = HashSet<Pair<SpaceType, Int>>() // Maintain in memory
    fun checkIfHolesInTopicListRange(spaceType: SpaceType, topicList: List<Int>): List<Int> {
        if (spaceType in HOLE_CHECK_SKIP_TYPE) return emptyList()
        HOLE_CHECKED_TYPE_SET.add(spaceType)
        if (HOLE_CHECKED_SET.size >= HOLE_CHECKED_SET_SIZE_LIMIT) {
            LOGGER.info("HOLE_CHECKED_SET size ${HOLE_CHECKED_SET.size} is larger than limit ${HOLE_CHECKED_SET_SIZE_LIMIT}. Clearing.")
            HOLE_CHECKED_SET.clear()
        }
        val holes = mutableListOf<Int>()
        val maxId = topicList.max()
        val fakeTopicList = mutableListOf<Int>().apply {
            val checkSize = RANGE_HOLE_DETECT_DATE_BACK_LIMIT.coerceAtMost(topicList.size * 6 / 7)
            val lowerBound = maxId - checkSize + 1
            (lowerBound..maxId).forEach { add(it) }
        }
        fakeTopicList.removeAll(topicList)
        val rangeSummary = RangeHelper.summaryRanges(fakeTopicList)
        rangeSummary.forEach {
            if (it.size == 1) holes.add(it[0])
            else if (it.size == 2) {
                val length = it[1] - it[0] + 1
                if (length > RANGE_HOLE_INTERVAL_THRESHOLD) return@forEach
                for (i in it[0]..it[1]) {
                    holes.add(i)
                }
            }
        }
        val result = holes
            .filter {
                // If already spot checked then skip it
                !HOLE_CHECKED_SET.contains(Pair(spaceType, it))
            }
            .takeLast((topicList.size / 8).coerceAtMost(RANGE_HOLE_DETECT_TAKE_LIMIT))
        if (result.isNotEmpty()) {
            LOGGER.warn("Holes for type $spaceType detected during spot check: $result")
        }
        HOLE_CHECKED_SET.addAll(result.map { Pair(spaceType, it) })
        LOGGER.info("HOLE_CHECKED_SET size / limit : ${HOLE_CHECKED_SET.size} / $HOLE_CHECKED_SET_SIZE_LIMIT")
        return result
    }

    private fun randomSelectTopicIds(archiveRepo: RepoDto, spaceType: SpaceType): List<Int> {
        val result = mutableListOf<Int>()
        val spotCheckedBs = getSpotCheckedTopicMask(archiveRepo, spaceType)
        val hiddenBs = getHiddenTopicMask(archiveRepo, spaceType)
        val maxId = getMaxTopicId(spaceType)

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
                if (tmpId > maxId) break
                spotCheckedBs.set(tmpId)
                result.add(tmpId)
            }
            LOGGER.info("The last batch to spot check for $spaceType: $result")
            // Reset the bitset file
            LOGGER.info("Writing empty spot check bitset file for $spaceType.")
            writeBitsetToFile(
                BitSet(),
                File(archiveRepo.repo.absolutePathWithoutDotGit()).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_BITSET_FILE_NAME")
            )
            // Reset the mask file
            LOGGER.info("Going to re generate hidden topic mask file for $spaceType.")
            genHiddenTopicMaskFile(archiveRepo, spaceType)
            return result
        }
        val samplingSize =
            (fakeTotalCount / (30 * (archiveRepo.expectedCommitPerDay / (2 * (SpaceType.entries.size)))))
                .coerceAtLeast(MIN_SPOT_CHECK_SIZE).coerceAtMost(MAX_SPOT_CHECK_SIZE)
        val r = Random()

        repeat(samplingSize.coerceAtMost(remainZeroCount)) {
            var victim: Int
            do {
                victim = spotCheckedBs.nextClearBit(r.nextInt(0, spotCheckedBs.size().coerceAtMost(maxId)))
            } while (victim >= spotCheckedBs.size() || victim > maxId)
            spotCheckedBs.set(victim)
            result.add(victim)
        }
        result.sort()

        writeBitsetToFile(
            spotCheckedBs,
            File(archiveRepo.repo.absolutePathWithoutDotGit()).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_BITSET_FILE_NAME")
        )

        LOGGER.info("Spot check id selected: $spaceType - $result")
        return result
    }

    private fun getSpotCheckedTopicMask(archiveRepo: RepoDto, spaceType: SpaceType): BitSet {
        val maskFile =
            File(archiveRepo.repo.absolutePathWithoutDotGit()).resolve("${spaceType.name.lowercase()}/$SPOT_CHECK_BITSET_FILE_NAME")
        if (!maskFile.exists()) {
            LOGGER.warn("Spot checked mask bitset file not found for $spaceType. Creating one.")
            return BitSet()
        }
        return getBitsetFromLongPlaintextFile(maskFile)
    }

    private fun getHiddenTopicMask(archiveRepo: RepoDto, spaceType: SpaceType): BitSet {
        val maskFile =
            File(archiveRepo.repo.absolutePathWithoutDotGit()).resolve("${spaceType.name.lowercase()}/$HIDDEN_TOPIC_MASK_FILE_NAME")
        if (!maskFile.exists()) {
            LOGGER.warn("Hidden topic mask file not found for $spaceType. Creating one.")
            genHiddenTopicMaskFile(archiveRepo, spaceType)
        }
        assert(maskFile.exists())
        return getBitsetFromLongPlaintextFile(maskFile)
    }

    fun getBitsetFromLongPlaintextFile(maskFile: File): BitSet {
        val maskStr = maskFile.readText(Charsets.UTF_8)
        return getBitsetFromLongListStr(maskStr)
    }

    fun getBitsetFromLongListStr(maskStr: String): BitSet {
        val longList = mutableListOf<Long>()
        maskStr.lines().mapNotNull { it.toLongOrNull() }.forEach { longList.add(it) }
        return getBitsetFromLongList(longList)
    }

    private fun getBitsetFromLongList(longList: MutableList<Long>): BitSet {
        val longArr = longList.toLongArray()
        return BitSet.valueOf(longArr)
    }

    private fun genHiddenTopicMaskFile(archiveRepo: RepoDto, spaceType: SpaceType) {
        LOGGER.info("Generating hidden topic mask file for $spaceType.")
        val maxId = if (spaceType in listOf(SpaceType.EP, SpaceType.PERSON, SpaceType.CHARACTER)) {
            getMaxIdByVisitingAllFiles(spaceType).coerceAtLeast(getMaxTopicId(spaceType))
        } else {
            getMaxTopicId(spaceType)
        }

        var newBs = BitSet(maxId + 2).apply { set(maxId + 1) } // Ensure (words in use) of bs is enough
        val visited = BitSet(maxId + 1)
//        val onceNormal = BitSet(maxId + 2).apply { set(maxId + 1) }
        walkThroughJson { (repoDto, relPath, fileContent) ->
            if (!relPath.contains(spaceType.name.lowercase())) return@walkThroughJson false
            if (!relPath.lowercase().endsWith("json")) return@walkThroughJson false
            if ((relPath.split("/").last().split(".").first().toIntOrNull() ?: -1) > maxId) return@walkThroughJson false
            if (relPath.hashCode() and 1023 == 1023) LOGGER.info("$relPath is processing at ${repoDto.repo.simpleName()}")
            val fileStr = fileContent.get()
            val topic = GSON.fromJson(fileStr, Topic::class.java)
            visited.set(topic.id)
            if (topic.isBlogRedirect()/* Possibly reopen */) {
                newBs.clear(topic.id)
            } else if (topic.isEmptyTopic()) {
                newBs.set(topic.id)
            } else {
                newBs.clear(topic.id)
            }
//            if (topic.isBlogRedirect()/* Possibly reopen */) {
//                onceNormal.set(topic.id)
//            } else if (!topic.isEmptyTopic()) {
//                onceNormal.set(topic.id)
//            }
            return@walkThroughJson true
        }
//        onceNormal.flip(0, onceNormal.size())
//        var newBs = onceNormal.clone() as BitSet
        LOGGER.info("New bitset size: ${newBs.size()}. Cardinality: ${newBs.cardinality()}. Zero count: ${newBs.size() - newBs.cardinality()}")
        val hiddenTopicMaskFileContent =
            archiveRepo.getFileContentAsStringInACommit(
                archiveRepo.getLastCommitSha1StrExtGit(),
                "${spaceType.name.lowercase()}/$HIDDEN_TOPIC_MASK_FILE_NAME"
            )
        if (hiddenTopicMaskFileContent.isNotBlank()) {
            LOGGER.warn("Old hidden topic mask file exists. Perform bitwise operation to gen a new one.")
            val oldBs = getBitsetFromLongListStr(hiddenTopicMaskFileContent)
            LOGGER.info("Old bitset size: ${oldBs.size()}. Cardinality: ${oldBs.cardinality()}. Zero count: ${oldBs.size() - oldBs.cardinality()}")
            val result = mergeOldNewVisited(oldBs, newBs, visited)
            newBs = result
            LOGGER.info("After merging op, new bitset size: ${newBs.size()}. Cardinality: ${newBs.cardinality()}. Zero count: ${newBs.size() - newBs.cardinality()}")
        }
        writeBitsetToFile(
            newBs,
            File(archiveRepo.repo.absolutePathWithoutDotGit()).resolve("${spaceType.name.lowercase()}/$HIDDEN_TOPIC_MASK_FILE_NAME")
        )
    }

    fun mergeOldNewVisited(oldBs: BitSet, newBs: BitSet, visited: BitSet): BitSet {
        val notVisited = (visited.clone() as BitSet).apply { flip(0, size()) }

        val visitedAndNew = (newBs.clone() as BitSet).apply { and(visited) }
        val notVisitedAndOld = (oldBs.clone() as BitSet).apply { and(notVisited) }
        val result = (visitedAndNew.clone() as BitSet).apply { or(notVisitedAndOld) }

        val diff = (oldBs.clone() as BitSet).apply { xor(result) }

        val maskedInOldButNotInNew = (oldBs.clone() as BitSet).apply { and(diff) }
        maskedInOldButNotInNew.stream().toList().let {
            LOGGER.info("What's masked in old but not masked in new? $it")
        }
        val maskedInNewButNotMaskedInOld = (newBs.clone() as BitSet).apply { and(diff) }
        maskedInNewButNotMaskedInOld.stream().toList().let {
            LOGGER.info("What's masked in new but not masked in old? $it")
        }
        return result
    }

    private fun writeBitsetToFile(bs: BitSet, file: File) {
        LOGGER.info("Writing bitset to file: ${file.absolutePath}")
        file.parentFile.mkdirs()
        val longs = bs.toLongArray()
        FileWriter(file).use { fw ->
            BufferedWriter(fw).use { bw ->
                longs.forEach { l ->
                    bw.write(l.toString())
                    bw.write("\n")
                }
                bw.flush()
            }
        }
    }

    fun getMaxTopicId(spaceType: SpaceType): Int {
        val result = getTopicList(spaceType).max().coerceAtLeast(TYPE_MAX_ID_MAP[spaceType]!!)
        TYPE_MAX_ID_MAP[spaceType] = result
        LOGGER.info("Max id for $spaceType: $result")
        return result
    }


    private fun walkThroughJson(
        jsonRepoList: List<RepoDto> = allJsonRepoListSingleton,
        handler: (RelPathAndContentSupp) -> Boolean
    ) {
        val timing = System.currentTimeMillis()
        LOGGER.info("Walking through json repo folders.")
        // we should ensure the oldest repo to be iterated first, so we resort the repos here
        val workingRepoList = allJsonRepoListSingleton.toMutableList().apply {
            sortBy {
                val lastCommitSha1 = it.getLastCommitSha1StrExtGit()
                val revCommit = it.getRevCommitById(lastCommitSha1)
                revCommit.authorIdent.whenAsInstant
            }
        }
        LOGGER.info("Json repo folders: ${workingRepoList.map { it.repo.absolutePathWithoutDotGit() }}")

        workingRepoList.forEach outer@{ jsonRepo ->
            if (jsonRepo.repo.isBare) {
                LOGGER.error("Not support walking through bare repo: {}", jsonRepo.repo.absolutePathWithoutDotGit())
                return@outer
            }
            val headSha1 = jsonRepo.getLastCommitSha1StrExtGit()
            LOGGER.info("Last commit sha1 of repo: $headSha1, msg: ${jsonRepo.getRevCommitById(headSha1).shortMessage}")
            val gitLsTree = ProcessBuilder()
                .command("git", "ls-tree", "-r", "--name-only", "HEAD")
                .directory(File(jsonRepo.repo.absolutePathWithoutDotGit()))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            gitLsTree.inputStream.use outerUse@{ ins ->
                InputStreamReader(ins, UTF_8).use { isr ->
                    BufferedReader(isr).use { br ->
                        val innerTiming = System.currentTimeMillis()
                        LOGGER.info("Start walking through json folder $jsonRepo.")
                        var line: String?
                        while (br.readLine().also { line = it } != null) {
                            val relPath = line!!
                            val fileContent = {
                                jsonRepo.getFileContentAsStringInACommit(headSha1, relPath)
                            }
                            runCatching {
                                handler.invoke(RelPathAndContentSupp(jsonRepo, relPath, fileContent))
                            }.onFailure {
                                LOGGER.error(
                                    "Ex when walking through ${jsonRepo.repo.absolutePathWithoutDotGit()}${File.separator}${relPath}: ",
                                    it
                                )
                            }
                        }
                        LOGGER.info(
                            "Finished walking through json folder {}. Timing: {}ms / {}",
                            jsonRepo.repo.absolutePathWithoutDotGit(),
                            System.currentTimeMillis() - innerTiming,
                            Duration.ofMillis(System.currentTimeMillis() - innerTiming)
                        )
                    }
                }
            }
        }
        LOGGER.info(
            "Finished walking through json repo folders. Timing: {}ms / {}",
            System.currentTimeMillis() - timing,
            Duration.ofMillis(System.currentTimeMillis() - timing)
        )
    }

    data class RelPathAndContentSupp(
        val repo: RepoDto,
        val relPath: String,
        val fileContent: Supplier<String>
    )


    @JvmStatic
    fun main(argv: Array<String>) {
        // LOGGER.info("max id for group ${getMaxId(SpaceType.GROUP)}")
        // repeat(10) { genSpotCheckListFile(SpaceType.SUBJECT) }
        // System.err.println(randomSelectTopicIds(SpaceType.GROUP))
//        SpaceType.values().forEach {
//            genHiddenTopicMaskFile(it)
//        }
//        randomSelectTopicIds(GitHelper.defaultArchiveRepoSingleton, spaceType = SpaceType.EP)
//        genEmptyTopicMaskFile(SpaceType.EP)
        allJsonRepoListSingleton.forEach {
            if (it.getCouplingArchiveRepo() == null) return@forEach
            if (!it.repo.simpleName().contains("gre")) return@forEach
            System.err.println(it.repo.simpleName())
            genHiddenTopicMaskFile(it.getCouplingArchiveRepo()!!, SpaceType.EP)
        }
    }

    private fun checkEmptyTopicMaskBitsetFile(htmlRepo: RepoDto, spaceType: SpaceType) {
        val bs = getBitsetFromLongPlaintextFile(
            File(htmlRepo.repo.absolutePathWithoutDotGit()).resolve(spaceType.name.lowercase())
                .resolve("empty_topic_has_masked_bs.txt")
        )
        val bsSize = bs.size()
        val bsCar = bs.cardinality()
        val bsZero = bsSize - bsCar
        LOGGER.info("bssize - $bsSize, bscar - $bsCar , bszero - $bsZero")
    }

    fun getMaxIdByVisitingAllFiles(spaceType: SpaceType): Int {
        var maxId = -1
        walkThroughJson { (_, relPath, _) ->
            if (!relPath.contains(spaceType.name.lowercase())) return@walkThroughJson false
            if (!relPath.lowercase().endsWith("json")) return@walkThroughJson false
            maxId = Math.max(maxId, relPath.split("/").last().split(".").first().toIntOrNull() ?: -1)
            return@walkThroughJson true
        }
        LOGGER.info("Max id for $spaceType from all files: $maxId")
        return maxId
    }
}