package moe.nyamori.bgm

import com.google.gson.*
import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.db.SpaceNameMappingData
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.getLatestCommitRef
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.util.SealedTypeAdapterFactory
import moe.nyamori.bgm.util.StringHashingHelper.stringHash
import moe.nyamori.bgm.util.TopicJsonHelper.getLikeListFromTopic
import moe.nyamori.bgm.util.TopicJsonHelper.getPostListFromTopic
import moe.nyamori.bgm.util.TopicJsonHelper.getUserListFromPostList
import moe.nyamori.bgm.util.TopicJsonHelper.handleBlogTagAndRelatedSubject
import moe.nyamori.bgm.util.TopicJsonHelper.isValidTopic
import moe.nyamori.bgm.util.TopicJsonHelper.preProcessTopic
import org.eclipse.jgit.lib.ObjectId
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.asStream

class DbTest {
    companion object {
        val LOGGER = LoggerFactory.getLogger(DbTest.javaClass)
        val GSON = GsonBuilder()
            .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .registerTypeAdapterFactory(
                SealedTypeAdapterFactory.of(Space::class)
            ).create()

        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            Dao.bgmDao().healthCheck()
            val dbTest = DbTest()
            dbTest.readJsonAndUpsert()

            dbTest.readJsonUpdateSpaceAliasMapping()


        }
    }


    @Test
    fun healthCheck() {
        assert(Dao.bgmDao().healthCheck() == 1)
    }

    // @Test
    fun insertTest() {
        Dao.bgmDao().batchUpsertUser(listOf(User(username = "hihihi", id = null, nickname = "")))
        Dao.bgmDao().batchUpsertUser(listOf(User(username = "hihihi1", id = null, nickname = "")))
        Dao.bgmDao().batchUpsertUser(listOf(User(username = "hihihi3", id = null, nickname = "")))
        Dao.bgmDao().batchUpsertUser(listOf(User(username = "hihihi4", id = 123456, nickname = "")))

        Dao.bgmDao().batchUpsertTopic(
            typeId = SpaceType.GROUP.id, listOf(
                Topic(
                    id = 12345,
                    space = Group(
                        name = "hihihi",
                        displayName = "Hi there!"
                    ),
                    uid = 12345,
                    title = "hihihi",
                    dateline = System.currentTimeMillis() / 1000,
                ),
                Topic(
                    id = 45678,
                    space = Subject(
                        name = "77777",
                        displayName = "Hi there!"
                    ),
                    uid = 12345,
                    title = "hihihi",
                    dateline = System.currentTimeMillis() / 1000,
                )
            )
        )

        Dao.bgmDao().batchUpsertLikes(
            listOf(
                Like(1, 2, 3, 4, 5),
                Like(1, 2, 3, 4, 5),
                Like(1, 2, 3, 4, 5),
                Like(1, 2, 3, 4, 5),
            )
        )
    }

    // @Test
    fun readJsonAndUpsert() {
        val jsonRepoFolders = ArrayList<String>()
        Config.BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST.split(",")
            .map {
                jsonRepoFolders.add(it.trim())
            }
        Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR.let { jsonRepoFolders.add(it) }


        jsonRepoFolders.forEach outer@{
            val folder = File(it)
            val fileStream = folder.walkBottomUp().asStream()//.parallel()
            fileStream.forEach inner@{ file ->
                runCatching {
                    if (file.isDirectory) return@inner
                    // if (file.absolutePath.contains("blog")) return@inner
                    if (!file.extension.equals("json", ignoreCase = true)) return@inner
                    if (file.nameWithoutExtension.hashCode() and 127 == 127) LOGGER.info("$file is processing")
                    val fileStr = FileUtil.getFileContent(file)!!
                    var topic = GSON.fromJson(fileStr, Topic::class.java)
                    if (!isValidTopic(topic)) return@inner
                    topic = preProcessTopic(topic)
                    val likeList = getLikeListFromTopic(topic)
                    val postList = getPostListFromTopic(topic)
                    val userList = getUserListFromPostList(postList)

                    Dao.bgmDao().batchUpsertUser(userList)
                    Dao.bgmDao().batchUpsertLikes(likeList)
                    Dao.bgmDao().batchUpsertPost(topic.space!!.type.id, postList)
                    Dao.bgmDao().batchUpsertTopic(topic.space!!.type.id, listOf(topic))

                    if (topic.space!! is Blog) {
                        handleBlogTagAndRelatedSubject(topic)
                    }
                }.onFailure { ex ->
                    LOGGER.error("Ex when handling $file: ", ex)
                }
            }
        }
        Dao.bgmDao().handleNegativeUid()
        Dao.bgmDao().updatePrevPersistedCommitId(ObjectId.toString(GitHelper.jsonRepoSingleton.getLatestCommitRef()))
    }


    // @Test
    fun readJsonUpdateSpaceAliasMapping() {
        val jsonRepoFolders = ArrayList<String>()
        Config.BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST.split(",")
            .map {
                jsonRepoFolders.add(it.trim())
            }
        Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR.let { jsonRepoFolders.add(it) }
        val sidNameMappingSet = ConcurrentHashMap.newKeySet<SpaceNameMappingData>()

        jsonRepoFolders.forEach outer@{
            val folder = File(it)
            val fileStream = folder.walkBottomUp().asStream().parallel()
            fileStream.forEach inner@{ file ->
                runCatching {
                    if (file.isDirectory) return@inner
                    if (!file.absolutePath.contains("group") && !file.absolutePath.contains("subject")) return@inner
                    if (!file.extension.equals("json", ignoreCase = true)) return@inner
                    if (file.nameWithoutExtension.hashCode() and 127 == 127) LOGGER.info("$file is processing")
                    val fileStr = FileUtil.getFileContent(file)!!
                    val topic = GSON.fromJson(fileStr, Topic::class.java)
                    if (!isValidTopic(topic)) return@inner
                    if (topic.isEmptyTopic()) return@inner

                    if (topic.space!!.type != SpaceType.GROUP && topic.space!!.type != SpaceType.SUBJECT) return@inner
                    if (topic.space!! is Group) {
                        val groupSpace = (topic.space!! as Group)
                        sidNameMappingSet.add(
                            SpaceNameMappingData(
                                SpaceType.GROUP.id,
                                stringHash(groupSpace.name!!),
                                groupSpace.name!!,
                                groupSpace.displayName!!
                            )
                        )
                    }
                    if (topic.space!! is Subject) {
                        val subjectSpace = (topic.space!! as Subject)
                        sidNameMappingSet.add(
                            SpaceNameMappingData(
                                SpaceType.SUBJECT.id,
                                stringHash(subjectSpace.name!!),
                                subjectSpace.name!!,
                                subjectSpace.displayName!!
                            )
                        )
                    }
                }.onFailure {
                    LOGGER.error("Ex when updating alias for file ${file.absolutePath}", it)
                }
            }
        }
        Dao.bgmDao().upsertSidAlias(sidNameMappingSet)
    }


}