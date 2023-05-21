package moe.nyamori.bgm.db

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.findChangedFilePaths
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.git.GitHelper.getLatestCommitRef
import moe.nyamori.bgm.git.GitHelper.getPrevPersistedJsonCommitRef
import moe.nyamori.bgm.git.GitHelper.getWalkBetweenCommitInReverseOrder
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.util.SealedTypeAdapterFactory
import moe.nyamori.bgm.util.StringHashingHelper
import moe.nyamori.bgm.util.TopicJsonHelper
import moe.nyamori.bgm.util.TopicJsonHelper.getLikeListFromTopic
import moe.nyamori.bgm.util.TopicJsonHelper.getPostListFromTopic
import moe.nyamori.bgm.util.TopicJsonHelper.getUserListFromPostList
import moe.nyamori.bgm.util.TopicJsonHelper.isValidTopic
import moe.nyamori.bgm.util.TopicJsonHelper.preProcessTopic
import org.eclipse.jgit.lib.ObjectId
import org.slf4j.LoggerFactory

object JsonToDbProcessor {
    val LOGGER = LoggerFactory.getLogger(JsonToDbProcessor.javaClass)
    val GSON = GsonBuilder()
        .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .registerTypeAdapterFactory(
            SealedTypeAdapterFactory.of(Space::class)
        ).create()

    fun job() {
        val latestCommit = GitHelper.jsonRepoSingleton.getLatestCommitRef()
        val prevPersistedCommit =
            getPrevPersistedJsonCommitRef()
        val walk = GitHelper.jsonRepoSingleton.getWalkBetweenCommitInReverseOrder(
            latestCommit,
            prevPersistedCommit,
            stepInAdvance = false,
        )
        val realPrevPersistedCommitForCheck = getPrevPersistedJsonCommitRef()
        var prev = walk.next()
        val sidNameMappingSet = mutableSetOf<SpaceNameMappingData>()
        var everFailed = false

        while (prev != null) {
            if (realPrevPersistedCommitForCheck == prev) {
                break
            }
            prev = walk.next()
        }

        LOGGER.info("The previously persisted json repo commit: $prev")
        run breakable@{
            walk.forEach outer@{ cur ->
                if (cur == prev) {
                    LOGGER.warn("Commit $cur has been iterated twice!")
                    return@breakable
                }
                val curCommitId = ObjectId.toString(cur)
                val curCommitFullMsg = cur.fullMessage
                if (curCommitFullMsg.startsWith("META", ignoreCase = true)) {
                    Dao.bgmDao().updatePrevPersistedCommitId(curCommitId)
                    prev = cur
                    return@outer
                }
                LOGGER.info("Persisting $curCommitFullMsg - $curCommitId")
                val changedFilePathList = GitHelper.jsonRepoSingleton.findChangedFilePaths(prev, cur)
                changedFilePathList.forEach inner@{ path ->
                    if (!path.endsWith("json")) return@inner
                    LOGGER.info("Path $path")
                    val jsonStr = GitHelper.jsonRepoSingleton.getFileContentAsStringInACommit(cur, path)
                    runCatching {
                        val topicUnmod = GSON.fromJson(jsonStr, Topic::class.java)

                        if (!isValidTopic(topicUnmod)) return@inner
                        val topic = preProcessTopic(topicUnmod)
                        val space = topic.space!!
                        val spaceTypeId = topic.space!!.type.id
                        val topicId = topic.id
                        // val topicListFromDb =
                        //    Dao.bgmDao().getTopicListByTypeAndTopicId(spaceTypeId, topicId)
                        // val postListFromDb =
                        //    Dao.bgmDao().getPostListByTypeAndTopicId(spaceTypeId, topicId)
                        val likeListFromDb = Dao.bgmDao().getLikeListByTypeAndTopicId(spaceTypeId, topicId)
                        // LOGGER.debug("topic {}", topicListFromDb)
                        // LOGGER.debug("post {}", postListFromDb)
                        LOGGER.debug("like {}", likeListFromDb)

                        val postListFromFile = getPostListFromTopic(topic)
                        val likeListFromFile = getLikeListFromTopic(topic)
                        val userListFromFile = getUserListFromPostList(postListFromFile)

                        // DONE : Calculate diff and update zero like,
                        val processedLikeList = calZeroLike(likeListFromFile, likeListFromDb, topic.isEmptyTopic())


                        Dao.bgmDao().batchUpsertUser(userListFromFile)
                        Dao.bgmDao().batchUpsertLikes(processedLikeList)
                        Dao.bgmDao().batchUpsertPost(spaceTypeId, topic.getSid(), postListFromFile)
                        Dao.bgmDao().batchUpsertTopic(spaceTypeId, listOf(topic))

                        if (space is Blog) {
                            TopicJsonHelper.handleBlogTagAndRelatedSubject(topic)
                            // TODO: Make use of blog author to set type=blog, sid = uid, name = username, displayName = nickname

                            // TODO: Remove deleted blog post
                            val postListFromDb =
                                Dao.bgmDao().getPostListByTypeAndTopicId(spaceTypeId, topicId)
                            val calDeletedBlogPost = calDeletedBlogPostRow(postListFromFile, postListFromDb)
                            Dao.bgmDao().batchUpsertPostRow(calDeletedBlogPost)
                        } else if (space is Subject) {
                            if (space.name != null) {
                                sidNameMappingSet.add(
                                    SpaceNameMappingData(
                                        SpaceType.SUBJECT.id,
                                        StringHashingHelper.stringHash(space.name!!),
                                        space.name!!,
                                        space.displayName!!
                                    )
                                )
                            } else {
                            }
                        } else if (space is Group) {
                            if (space.name != null) {
                                sidNameMappingSet.add(
                                    SpaceNameMappingData(
                                        SpaceType.GROUP.id,
                                        StringHashingHelper.stringHash(space.name!!),
                                        space.name!!,
                                        space.displayName!!
                                    )
                                )
                            } else {
                            }
                        } else {
                            //
                        }
                        Dao.bgmDao().upsertSidAlias(sidNameMappingSet)
                        sidNameMappingSet.clear()
                    }.onFailure {
                        LOGGER.error("Ex when checking content of $path at commit ${ObjectId.toString(cur)}", it)
                        LOGGER.error("Json Str: $jsonStr")
                        everFailed = true
                    }
                }

                Dao.bgmDao().updatePrevPersistedCommitId(curCommitId)
                prev = cur
            }
            Dao.bgmDao().handleNegativeUid()
        }
        LOGGER.info("Persisted last commit ${Dao.bgmDao().getPrevPersistedCommitId()}")
        if (everFailed) {
            LOGGER.error("Failed at persistence. Please check log!")
        }
    }

