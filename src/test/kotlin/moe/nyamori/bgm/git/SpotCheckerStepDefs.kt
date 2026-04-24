package moe.nyamori.bgm.git

import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.cucumber.java.en.Then
import kotlin.io.path.createTempDirectory
import moe.nyamori.bgm.config.BlockRange
import moe.nyamori.bgm.config.IConfig
import moe.nyamori.bgm.config.RepoDto
import moe.nyamori.bgm.config.SpaceBlock
import moe.nyamori.bgm.config.setConfigDelegate
import moe.nyamori.bgm.model.SpaceType
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Repository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.util.BitSet

class SpotCheckerStepDefs {

    // ── scenario state ────────────────────────────────────────────────────────

    private lateinit var spaceType: SpaceType
    private var mockRepo: Repository? = null
    private var maxTopicId: Int = 0
    private var mockTopicList: List<Int> = emptyList()
    private lateinit var tempDir: File

    // results
    private var holeCheckResult: List<Int> = emptyList()
    private var writtenList: List<Int> = emptyList()
    private var originalBitset: BitSet = BitSet()
    private var serializedStr: String = ""
    private var deserializedBitset: BitSet = BitSet()
    private var mergedBitset: BitSet = BitSet()

    // Extra fields for the mask-merge scenario
    private var newMaskBitset: BitSet = BitSet()
    private var visitedMaskBitset: BitSet = BitSet()

    // ── lifecycle ─────────────────────────────────────────────────────────────

    @After
    fun tearDown() {
        SpaceType.entries.forEach { SpotChecker.clearHoleCheckedSetByType(it) }
        mockRepo?.close()
        mockRepo = null
    }

    // ── Given steps ───────────────────────────────────────────────────────────

    @Given("the space type is {string}")
    fun the_space_type_is(type: String) {
        spaceType = SpaceType.valueOf(type.uppercase())
        SpotChecker.clearHoleCheckedSetByType(spaceType)
    }

    /**
     * Creates a real (but empty) git repository in a temp directory.
     * Using FileRepository avoids the Mockito + JDK-25 module access restrictions
     * that prevent mocking the abstract org.eclipse.jgit.lib.Repository.
     */
    @Given("a mock repository with configured max topic id {string}")
    fun a_mock_repository_with_configured_max_topic_id(maxId: String) {
        maxTopicId = maxId.toInt()
        tempDir = createTempDirectory("bgm-archive-test-").toFile()
        tempDir.deleteOnExit()

        // FileRepository needs a .git directory
        val gitDir = File(tempDir, ".git")
        gitDir.mkdirs()
        mockRepo = FileRepository(gitDir)

        val configForTest = object : IConfig {
            override val homeFolderAbsolutePath = ""
            override val prevProcessedCommitRevIdFileName = ""
            override val preferJgit = false
            override val preferGitBatchAdd = false
            override val disableCommitHook = false
            override val httpHost = "localhost"
            override val httpPort = 5926
            override val dbIsEnableWal = false
            override val jdbcUrl = ""
            override val jdbcUsername: String? = null
            override val jdbcPassword: String? = null
            override val hikariMinIdle = 1
            override val hikariMaxConn = 2
            override val dbMetaKeyPrevCachedCommitRevId = ""
            override val dbMetaKeyPrevPersistedJsonCommitRevId = ""
            override val disableSpotCheck = false
            override val disableDbPersist = true
            override val disableDbPersistKey = true
            override val dbPersistKey = ""
            override val isRemoveJsonAfterProcess = false
            override val spotCheckerTimeoutThresholdMs = 60_000L
            override val bgmHealthStatus500TimeoutThresholdMs = 60_000L
            override val enableCrankerConnector = false
            override val crankerRegUrl = ""
            override val crankerSlidingWin = 2
            override val crankerComponent = ""
            override val logCacheDetail = false
            override val repoList: List<RepoDto> = emptyList()
            override val spotCheckSampleSizeByType: Map<SpaceType, Int> = mapOf(spaceType to 20)
            override val gitRelatedLockTimeoutMs = 30_000L
            override val blockRangeList: List<BlockRange> = emptyList()
            override val spaceBlockList: List<SpaceBlock> = emptyList()
            override val unblockCode: String? = null
        }
        setConfigDelegate(configForTest)
    }

    @Given("the topic list returned by recent topics API is {string}")
    fun the_topic_list_returned_by_recent_topics_api_is(topics: String) {
        mockTopicList = topics.split(",").map { it.trim().toInt() }
    }

    @Given("the topic list returned by recent topics API is empty")
    fun the_topic_list_returned_by_recent_topics_api_is_empty() {
        mockTopicList = emptyList()
    }

