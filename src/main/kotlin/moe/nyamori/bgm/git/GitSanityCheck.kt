package moe.nyamori.bgm.git

import moe.nyamori.bgm.config.IConfig
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.FilePathHelper
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.random.Random

object GitSanityCheck {
    private val log = LoggerFactory.getLogger(GitSanityCheck::class.java)

    fun initEmptyRepo(dir: File) {
        dir.mkdirs()
        runGitCommand(dir, "git", "init")
        runGitCommand(dir, "git", "config", "user.name", "test")
        runGitCommand(dir, "git", "config", "user.email", "test@test.com")
        runGitCommand(dir, "git", "config", "commit.gpgsign", "false")
        runGitCommand(dir, "git", "commit", "-m", "META: Init.", "--allow-empty")
    }

    /**
     * Generates a realistic mock repository matching the structure of production bgm-archive repositories.
     * Contains multiple SpaceTypes and multiple files per commit, with proper meta files.
     */
    fun generateRealisticRepo(dir: File, groupTemplateHtml: String = "<html></html>") {
        initEmptyRepo(dir)

        // We will generate one commit per space type, containing multiple topics
        for (spaceType in SpaceType.entries) {
            val spaceName = spaceType.name.lowercase()
            val spaceDir = File(dir, spaceName)
            spaceDir.mkdirs()

            // Create meta files
            val metaTsFile = File(spaceDir, "meta_ts.txt")
            val topicListFile = File(spaceDir, "topiclist.txt")
            File(spaceDir, "bn.txt").writeText("")
            File(spaceDir, "ng.txt").writeText("")
            File(spaceDir, "sc.txt").writeText("")
            File(spaceDir, "spot_check_bitset.txt").writeText("")
            File(spaceDir, "hidden_topic_mask.txt").writeText("")

            val nowTs = System.currentTimeMillis()
            
            // For GROUP space, we add a specific topic to be tested in FullLifecycleGitExtTest
            val testTopicId = 123456
            if (spaceType == SpaceType.GROUP) {
                val postId1 = 111111
                val postId2 = 222222
                val htmlRelPath = FilePathHelper.numberToPath(testTopicId) + ".html"
                val htmlFile = File(spaceDir, htmlRelPath)
                htmlFile.parentFile.mkdirs()
                val sampleHtml = groupTemplateHtml
                    .replace("{topicId}", testTopicId.toString())
                    .replace("{postId1}", postId1.toString())
                    .replace("{postId2}", postId2.toString())
                htmlFile.writeText(sampleHtml)
                metaTsFile.appendText("$testTopicId:$nowTs\n")
                topicListFile.appendText("$testTopicId\n")
            }

            // Generate a bunch of random other files
            for (i in 1..20) {
                val randomTopicId = Random.nextInt(1000, 99999)
                if (randomTopicId == testTopicId) continue
                val htmlRelPath = FilePathHelper.numberToPath(randomTopicId) + ".html"
                val htmlFile = File(spaceDir, htmlRelPath)
                htmlFile.parentFile.mkdirs()
                htmlFile.writeText("<html><body>Dummy $spaceName $randomTopicId</body></html>")
                metaTsFile.appendText("$randomTopicId:$nowTs\n")
                topicListFile.appendText("$randomTopicId\n")
            }

            // Commit all files for this space type
            runGitCommand(dir, "git", "add", ".")
            val commitMsg = "${spaceType.name} TOPIC: 2026-05-23T00:00:00.000Z | $nowTs"
            runGitCommand(dir, "git", "commit", "-m", commitMsg)
        }
    }

    fun performSanityCheck(config: IConfig) {
        log.info("Performing GitSanityCheck to ensure native git execution and format compatibility...")
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bgm-sanity-${System.currentTimeMillis()}")
        try {
            generateRealisticRepo(tempDir)
            
            // Test 1: First commit should be META: Init.
            val firstCommitProcess = ProcessBuilder("git", "rev-list", "--max-parents=0", "HEAD")
                .directory(tempDir)
                .start()
            val firstCommitId = firstCommitProcess.inputStream.bufferedReader().readText().trim()
            if (firstCommitId.isBlank()) {
                throw IllegalStateException("GitSanityCheck failed: Could not retrieve first commit ID via rev-list.")
            }

            val firstCommitMsgProcess = ProcessBuilder("git", "log", "-1", "--format=%B", firstCommitId)
                .directory(tempDir)
                .start()
            val firstCommitMsg = firstCommitMsgProcess.inputStream.bufferedReader().readText().trim()
            if (!firstCommitMsg.contains("META: Init.")) {
                throw IllegalStateException("GitSanityCheck failed: First commit message is not 'META: Init.', got '$firstCommitMsg'")
            }

            // Test 2: Ensure git log --reverse --raw parses files correctly
            val logProcess = ProcessBuilder("git", "--no-pager", "log", "--reverse", "--raw", "--format=%H")
                .directory(tempDir)
                .start()
            val logOutput = logProcess.inputStream.bufferedReader().readText()
            if (!logOutput.contains("A\tgroup/topiclist.txt")) {
                throw IllegalStateException("GitSanityCheck failed: git log --raw output format changed or missing files! Output: \n$logOutput")
            }

            log.info("GitSanityCheck passed successfully.")
        } catch (e: Exception) {
            log.error("GitSanityCheck encountered an error: ", e)
            throw e
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun runGitCommand(dir: File, vararg command: String) {
        val pb = ProcessBuilder(*command)
            .directory(dir)
            .start()
        val exitCode = pb.waitFor()
        if (exitCode != 0) {
            val errorStream = pb.errorStream.bufferedReader().readText()
            throw IllegalStateException("Command ${command.joinToString(" ")} failed with exit code $exitCode: $errorStream")
        }
    }
}