    private fun calDeletedBlogPostRow(postListFromFile: List<Post>, postListFromDb: List<PostRow>): Iterable<PostRow> {
        val dbPostSet = postListFromDb.toMutableSet()
        postListFromFile.forEach { filePost ->
            dbPostSet.removeIf { dbPost ->
                dbPost.id == filePost.id || dbPost.id == -dbPost.mid /* top post */
            }
        }
        val result = dbPostSet.map { it.copy(state = Post.STATE_DELETED) }.toList()
        return result
    }

    private fun calZeroLike(
        likeListFromFile: List<Like>,
        likeListFromDb: List<Like>,
        isEmptyTopic: Boolean = false
    ): List<Like> {
        if (isEmptyTopic) return emptyList()
        if (likeListFromFile.isEmpty() && likeListFromDb.isEmpty()) return emptyList()
        val merged = mutableSetOf<Like>().apply {
            addAll(likeListFromFile)
            addAll(likeListFromDb)
        }
        val typeId = merged.first().type
        val mid = merged.first().mid
        val result = mutableSetOf<Like>().apply { addAll(likeListFromFile) }


        val fileLikeKeys = likeListFromFile.groupBy { it.pid to it.value }.keys
        val dbLikeKeys = likeListFromDb.groupBy { it.pid to it.value }.keys
        // val fileLikeKeyCopy = fileLikeKeys.toMutableSet()
        val dbLikeKeysCopy = dbLikeKeys.toMutableSet()
        // If some keys not in file but in db, then update the result to set zero of these types
        dbLikeKeysCopy.removeAll(fileLikeKeys)
        if (dbLikeKeysCopy.isNotEmpty()) {
            LOGGER.info("Some keys not in file but in db. Updating them to zero.")
            dbLikeKeysCopy.forEach {
                LOGGER.info("Zero for type-$typeId, mid-$mid, pid-${it.first}, value-${it.second}")
                result.add(
                    Like(
                        type = typeId,
                        mid = mid,
                        pid = it.first,
                        value = it.second,
                        total = 0
                    )
                )
            }
        }
        return result.toList()
    }
}