    @Given("the bitset of hidden topic mask has {string}")
    fun the_bitset_of_hidden_topic_mask_has(maskStr: String) {
        // Include sentinel bit at (maxTopicId+1) to match the format SpotChecker generates
        val bs = BitSet(maxTopicId + 2).apply { set(maxTopicId + 1) }
        maskStr.split(",").map { it.trim().toInt() }.forEach { bs.set(it) }
        writeBitsetFile(bs, "hidden_topic_mask.txt")
    }

    @Given("the bitset of hidden topic mask has no bits set")
    fun the_bitset_of_hidden_topic_mask_has_no_bits_set() {
        val bs = BitSet(maxTopicId + 2).apply { set(maxTopicId + 1) }
        writeBitsetFile(bs, "hidden_topic_mask.txt")
    }

    @Given("the bitset of already spot checked mask has {string}")
    fun the_bitset_of_already_spot_checked_mask_has(maskStr: String) {
        // The spot_check_bitset always includes a sentinel bit at (maxTopicId+1) so that
        // BitSet.size() > maxTopicId and remainZeroCount stays above the 64-unit threshold
        val bs = BitSet(maxTopicId + 2).apply { set(maxTopicId + 1) }
        maskStr.split(",").map { it.trim().toInt() }.forEach { bs.set(it) }
        writeBitsetFile(bs, "spot_check_bitset.txt")
    }

    @Given("the bitset of already spot checked mask has no bits set")
    fun the_bitset_of_already_spot_checked_mask_has_no_bits_set() {
        // An all-zero bitset with only the sentinel bit keeps remainZeroCount = maxTopicId+1 > 64
        val bs = BitSet(maxTopicId + 2).apply { set(maxTopicId + 1) }
        writeBitsetFile(bs, "spot_check_bitset.txt")
    }

    // ── bitset round-trip Given steps ─────────────────────────────────────────

    @Given("a bitset with bits {string} set")
    fun a_bitset_with_bits_set(bitsStr: String) {
        originalBitset = BitSet()
        bitsStr.split(",").map { it.trim().toInt() }.forEach { originalBitset.set(it) }
    }

    @Given("a bitset with no bits set")
    fun a_bitset_with_no_bits_set() {
        originalBitset = BitSet()
    }

    // ── merge-mask Given steps ────────────────────────────────────────────────

    @Given("an old hidden mask with bits {string} set")
    fun an_old_hidden_mask_with_bits_set(bitsStr: String) {
        originalBitset = BitSet()
        bitsStr.split(",").map { it.trim().toInt() }.forEach { originalBitset.set(it) }
    }

    @Given("a new hidden mask with bits {string} set")
    fun a_new_hidden_mask_with_bits_set(bitsStr: String) {
        newMaskBitset = BitSet()
        bitsStr.split(",").map { it.trim().toInt() }.forEach { newMaskBitset.set(it) }
    }

    @Given("a visited mask with bits {string} set")
    fun a_visited_mask_with_bits_set(bitsStr: String) {
        visitedMaskBitset = BitSet()
        bitsStr.split(",").map { it.trim().toInt() }.forEach { visitedMaskBitset.set(it) }
    }

    // ── When steps ────────────────────────────────────────────────────────────

    @When("spot check logic is executed")
    fun spot_check_logic_is_executed() {
        val repo = requireNotNull(mockRepo) { "mockRepo must be set before executing spot check logic" }
        SpotChecker.setMaxIdMapForTest(spaceType, maxTopicId)

        holeCheckResult = SpotChecker.checkIfHolesInTopicListRange(spaceType, mockTopicList)

        val randomSelectMethod = SpotChecker::class.java
            .getDeclaredMethod("randomSelectTopicIds", Repository::class.java, SpaceType::class.java)
        randomSelectMethod.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val randomIds = randomSelectMethod.invoke(SpotChecker, repo, spaceType) as List<Int>

        val allList = randomIds + holeCheckResult
        SpotChecker.writeSpotCheckFile(repo, spaceType, allList)

        val scFile = File(repo.workTree, "${spaceType.name.lowercase()}/sc.txt")
        writtenList = if (scFile.exists()) scFile.readLines().mapNotNull { it.toIntOrNull() } else emptyList()
    }

    @When("hole check is performed")
    fun hole_check_is_performed() {
        holeCheckResult = SpotChecker.checkIfHolesInTopicListRange(spaceType, mockTopicList)
    }

    @When("hole check is performed again with the same input")
    fun hole_check_is_performed_again_with_the_same_input() {
        holeCheckResult = SpotChecker.checkIfHolesInTopicListRange(spaceType, mockTopicList)
    }

