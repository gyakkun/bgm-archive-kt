package moe.nyamori.bgm

import com.google.gson.*
import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.db.SpaceNameMappingData
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.util.SealedTypeAdapterFactory
import moe.nyamori.bgm.util.StringHashingHelper.stringHash
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
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

//            LOGGER.info("neg user {}", Dao.bgmDao().getNegativeUidUsers())
//            LOGGER.info("prev commit id {}", Dao.bgmDao().getPrevProcessedCommitId())
//            Dao.bgmDao().handleNegativeUid()
//            LOGGER.info("neg user after handling {}", Dao.bgmDao().getNegativeUidUsers())
            dbTest.readJsonUpdateSpaceAliasMapping()
        }
    }

    lateinit var topicQueue: BlockingQueue<Topic>
    lateinit var postQueue: BlockingQueue<Pair<SpaceType, Post>>
    lateinit var likeQueue: BlockingQueue<Like>
    lateinit var userQueue: BlockingQueue<User>
    val QUEUE_LEN = 1000
    val BATCH_THRESHOLD = 200
    val TITLE_MAX_LENGTH = 100

    @Volatile
    var endAll = false

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

    @Test
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
                    val postList = topic.getAllPosts().map { processPostWithEmptyUid(it) }
                    val userList = postList.mapNotNull { it.user }.distinct()

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
        Dao.bgmDao().updatePrevProcessedCommitId(GitHelper.getPrevProcessedArchiveCommitRevId())
        endAll = true
    }

    private fun handleBlogTagAndRelatedSubject(topic: Topic) {
        val blogSpace = topic.space!! as Blog
        val relatedSubjectIds = blogSpace.relatedSubjectIds
        val tags = blogSpace.tags
        if (!relatedSubjectIds.isNullOrEmpty()) {
            Dao.bgmDao().upsertBlogSubjectIdMapping(relatedSubjectIds.map { Pair(topic.id, it) })
        }
        if (!tags.isNullOrEmpty()) {
            Dao.bgmDao().upsertBlogTagMapping(tags.map { Pair(topic.id, it) })
        }
    }

    @Test
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


    private fun preProcessTopic(topic: Topic): Topic {
        if (topic.uid != null) {
            return topic.copy(title = topic.title?.substring(0, topic.title!!.length.coerceAtMost(TITLE_MAX_LENGTH)))
        }
        if (topic.isEmptyTopic()) {
            return topic.copy(uid = 0, dateline = 0)
        }
        val topPostPid = topic.topPostPid!!
        val topPost = topic.getAllPosts().first { it.id == topPostPid }
        var topPostUser = topPost.user
        if (topPostUser == null) {
            topPostUser = User(id = 0, username = "0", nickname = "0")
        } else {
            if (topPostUser.id == null) {
                topPostUser = topPostUser.let { it.copy(id = stringHash(it.username)) }
            }
        }
        return topic.copy(
            uid = topPostUser.id,
            title = topic.title?.substring(0, topic.title!!.length.coerceAtMost(TITLE_MAX_LENGTH))
        )
    }

    private fun processPostWithEmptyUid(post: Post): Post {
        if (post.user != null && post.user!!.id != null) return post
        var postUser = post.user
        if (postUser == null) {
            postUser = User(id = 0, username = "0", nickname = "0")
        } else {
            if (postUser.id == null) {
                postUser = postUser.let { it.copy(id = stringHash(it.username)) }
            }
        }
        return post.copy(user = postUser)
    }

    private fun isValidTopic(topic: Topic): Boolean {
        return topic.space != null
        // && (topic.space!!.type == SpaceType.GROUP || topic.space!!.type == SpaceType.SUBJECT)
    }

    private fun getLikeListFromTopic(topic: Topic): List<Like> {
        if (!isValidTopic(topic)) return emptyList()

        if (topic.space?.meta?.get("data_likes_list") == null) return emptyList()
        val dataLikesList = topic.space!!.meta!!["data_likes_list"] as Map<String, Any?>
        return dataLikesList.map {
            val pid = it.key.toInt()
            val faces = it.value
            if (faces is Map<*, *>) {
                val res = ArrayList<Like>()
                (faces as Map<String, Any?>).forEach {
                    val faceId = it.key.toInt()
                    val m = it.value as Map<String, Any?>
                    val mid = (m["main_id"] as Double).toInt()
                    val value = (m["value"] as String).toInt()
                    val total = (m["total"] as String).toInt()
                    res.add(Like(type = topic.space!!.type.id, mid = mid, pid = pid, value = value, total = total))
                }
                return@map res
            } else if (faces is List<*>) {
                val res = ArrayList<Like>()
                (faces as List<Map<String, Any?>>).forEach {
                    val m = it
                    val mid = (m["main_id"] as Double).toInt()
                    val value = (m["value"] as String).toInt()
                    val total = (m["total"] as String).toInt()
                    res.add(Like(type = topic.space!!.type.id, mid = mid, pid = pid, value = value, total = total))
                }
                return@map res
            } else {
                return@map emptyList<Like>()
            }
        }.flatten().toList()

    }
}