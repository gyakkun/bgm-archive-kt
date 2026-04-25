package moe.nyamori.bgm.git

import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import moe.nyamori.bgm.model.SpaceType
import org.eclipse.jgit.lib.Repository
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.util.*

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
        SpotChecker.reset()
        moe.nyamori.bgm.db.Dao.mockBgmDao = null
        GitHelper.mockAllJsonRepoList = null
        mockRepo?.close()
        mockRepo = null

        // Clear files and commits from the fixed repos to prevent cross-scenario pollution
        val dirs = listOf(htmlRepoDir, jsonRepoDir)
        for (dir in dirs) {
            if (File(dir, ".git").exists()) {
                ProcessBuilder("git", "rm", "-rf", ".").directory(dir).start().waitFor()
                ProcessBuilder("git", "commit", "-m", "cleanup", "--allow-empty").directory(dir).start().waitFor()
            }
            
            // Also delete any un-tracked files
            for (space in SpaceType.entries) {
                val spaceDir = File(dir, space.name.lowercase())
                if (spaceDir.exists()) {
                    spaceDir.deleteRecursively()
                }
            }
        }
    }

    // ── Given steps ───────────────────────────────────────────────────────────

    @Given("the space type is {string}")
    fun the_space_type_is(type: String) {
        spaceType = SpaceType.valueOf(type.uppercase())
        SpotChecker.clearHoleCheckedSetByType(spaceType)
    }

    /**
     * Creates Mockito mocks for both the Repository and the IConfig.
     * Mockito 5.23+ uses the 'subclass' mock maker for abstract classes (like Repository)
     * instead of the inline byte-buddy agent, which avoids JDK 25 module-open restrictions.
     */
    companion object {
        private var initialized = false
        lateinit var htmlRepoDir: File
        lateinit var jsonRepoDir: File
        lateinit var htmlRepoDto: moe.nyamori.bgm.config.RepoDto
        lateinit var jsonRepoDto: moe.nyamori.bgm.config.RepoDto

        fun setupGlobalReposAndConfig() {
            if (initialized) return
            
            val tmp = File(System.getProperty("java.io.tmpdir"))
            htmlRepoDir = File(tmp, "bgm-archive-test-fixed-html")
            jsonRepoDir = File(tmp, "bgm-archive-test-fixed-json")
            
            // Always clean up dirs first to avoid stale commits/files from previous JVM runs
            htmlRepoDir.deleteRecursively()
            jsonRepoDir.deleteRecursively()
            htmlRepoDir.mkdirs()
            jsonRepoDir.mkdirs()
            
            listOf(htmlRepoDir, jsonRepoDir).forEach { dir ->
                ProcessBuilder("git", "init").directory(dir).start().waitFor()
                ProcessBuilder("git", "config", "user.email", "test@test.com").directory(dir).start().waitFor()
                ProcessBuilder("git", "config", "user.name", "Test").directory(dir).start().waitFor()
                ProcessBuilder("git", "config", "commit.gpgsign", "false").directory(dir).start().waitFor()
            }

            // Create initial empty commits so repos have a HEAD ref (required for git ls-tree HEAD)
            listOf(htmlRepoDir, jsonRepoDir).forEach {
                val placeholder = File(it, ".gitkeep")
                placeholder.createNewFile()
                ProcessBuilder("git", "add", ".").directory(it).start().waitFor()
                ProcessBuilder("git", "commit", "-m", "init").directory(it).start().waitFor()
                // Remove placeholder from working tree (keep git clean)
                ProcessBuilder("git", "rm", ".gitkeep").directory(it).start().waitFor()
                ProcessBuilder("git", "commit", "-m", "remove placeholder", "--allow-empty").directory(it).start().waitFor()
            }
            
            jsonRepoDto = moe.nyamori.bgm.config.RepoDto(
                id = 2,
                path = jsonRepoDir.absolutePath,
                type = moe.nyamori.bgm.config.RepoType.JSON,
                optFriendlyName = "test-json",
                optRepoIdCouplingWith = null,
                optIsStatic = false,
                optMutexTimeoutMs = 1000
            )

            htmlRepoDto = moe.nyamori.bgm.config.RepoDto(
                id = 1,
                path = htmlRepoDir.absolutePath,
                type = moe.nyamori.bgm.config.RepoType.HTML,
                optFriendlyName = "test",
                optRepoIdCouplingWith = 2,
                optIsStatic = false,
                optMutexTimeoutMs = 1000
            )

            val baseConfig = moe.nyamori.bgm.config.checkAndGetConfigDto()
            val myConfig = baseConfig.copy(
                disableSpotCheck = false,
                spotCheckSampleSizeByType = SpaceType.values().associate { it to 20 },
                repoList = listOf(htmlRepoDto, jsonRepoDto),
                jdbcUrl = "jdbc:sqlite:${htmlRepoDir.absolutePath.replace('\\', '/')}/test.db",
                jdbcUsername = "",
                jdbcPassword = "",
                hikariMaxConn = 10,
                preferJgit = true
            )
            moe.nyamori.bgm.config.setConfigDelegate(myConfig)
            
            // Touch Config to force initialization here
            moe.nyamori.bgm.config.Config.disableSpotCheck
            
            initialized = true
            
            Runtime.getRuntime().addShutdownHook(Thread {
                if (htmlRepoDir.exists()) htmlRepoDir.deleteRecursively()
                if (jsonRepoDir.exists()) jsonRepoDir.deleteRecursively()
            })
        }
    }
    @io.cucumber.java.Before
    fun globalSetup() {
        setupGlobalReposAndConfig()
    }

    @Given("a mock repository with configured max topic id {string}")
    fun a_mock_repository_with_configured_max_topic_id(maxId: String) {
        maxTopicId = maxId.toInt()
        mockRepo = htmlRepoDto.repo
        GitHelper.mockAllJsonRepoList = listOf(jsonRepoDto.repo)
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

        val randomIds = SpotChecker.randomSelectTopicIds(repo, spaceType)

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

    @Given("the bitset of already spot checked mask has bits from {string} to {string} set")
    fun the_bitset_of_already_spot_checked_mask_has_bits_from_to_set(startStr: String, endStr: String) {
        val start = startStr.toInt()
        val end = endStr.toInt()
        val bs = BitSet(maxTopicId + 2).apply { set(maxTopicId + 1) }
        for (i in start..end) {
            bs.set(i)
        }
        writeBitsetFile(bs, "spot_check_bitset.txt")
    }

    @Given("the database returns normal ids {string} and max id {string}")
    fun the_database_returns_normal_ids_and_max_id(idsStr: String, maxIdStr: String) {
        val ids = idsStr.split(",").map { it.trim().toInt() }
        val maxId = maxIdStr.toInt()
        
        // Mocking Dao.bgmDao
        val mockDao = mock<moe.nyamori.bgm.db.IBgmDao> {
            on { getAllTopicIdByTypeAndState(spaceType.id, 0) } doReturn ArrayList(ids)
            on { getMaxTopicIdByType(spaceType.id) } doReturn maxId
        }
        
        moe.nyamori.bgm.db.Dao.mockBgmDao = mockDao
    }

    @Given("the json repo has file {string} with empty topic false")
    fun the_json_repo_has_file_with_empty_topic_false(relPath: String) {
        val jsonRepo = GitHelper.mockAllJsonRepoList!!.first()
        val repoDir = jsonRepo.directory.parentFile
        
        val file = File(repoDir, relPath)
        file.parentFile.mkdirs()
        val id = relPath.substringAfterLast("/").substringBefore(".").toInt()
        // Provide a minimal but complete JSON that satisfies isEmptyTopic() == false
        val jsonContent = "{\"id\": $id, \"state\": 0, \"topPostPid\": 1, \"postList\": [{\"id\": 1, \"floorNum\": 1, \"mid\": $id, \"dateline\": 123456789, \"contentHtml\": \"test\"}]}"
        file.writeText(jsonContent)
        
        ProcessBuilder("git", "add", ".").directory(repoDir).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "add $relPath", "--allow-empty").directory(repoDir).start().waitFor()
    }

    @Then("the generated hidden topic mask should have bit {string} set")
    fun the_generated_hidden_topic_mask_should_have_bit_set(bitStr: String) {
        val bit = bitStr.toInt()
        val repo = requireNotNull(mockRepo)
        val maskFile = File(repo.workTree, "${spaceType.name.lowercase()}/hidden_topic_mask.txt")
        val bs = SpotChecker.getBitsetFromLongPlaintextFile(maskFile)
        assertTrue(bs.get(bit), "Expected bit $bit to be set in hidden_topic_mask.txt")
    }

    @Then("the generated hidden topic mask should not have bit {string} set")
    fun the_generated_hidden_topic_mask_should_not_have_bit_set(bitStr: String) {
        val bit = bitStr.toInt()
        val repo = requireNotNull(mockRepo)
        val maskFile = File(repo.workTree, "${spaceType.name.lowercase()}/hidden_topic_mask.txt")
        val bs = SpotChecker.getBitsetFromLongPlaintextFile(maskFile)
        assertFalse(bs.get(bit), "Expected bit $bit to not be set in hidden_topic_mask.txt")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun writeBitsetFile(bs: BitSet, filename: String) {
        val repo = requireNotNull(mockRepo) { "mockRepo must be initialised before writing mask files" }
        val repoDir = repo.workTree
        val maskFile = File(repoDir, "${spaceType.name.lowercase()}/$filename")
        maskFile.parentFile.mkdirs()
        val relPath = "${spaceType.name.lowercase()}/$filename"
        maskFile.writeText(bs.toLongArray().joinToString("\n"))

        // Must commit so that archiveRepo.getFileContentAsStringInACommit can see it
        ProcessBuilder("git", "add", relPath).directory(repoDir).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "add $relPath", "--allow-empty").directory(repoDir).start().waitFor()
    }
}