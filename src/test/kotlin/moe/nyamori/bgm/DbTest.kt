package moe.nyamori.bgm

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.vladsch.flexmark.util.misc.FileUtil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.util.StringHashingHelper.stringHash
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.reflect.KClass
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
            runBlocking {
                Dao.bgmDao().healthCheck()
                val dbTest = DbTest()
                // dbTest.initQueue()

                dbTest.readJsonAndUpsert()
            }
        }
    }

    lateinit var topicQueue: BlockingQueue<Topic>
    lateinit var postQueue: BlockingQueue<Pair<SpaceType, Post>>
    lateinit var likeQueue: BlockingQueue<Like>
    lateinit var userQueue: BlockingQueue<User>
    val QUEUE_LEN = 1000
    val BATCH_THRESHOLD = 200

    @Volatile
    var endAll = false

    @Test
    fun healthCheck() {
        assert(Dao.bgmDao().healthCheck() == 1)
        // Dao.bgmDao().createTables()
        assert(Dao.bgmDao().healthCheck() == 1)

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

        // Dao.bgmDao().batchUpsertLikes(
        //     listOf(
        //         Like(1, 2, 3, 4, 5),
        //         Like(1, 2, 3, 4, 5),
        //         Like(1, 2, 3, 4, 5),
        //         Like(1, 2, 3, 4, 5),
        //     )
        // )
    }

    @Test
    suspend fun readJsonAndUpsert() = coroutineScope {
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
                    val likeList = getLikeListFromTopic(topic!!)
                    val postList = topic.getAllPosts().map { processPostWithEmptyUid(it) }
                    val userList = postList.map { it.user }.filterNotNull().distinct()

                    Dao.bgmDao().batchUpsertUser(userList)
                    // if (topic.space!!.type == SpaceType.BLOG) return@inner
                    Dao.bgmDao().batchUpsertLikes(likeList)
                    Dao.bgmDao().batchUpsertPost(topic.space!!.type.id, postList)
                    Dao.bgmDao().batchUpsertTopic(topic.space!!.type.id, listOf(topic))

                    //launch { topicQueue.put(topic) }
//
                    //launch { likeList.forEach { likeQueue.put(it) } }
//
                    //launch { postList.map { Pair(topic.space!!.type, it) }.forEach { postQueue.put(it) } }
                    //launch { userList.forEach { userQueue.put(it) } }
                }.onFailure { ex ->
                    LOGGER.error("Ex when handling $file: ", ex)
                }
            }
        }
        Dao.bgmDao().updatePrevProcessedCommitId(GitHelper.getPrevProcessedCommitRevId())
        endAll = true
    }

    private fun preProcessTopic(topic: Topic): Topic {
        if (topic.uid != null) return topic
        val topPostPid = topic.topPostPid
        val topPost = topic.getAllPosts().first { it.id == topPostPid }
        var topPostUser = topPost.user
        if (topPostUser == null) {
            topPostUser = User(id = 0, username = "0", nickname = "0")
        } else {
            if (topPostUser.id == null) {
                topPostUser = topPostUser.let { it.copy(id = stringHash(it.username)) }
            }
        }
        return topic.copy(uid = topPostUser.id)
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
        return topic.display && topic.space != null
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

    private fun initQueue() {
        topicQueue = LinkedBlockingQueue(QUEUE_LEN)
        postQueue = LinkedBlockingQueue(QUEUE_LEN)
        likeQueue = LinkedBlockingQueue(QUEUE_LEN)
        userQueue = LinkedBlockingQueue(QUEUE_LEN)
        thread(name = "topicThread") {
            var batchList = ArrayList<Topic>()
            var tmp: Topic
            while (!endAll) {
                if (topicQueue.isEmpty()) {
                    Thread.sleep(500)
                    continue
                }
                tmp = topicQueue.take()
                batchList.add(tmp)
                if (batchList.size >= BATCH_THRESHOLD) {
                    batchList
                        .filter { it.space != null }
                        .filter { it.space!!.type != null }
                        .groupBy { it.space!!.type }
                        .forEach { type, list ->
                            Dao.bgmDao().batchUpsertTopic(type.id, list)
                        }
                    batchList.clear()
                }
            }
            batchList.addAll(topicQueue)
            batchList
                .filter { it.space != null }
                .filter { it.space!!.type != null }
                .groupBy { it.space!!.type }
                .forEach { type, list ->
                    Dao.bgmDao().batchUpsertTopic(type.id, list)
                }
        }

        thread(name = "postThread") {
            var batchList = ArrayList<Pair<SpaceType, Post>>()
            var tmp: Pair<SpaceType, Post>
            while (!endAll) {
                if (postQueue.isEmpty()) {
                    Thread.sleep(500)
                    continue
                }
                tmp = postQueue.take()
                batchList.add(tmp)
                if (batchList.size >= BATCH_THRESHOLD) {
                    batchList
                        .groupBy { it.first }
                        .forEach { type, list ->
                            Dao.bgmDao().batchUpsertPost(type.id, list.map { it.second })
                        }
                    batchList.clear()
                }
            }
            batchList.addAll(postQueue)
            batchList
                .groupBy { it.first }
                .forEach { type, list ->
                    Dao.bgmDao().batchUpsertPost(type.id, list.map { it.second })
                }
        }


        thread(name = "userThread") {
            var batchList = ArrayList<User>()
            var tmp: User
            while (!endAll) {
                if (userQueue.isEmpty()) {
                    Thread.sleep(500)
                    continue
                }
                tmp = userQueue.take()
                batchList.add(tmp)
                if (batchList.size >= BATCH_THRESHOLD) {
                    batchList.stream().peek {
                        if (it.id == null) {
                            it.id = stringHash(it.username)
                        }
                    }
                    Dao.bgmDao().batchUpsertUser(batchList)
                    batchList.clear()
                }
            }
            batchList.addAll(userQueue)
            Dao.bgmDao().batchUpsertUser(batchList)
        }


        thread(name = "likeThread") {
            var batchList = ArrayList<Like>()
            var tmp: Like
            while (!endAll) {
                if (likeQueue.isEmpty()) {
                    Thread.sleep(500)
                }
                tmp = likeQueue.take()
                batchList.add(tmp)
                if (batchList.size >= BATCH_THRESHOLD) {
                    Dao.bgmDao().batchUpsertLikes(batchList)
                    batchList.clear()
                }
            }
            batchList.addAll(likeQueue)
            Dao.bgmDao().batchUpsertLikes(batchList)
        }

    }


}

