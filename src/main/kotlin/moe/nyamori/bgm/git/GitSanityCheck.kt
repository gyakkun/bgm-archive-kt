package moe.nyamori.bgm.git

import moe.nyamori.bgm.config.IConfig
import moe.nyamori.bgm.git.GitHelper.getCommitById
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.git.GitHelper.getFirstCommitIdStr
import moe.nyamori.bgm.git.GitHelper.getLatestCommit
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.FilePathHelper
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
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
     * Loads HTML templates for each SpaceType from classpath resources.
     * Falls back to minimal HTML if a template is not found on the classpath.
     */
    fun loadTemplatesFromClasspath(): Map<SpaceType, String> {
        return SpaceType.entries.associateWith { spaceType ->
            val resourcePath = "/html_samples/templates/${spaceType.name.lowercase()}.html"
            GitSanityCheck::class.java.getResourceAsStream(resourcePath)?.bufferedReader()?.readText()
                ?: "<html></html>"
        }
    }

    /**
     * Generates a realistic mock repository matching the structure of production bgm-archive repositories.
     * Contains multiple SpaceTypes and multiple files per commit, with proper meta files.
     * Each HTML file uses the real template for its space type so that parsers can process them.
     */
    fun generateRealisticRepo(dir: File, templatesBySpaceType: Map<SpaceType, String> = loadTemplatesFromClasspath()) {
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
            val template = templatesBySpaceType[spaceType] ?: "<html></html>"

            // Add a specific topic (per space type) to be tested in FullLifecycleGitExtTest
            val testTopicId = 123456
            val postId1 = 111111
            val postId2 = 222222
            val htmlRelPath = FilePathHelper.numberToPath(testTopicId) + ".html"
            val htmlFile = File(spaceDir, htmlRelPath)
            htmlFile.parentFile.mkdirs()
            val sampleHtml = template
                .replace("{topicId}", testTopicId.toString())
                .replace("{postId1}", postId1.toString())
                .replace("{postId2}", postId2.toString())
            htmlFile.writeText(sampleHtml)
            metaTsFile.appendText("$testTopicId:$nowTs\n")
            topicListFile.appendText("$testTopicId\n")

            // Generate a bunch of random other files using real templates
            for (i in 1..20) {
                val randomTopicId = Random.nextInt(1000, 99999)
                if (randomTopicId == testTopicId) continue
                val rndPostId1 = Random.nextInt(100000, 999999)
                val rndPostId2 = Random.nextInt(100000, 999999)
                val rndHtmlRelPath = FilePathHelper.numberToPath(randomTopicId) + ".html"
                val rndHtmlFile = File(spaceDir, rndHtmlRelPath)
                rndHtmlFile.parentFile.mkdirs()
                val rndHtml = template
                    .replace("{topicId}", randomTopicId.toString())
                    .replace("{postId1}", rndPostId1.toString())
                    .replace("{postId2}", rndPostId2.toString())
                rndHtmlFile.writeText(rndHtml)
                metaTsFile.appendText("$randomTopicId:$nowTs\n")
                topicListFile.appendText("$randomTopicId\n")
            }

            // Commit all files for this space type
            runGitCommand(dir, "git", "add", ".")
            val commitMsg = "${spaceType.name} TOPIC: 2026-05-23T00:00:00.000Z | $nowTs"
            runGitCommand(dir, "git", "commit", "-m", commitMsg)

            // Fuzzy commit with weird characters
            val fuzzyHtmlRelPath = "fuzzy/\${Random.nextInt(100000)}.html"
            val fuzzyHtmlFile = File(spaceDir, fuzzyHtmlRelPath)
            fuzzyHtmlFile.parentFile.mkdirs()
            fuzzyHtmlFile.writeText("<html><body>Fuzzy! \\n\\r\\t \uD83D\uDE00 '\\\" \\\\</body></html>")
            runGitCommand(dir, "git", "add", ".")
            val fuzzyMsg = "FUZZY TOPIC: \n Line 2 \n Line 3 with 'single' and \"double\" quotes \n Emoji: \uD83D\uDE00 \n Backslash: \\"
            runGitCommand(dir, "git", "commit", "-m", fuzzyMsg)
        }
    }

    fun performSanityCheck(config: IConfig) {
        log.info("Performing GitSanityCheck to ensure native git execution and format compatibility...")
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bgm-sanity-${System.currentTimeMillis()}")
        try {
            generateRealisticRepo(tempDir, loadTemplatesFromClasspath())
            
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

            // Test 3: Strict Parity Check between JGit and GitExt
            val jgitRepo = FileRepositoryBuilder()
                .setGitDir(File(tempDir, ".git"))
                .build()

            val firstExt = jgitRepo.getFirstCommitIdStr(useJgit = false)
            val firstJgit = jgitRepo.getFirstCommitIdStr(useJgit = true)
            check(firstExt == firstJgit) { "Parity failure: getFirstCommitIdStr Ext=\$firstExt, Jgit=\$firstJgit" }

            val latestExt = jgitRepo.getLatestCommit(useJgit = false)
            val latestJgit = jgitRepo.getLatestCommit(useJgit = true)
            check(latestExt.sha1 == latestJgit.sha1) { "Parity failure: getLatestCommit sha1 Ext=${latestExt.sha1}, Jgit=${latestJgit.sha1}" }
            check(latestExt.fullMessage == latestJgit.fullMessage) { "Parity failure: getLatestCommit fullMessage Ext=${latestExt.fullMessage}, Jgit=${latestJgit.fullMessage}" }
            check(latestExt.commitTimeEpochMs == latestJgit.commitTimeEpochMs) { "Parity failure: getLatestCommit commitTimeEpochMs Ext=${latestExt.commitTimeEpochMs}, Jgit=${latestJgit.commitTimeEpochMs}" }
            check(latestExt.authorTimeEpochMs == latestJgit.authorTimeEpochMs) { "Parity failure: getLatestCommit authorTimeEpochMs Ext=${latestExt.authorTimeEpochMs}, Jgit=${latestJgit.authorTimeEpochMs}" }

            val firstCommitExt = jgitRepo.getCommitById(firstExt, useJgit = false)
            val firstCommitJgit = jgitRepo.getCommitById(firstJgit, useJgit = true)
            check(firstCommitExt.sha1 == firstCommitJgit.sha1) { "Parity failure: getCommitById sha1 Ext=${firstCommitExt.sha1}, Jgit=${firstCommitJgit.sha1}" }
            check(firstCommitExt.fullMessage == firstCommitJgit.fullMessage) { "Parity failure: getCommitById fullMessage Ext=${firstCommitExt.fullMessage}, Jgit=${firstCommitJgit.fullMessage}" }

            val fileRelPath = "group/topiclist.txt"
            val contentExt = jgitRepo.getFileContentAsStringInACommit(latestExt.sha1, fileRelPath, useJgit = false)
            val contentJgit = jgitRepo.getFileContentAsStringInACommit(latestExt.sha1, fileRelPath, useJgit = true)
            check(contentExt.trim() == contentJgit.trim()) { "Parity failure: getFileContentAsStringInACommit Ext=$contentExt, Jgit=$contentJgit" }
            
            // --- FUZZY RANDOMIZED PARITY CHECKS ---
            log.info("Starting fuzzy randomized parity checks...")
            val allCommitsProcess = ProcessBuilder("git", "log", "--format=%H")
                .directory(tempDir)
                .start()
            val allCommits = allCommitsProcess.inputStream.bufferedReader().readText().trim().lines().filter { it.isNotBlank() }

            val randomCommits = allCommits.shuffled(Random(System.currentTimeMillis())).take(15) // Check up to 15 random commits
            for (commitSha1 in randomCommits) {
                // Test getCommitById parity
                val extCommit = jgitRepo.getCommitById(commitSha1, useJgit = false)
                val jgitCommit = jgitRepo.getCommitById(commitSha1, useJgit = true)
                check(extCommit.sha1 == jgitCommit.sha1) { "Fuzzy Parity failure: getCommitById sha1 Ext=${extCommit.sha1}, Jgit=${jgitCommit.sha1}" }
                check(extCommit.fullMessage == jgitCommit.fullMessage) { "Fuzzy Parity failure: getCommitById fullMessage Ext=${extCommit.fullMessage}, Jgit=${jgitCommit.fullMessage}" }
                check(extCommit.commitTimeEpochMs == jgitCommit.commitTimeEpochMs) { "Fuzzy Parity failure: getCommitById commitTimeEpochMs Ext=${extCommit.commitTimeEpochMs}, Jgit=${jgitCommit.commitTimeEpochMs}" }
                check(extCommit.authorTimeEpochMs == jgitCommit.authorTimeEpochMs) { "Fuzzy Parity failure: getCommitById authorTimeEpochMs Ext=${extCommit.authorTimeEpochMs}, Jgit=${jgitCommit.authorTimeEpochMs}" }

                // Test getFileContentAsStringInACommit parity for a random file in this commit
                val lsTreeProcess = ProcessBuilder("git", "ls-tree", "-r", "--name-only", commitSha1)
                    .directory(tempDir)
                    .start()
                val files = lsTreeProcess.inputStream.bufferedReader().readText().trim().lines().filter { it.isNotBlank() }
                if (files.isNotEmpty()) {
                    val randomFile = files.random(Random(System.currentTimeMillis()))
                    val fuzzyContentExt = jgitRepo.getFileContentAsStringInACommit(commitSha1, randomFile, useJgit = false)
                    val fuzzyContentJgit = jgitRepo.getFileContentAsStringInACommit(commitSha1, randomFile, useJgit = true)
                    check(fuzzyContentExt.trim() == fuzzyContentJgit.trim()) { "Fuzzy Parity failure: getFileContentAsStringInACommit for $randomFile in $commitSha1" }
                }
            }
            log.info("Fuzzy randomized parity checks passed for ${randomCommits.size} commits!")
            
            jgitRepo.close()

            log.info("GitSanityCheck passed successfully. Strict Parity is 100%.")
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