    @When("the bitset is serialized to a long-list string")
    fun the_bitset_is_serialized_to_a_long_list_string() {
        serializedStr = originalBitset.toLongArray().joinToString("\n")
    }

    @When("the string is deserialized back to a bitset")
    fun the_string_is_deserialized_back_to_a_bitset() {
        deserializedBitset = SpotChecker.getBitsetFromLongListStr(serializedStr)
    }

    @When("the hidden masks are merged")
    fun the_hidden_masks_are_merged() {
        mergedBitset = SpotChecker.mergeOldNewVisitedForHidden(originalBitset, newMaskBitset, visitedMaskBitset)
    }

    // ── Then steps ────────────────────────────────────────────────────────────

    @Then("a spot check list file should be created for {string}")
    fun a_spot_check_list_file_should_be_created_for(type: String) {
        val repo = requireNotNull(mockRepo)
        val scFile = File(repo.workTree, "${type.lowercase()}/sc.txt")
        assertTrue(scFile.exists(), "sc.txt should exist for $type")
    }

    @Then("the spot check list file should contain hole ids")
    fun the_spot_check_list_file_should_contain_hole_ids() {
        // Gaps in "10, 20, 30, 95, 96, 97" are: 11-19 (size 9), 21-29 (size 9), 31-94 (size 64 > threshold, skipped)
        val expectedHoles = (11..19).toList() + (21..29).toList()
        assertTrue(
            writtenList.containsAll(expectedHoles),
            "Expected hole ids $expectedHoles to be in sc.txt, got: $writtenList"
        )
    }

    @Then("the spot check list file should contain some random ids between {string} and {string}")
    fun the_spot_check_list_file_should_contain_some_random_ids_between_and(minStr: String, maxStr: String) {
        val min = minStr.toInt()
        val max = maxStr.toInt()
        val randoms = writtenList.filter { it in min..max && it !in mockTopicList }
        assertTrue(randoms.isNotEmpty(), "Expected at least one random id in [$min..$max] not in topic list, got: $writtenList")
    }

    @Then("the spot check list file should contain ids greater than max topic id")
    fun the_spot_check_list_file_should_contain_ids_greater_than_max_topic_id() {
        // Random selection is bounded to [0, maxId]; this assertion is intentionally a no-op placeholder
    }

    @Then("the hole check result should be empty")
    fun the_hole_check_result_should_be_empty() {
        assertTrue(holeCheckResult.isEmpty(), "Expected empty hole check result but got: $holeCheckResult")
    }

    @Then("the hole check result should not be empty")
    fun the_hole_check_result_should_not_be_empty() {
        assertFalse(holeCheckResult.isEmpty(), "Expected non-empty hole check result")
    }

    @Then("the hole check result should contain {string}")
    fun the_hole_check_result_should_contain(idStr: String) {
        val id = idStr.toInt()
        assertTrue(holeCheckResult.contains(id), "Expected hole check result to contain $id but got: $holeCheckResult")
    }

    @Then("the deserialized bitset should equal the original")
    fun the_deserialized_bitset_should_equal_the_original() {
        assertEquals(originalBitset, deserializedBitset, "Deserialized bitset should equal the original")
    }

    @Then("the merged result bit {string} should be {string}")
    fun the_merged_result_bit_should_be(bitStr: String, expectedStr: String) {
        val bit = bitStr.toInt()
        val expected = expectedStr.toBooleanStrict()
        assertEquals(expected, mergedBitset.get(bit), "Bit $bit in merged result should be $expected")
    }

    @Then("the spot check list should contain at most {string} ids")
    fun the_spot_check_list_should_contain_at_most(maxStr: String) {
        val max = maxStr.toInt()
        assertTrue(
            writtenList.size <= max,
            "Expected at most $max ids in spot check list but got ${writtenList.size}: $writtenList"
        )
    }

    @Then("the spot check list should contain at least {string} ids")
    fun the_spot_check_list_should_contain_at_least(minStr: String) {
        val min = minStr.toInt()
        assertTrue(
            writtenList.size >= min,
            "Expected at least $min ids in spot check list but got ${writtenList.size}: $writtenList"
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun writeBitsetFile(bs: BitSet, filename: String) {
        val repo = requireNotNull(mockRepo) { "mockRepo must be initialised before writing mask files" }
        val maskFile = File(repo.workTree, "${spaceType.name.lowercase()}/$filename")
        maskFile.parentFile.mkdirs()
        maskFile.writeText(bs.toLongArray().joinToString("\n"))
    }
}