private class SealedTypeAdapterFactory<T : Any> private constructor(
    private val baseType: KClass<T>,
    private val typeFieldName: String
) : TypeAdapterFactory {

    private val subclasses = baseType.sealedSubclasses
    private val nameToSubclass = subclasses.associateBy { it.simpleName!!.lowercase() }

    init {
        if (!baseType.isSealed) throw IllegalArgumentException("$baseType is not a sealed class")
    }

    override fun <R : Any> create(gson: Gson, type: TypeToken<R>?): TypeAdapter<R>? {
        if (type == null || subclasses.isEmpty() || subclasses.none { type.rawType.isAssignableFrom(it.java) }) return null

        val elementTypeAdapter = gson.getAdapter(JsonElement::class.java)
        val subclassToDelegate: Map<KClass<*>, TypeAdapter<*>> = subclasses.associateWith {
            gson.getDelegateAdapter(this, TypeToken.get(it.java))
        }
        return object : TypeAdapter<R>() {
            override fun write(writer: JsonWriter, value: R) {
                val srcType = value::class
                val label = srcType.simpleName!!
                @Suppress("UNCHECKED_CAST") val delegate = subclassToDelegate[srcType] as TypeAdapter<R>
                val jsonObject = delegate.toJsonTree(value).asJsonObject

                if (jsonObject.has(typeFieldName)) {
                    throw JsonParseException("cannot serialize $label because it already defines a field named $typeFieldName")
                }
                val clone = JsonObject()
                clone.add(typeFieldName, JsonPrimitive(label))
                jsonObject.entrySet().forEach {
                    clone.add(it.key, it.value)
                }
                elementTypeAdapter.write(writer, clone)
            }

            override fun read(reader: JsonReader): R {
                val element = elementTypeAdapter.read(reader)
                val labelElement = element.asJsonObject.remove(typeFieldName) ?: throw JsonParseException(
                    "cannot deserialize $baseType because it does not define a field named $typeFieldName"
                )
                val name = labelElement.asString.lowercase()
                val subclass =
                    nameToSubclass[name] ?: throw JsonParseException("cannot find $name subclass of $baseType")
                @Suppress("UNCHECKED_CAST")
                return (subclass.objectInstance as? R) ?: (subclassToDelegate[subclass]!!.fromJsonTree(element) as R)
            }
        }
    }

    companion object {
        fun <T : Any> of(clz: KClass<T>) = SealedTypeAdapterFactory(clz, "type")
    }
}