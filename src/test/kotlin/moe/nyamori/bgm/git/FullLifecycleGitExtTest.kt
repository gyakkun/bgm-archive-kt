package moe.nyamori.bgm.git

import moe.nyamori.bgm.config.ConfigDto
import moe.nyamori.bgm.config.RepoDto
import moe.nyamori.bgm.config.RepoType
import moe.nyamori.bgm.config.checkAndGetConfigDto
import moe.nyamori.bgm.config.setConfigDelegate
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper.getLatestCommitSha1StrExt
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.db.IBgmDaoProvider
import moe.nyamori.bgm.db.BgmDaoSqlite
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import moe.nyamori.bgm.util.CommitHistoryCacheHelper.buildCache
import moe.nyamori.bgm.util.FilePathHelper
import moe.nyamori.bgm.util.HttpHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class FullLifecycleGitTest {

    private lateinit var htmlRepoDir: File
    private lateinit var jsonRepoDir: File

    companion object {
        var globalRepoIdCounter = 1
    }

    @BeforeEach
    fun setup() {
        htmlRepoDir = File(System.getProperty("java.io.tmpdir"), "bgm-test-html-${System.currentTimeMillis()}")
        jsonRepoDir = File(System.getProperty("java.io.tmpdir"), "bgm-test-json-${System.currentTimeMillis()}")
        htmlRepoDir.mkdirs()
        jsonRepoDir.mkdirs()

        val htmlRepoId = globalRepoIdCounter++
        val jsonRepoId = globalRepoIdCounter++

        moe.nyamori.bgm.git.GitSanityCheck.initEmptyRepo(jsonRepoDir)
        
        val templates = moe.nyamori.bgm.git.GitSanityCheck.loadTemplatesFromClasspath()
        moe.nyamori.bgm.git.GitSanityCheck.generateRealisticRepo(htmlRepoDir, templates)

        val htmlRepoDto = RepoDto(
            id = htmlRepoId,
            path = htmlRepoDir.absolutePath,
            type = RepoType.HTML,
            optFriendlyName = "test-html",
            optRepoIdCouplingWith = jsonRepoId,
            optIsStatic = false,
            optMutexTimeoutMs = 1000L
        )
        val jsonRepoDto = RepoDto(
            id = jsonRepoId,
            path = jsonRepoDir.absolutePath,
            type = RepoType.JSON,
            optFriendlyName = "test-json",
            optRepoIdCouplingWith = htmlRepoId,
            optIsStatic = false,
            optMutexTimeoutMs = 1000L
        )

        val dbFile = File(htmlRepoDir, "test.db")
        val config = checkAndGetConfigDto().copy(
            preferJgit = false, // Key test component: disable jgit
            disableSpotCheck = true,
            repoList = listOf(htmlRepoDto, jsonRepoDto),
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath.replace('\\', '/')}",
            homeFolderAbsolutePath = htmlRepoDir.absolutePath // Isolate from user environment
        )
        setConfigDelegate(config)

        // Set up an isolated database connection pool that completely bypasses DSProvider
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath.replace('\\', '/')}"
            poolName = "TestSqlitePool"
            minimumIdle = 1
            maximumPoolSize = 1
        })
        val testJdbi = Jdbi.create(ds).apply {
            installPlugin(KotlinSqlObjectPlugin())
            installPlugin(KotlinPlugin())
        }
        Dao.setTestProvider(object : IBgmDaoProvider {
            val dao = testJdbi.onDemand(BgmDaoSqlite::class.java)
            override fun get() = dao
        })
        
        // Initialize tables for the isolated database using Flyway
        org.flywaydb.core.Flyway.configure()
            .dataSource(ds)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .sqlMigrationPrefix("0")
            .locations("db/sqlite")
            .schemas("main")
            .table("flyway_schema_history")
            .load()
            .migrate()
    }

    @AfterEach
    fun teardown() {
        htmlRepoDir.deleteRecursively()
        jsonRepoDir.deleteRecursively()
        Dao.resetProvider()
    }

    @Test
    fun testFullLifecycleGitExt() {
        testFullLifecycle(false)
    }

    @Test
    fun testFullLifecycleJgit() {
        testFullLifecycle(true)
    }

    private fun testFullLifecycle(useJgit: Boolean) {
        val currentConfig = moe.nyamori.bgm.config.Config
        val config = checkAndGetConfigDto().copy(
            preferJgit = useJgit,
            disableSpotCheck = true,
            repoList = currentConfig.repoList,
            jdbcUrl = currentConfig.jdbcUrl,
            homeFolderAbsolutePath = currentConfig.homeFolderAbsolutePath
        )
        setConfigDelegate(config)

        val htmlRepo = GitHelper.allArchiveRepoListSingleton.first()
        val jsonRepo = GitHelper.allJsonRepoListSingleton.first()

        val topicId = 123456 // The ID created by GitSanityCheck.generateRealisticRepo

        // 2. Html to Json processing
        CommitToJsonProcessor.job(true)

        // Verify json file is generated
        val jsonRelPath = FilePathHelper.numberToPath(topicId) + ".json"
        val jsonFile = File(jsonRepoDir, "group/$jsonRelPath")
        assertTrue(jsonFile.exists(), "JSON file should be created in coupling json repo")

        // Verify writeJsonRepoLastCommitId created prevProcessedCommitId file
        val prevProcessedFile = File(jsonRepoDir, moe.nyamori.bgm.config.Config.prevProcessedCommitRevIdFileName)
        assertTrue(prevProcessedFile.exists(), "prevProcessedCommitRevIdFileName should be created")

        // 3. Json to DB processing
        moe.nyamori.bgm.db.JsonToDbProcessor.job(true)

        // Verify DB contains parsed topic
        val maxId = Dao.bgmDao.getMaxTopicIdByType(SpaceType.GROUP.id)
        assertEquals(topicId, maxId, "Topic $topicId should be inserted into DB")

        // 4. Cache history
        HttpHelper.DB_WRITE_LOCK.lock()
        try {
            htmlRepo.buildCache()
        } finally {
            HttpHelper.DB_WRITE_LOCK.unlock()
        }

        // Verify cache updated successfully
        val prevCachedSha1 = Dao.bgmDao.getPrevCachedCommitId(htmlRepo)
        val latestHtmlSha1 = htmlRepo.getLatestCommitSha1StrExt()
        assertEquals(latestHtmlSha1, prevCachedSha1, "Cache marker should catch up to the latest commit")
    }
}
