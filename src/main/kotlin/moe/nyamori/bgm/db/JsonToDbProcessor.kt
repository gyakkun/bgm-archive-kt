package moe.nyamori.bgm.db

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import moe.nyamori.bgm.config.toRepoDtoOrThrow
import moe.nyamori.bgm.git.GitHelper.absolutePathWithoutDotGit
import moe.nyamori.bgm.git.GitHelper.allJsonRepoListSingleton
import moe.nyamori.bgm.git.GitHelper.findChangedFilePaths
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.git.GitHelper.getLatestCommitRef
import moe.nyamori.bgm.git.GitHelper.getPrevPersistedJsonCommitRef
import moe.nyamori.bgm.git.GitHelper.getWalkBetweenCommitInReverseOrder
import moe.nyamori.bgm.git.GitHelper.hasCouplingArchiveRepo
import moe.nyamori.bgm.git.GitHelper.simpleName
import moe.nyamori.bgm.model.*
import moe.nyamori.bgm.util.GitCommitIdHelper.sha1Str
import moe.nyamori.bgm.util.SealedTypeAdapterFactory
import moe.nyamori.bgm.util.StringHashingHelper
import moe.nyamori.bgm.util.TopicJsonHelper
import moe.nyamori.bgm.util.TopicJsonHelper.getLikeListFromTopic
import moe.nyamori.bgm.util.TopicJsonHelper.getLikeRevListFromTopic
import moe.nyamori.bgm.util.TopicJsonHelper.getPostListFromTopic
import moe.nyamori.bgm.util.TopicJsonHelper.getUserListFromPostList
import moe.nyamori.bgm.util.TopicJsonHelper.isValidTopic
import moe.nyamori.bgm.util.TopicJsonHelper.preProcessTopic
import org.eclipse.jgit.lib.Repository
import org.slf4j.LoggerFactory

object JsonToDbProcessor {
    val LOGGER = LoggerFactory.getLogger(JsonToDbProcessor.javaClass)
    val GSON = GsonBuilder()
        .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .registerTypeAdapterFactory(
            SealedTypeAdapterFactory.of(Space::class)
        ).create()

    init {
        // ensure they have different hash
        check(allJsonRepoListSingleton.map { it.absolutePathWithoutDotGit() }
            .distinct().size == allJsonRepoListSingleton.size)
    }

    fun job(isAll: Boolean = false, id: Int = 0) {
        val reposToProcess = mutableListOf<Repository>()
        if (isAll) {
            allJsonRepoListSingleton
                .filter { it.hasCouplingArchiveRepo() && !it.toRepoDtoOrThrow().isStatic }
                .map { reposToProcess.add(it) }
        } else {
            if (id in allJsonRepoListSingleton.map { it.toRepoDtoOrThrow().id }
                && allJsonRepoListSingleton.find { it.toRepoDtoOrThrow().id == id }!!.hasCouplingArchiveRepo()
            ) {
                reposToProcess.add(allJsonRepoListSingleton[id])
            }
        }
        reposToProcess.forEach { jsonRepo ->

            val latestCommit = jsonRepo.getLatestCommitRef()
            val prevPersistedCommit =
                getPrevPersistedJsonCommitRef(jsonRepo)
            val walk = jsonRepo.getWalkBetweenCommitInReverseOrder(
                latestCommit,
                prevPersistedCommit,
                stepInAdvance = false,
            )
            val realPrevPersistedCommitForCheck = getPrevPersistedJsonCommitRef(jsonRepo)
            var prev = walk.next()
            val sidNameMappingSet = mutableSetOf<SpaceNameMappingData>()
            var everFailed = false

            // FIXME
            while (prev != null) {
                if (realPrevPersistedCommitForCheck == prev) {
                    break
                }
                prev = walk.next()
            }

            LOGGER.info("The previously persisted json repo commit for ${jsonRepo.absolutePathWithoutDotGit()}: $prev")
            run breakable@{
                walk.forEach outer@{ cur ->
                    if (cur == prev) {
                        LOGGER.warn("Commit $cur has been iterated twice! Repo: ${jsonRepo.simpleName()}")
                        return@breakable
                    }
                    val curCommitId = cur.sha1Str()
                    val curCommitFullMsg = cur.fullMessage
                    if (curCommitFullMsg.startsWith("META", ignoreCase = true)) {
                        Dao.bgmDao.updatePrevPersistedCommitId(jsonRepo, curCommitId)
                        prev = cur
                        return@outer
                    }
                    LOGGER.info("Persisting $curCommitFullMsg - $curCommitId , repo - ${jsonRepo.simpleName()}")
                    val changedFilePathList = jsonRepo.findChangedFilePaths(prev, cur)
                    changedFilePathList.forEach inner@{ path ->
                        if (!path.endsWith("json")) return@inner
                        LOGGER.info("Path ${jsonRepo.simpleName()}/$path")
                        var jsonStr = "NOT READY YET"
                        runCatching {
                            jsonStr = jsonRepo.getFileContentAsStringInACommit(cur.sha1Str(), path)
                            val topicUnmod = GSON.fromJson(jsonStr, Topic::class.java)

                            if (!isValidTopic(topicUnmod)) return@inner
                            val topic = preProcessTopic(topicUnmod)
                            val space = topic.space!!
                            val spaceTypeId = topic.space!!.type.id
                            val topicId = topic.id
                            // val topicListFromDb =
                            //    Dao.bgmDao.getTopicListByTypeAndTopicId(spaceTypeId, topicId)
                            // val postListFromDb =
                            //    Dao.bgmDao.getPostListByTypeAndTopicId(spaceTypeId, topicId)
                            val likeListFromDb = Dao.bgmDao.getLikeListByTypeAndTopicId(spaceTypeId, topicId)
                            val likeRevListFromDb = Dao.bgmDao.getLikeRevListByTypeAndTopicId(spaceTypeId, topicId)
                            // LOGGER.debug("topic {}", topicListFromDb)
                            // LOGGER.debug("post {}", postListFromDb)
                            LOGGER.debug("like {}", likeListFromDb)

                            val postListFromFile = getPostListFromTopic(topic)
                            val likeListFromFile = getLikeListFromTopic(topic)
                            val likeRevUsernameFromFile = getLikeRevListFromTopic(topic)
                            val userListFromFile = getUserListFromPostList(postListFromFile)

                            // DONE : Calculate diff and update zero like,
                            val processedLikeList = calZeroLike(likeListFromFile, likeListFromDb, topic.isEmptyTopic())

                            // TODO: Calculate uid from username
                            val (processedLikeRevList, constructedUser) =
                                calLikeRev(likeRevUsernameFromFile, likeRevListFromDb, topic.isEmptyTopic())

                            Dao.bgmDao.batchUpsertUser(userListFromFile)
                            Dao.bgmDao.batchUpsertUser(constructedUser)
                            Dao.bgmDao.batchUpsertLikes(processedLikeList)
                            Dao.bgmDao.batchUpsertLikesRev(processedLikeRevList)
                            Dao.bgmDao.batchUpsertPost(spaceTypeId, topic.getSid(), postListFromFile)
                            Dao.bgmDao.batchUpsertTopic(spaceTypeId, listOf(topic))

                            specialHandlingForSpaceNameMapping(
                                space,
                                topic,
                                spaceTypeId,
                                topicId,
                                postListFromFile,
                                sidNameMappingSet
                            )
                            Dao.bgmDao.upsertSidAlias(sidNameMappingSet)
                            sidNameMappingSet.clear()
                        }.onFailure {
                            LOGGER.error(
                                "Ex when checking content of $path at commit ${cur.sha1Str()}, repo - ${jsonRepo.simpleName()}",
                                it
                            )
                            LOGGER.error("Json Str: $jsonStr")
                            everFailed = true
                        }
                    }

                    Dao.bgmDao.updatePrevPersistedCommitId(jsonRepo, curCommitId)
                    prev = cur
                }
                Dao.bgmDao.handleNegativeUid()
            }
            LOGGER.info(
                "Persisted last commit for repo ${jsonRepo.simpleName()}: ${
                    Dao.bgmDao.getPrevPersistedCommitId(jsonRepo)
                }"
            )
            if (everFailed) {
                LOGGER.error("Failed at persistence for repo ${jsonRepo.simpleName()}. Please check log!")
            }
        }
    }


    private fun specialHandlingForSpaceNameMapping(
        space: Space,
        topic: Topic,
        spaceTypeId: Int,
        topicId: Int,
        postListFromFile: List<Post>,
        sidNameMappingSet: MutableSet<SpaceNameMappingData>
    ) {
        if (space is Blog) {
            TopicJsonHelper.handleBlogTagAndRelatedSubject(topic)

            val postListFromDb =
                Dao.bgmDao.getPostListByTypeAndTopicId(spaceTypeId, topicId)
            val calDeletedBlogPost = calDeletedBlogPostRow(postListFromFile, postListFromDb)
            Dao.bgmDao.batchUpsertPostRow(calDeletedBlogPost)

            if (!topic.isEmptyTopic()) {
                runCatching {
                    val topPost = topic.getAllPosts().firstOrNull {
                        it.id == topic.topPostPid
                    } ?: return@runCatching
                    if (topPost.user == null) return@runCatching
                    sidNameMappingSet.add(
                        SpaceNameMappingData(
                            SpaceType.BLOG.id,
                            topic.getSid() ?: topPost.user!!.getId() /* blog */,
                            topPost.user!!.username,
                            topPost.user!!.nickname ?: ""
                        )
                    )
                }.onFailure {
                    LOGGER.error("Ex when extracting blog topic user/sid/name/displayName, ", it)
                }
            }
        } else {
            runCatching {
                if (space.name != null && space.displayName != null) {
                    sidNameMappingSet.add(
                        SpaceNameMappingData(
                            space.type.id,
                            topic.getSid() ?: StringHashingHelper.stringHash(space.name),
                            space.name,
                            space.displayName
                        )
                    )
                }
            }.onFailure {
                LOGGER.error("Ex when extracting space id - name mapping, ", it)
            }
        }
    }

    @JvmStatic
    fun main(argv: Array<String>) {
        val space = Blog()
        System.err.println("Blog as reserved : ${space.name}")
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

    private fun calLikeRev(
        likeRevUsernameFromFile: List<LikeRevUsername>,
        likeRevListFromDb: List<LikeRevRow>,
        isEmptyTopic: Boolean
    ): Pair<List<LikeRev>, List<User>> {
        if (isEmptyTopic) return Pair(emptyList(), emptyList())
        if (likeRevUsernameFromFile.isEmpty() && likeRevListFromDb.isEmpty()) return Pair(emptyList(), emptyList())

        val constructedUserList = constructUserList(likeRevUsernameFromFile)
        val mapByUsername = constructedUserList.associateBy { it.username }
        val likeRevListFromFile = likeRevUsernameFromFile.mapNotNull {
            if (it.username !in mapByUsername) {
                LOGGER.error("No mapped uid for username ${it.username}")
                return@mapNotNull null
            }
            if (mapByUsername[it.username]!!.id == null) {
                LOGGER.error("This one has null id ${it.username}")
                System.err.println("")
            }
            LikeRev(
                type = it.type,
                mid = it.mid,
                pid = it.pid,
                value = it.value,
                total = it.total,
                uid = mapByUsername[it.username]!!.getId()
            )
        }

        val merged = mutableSetOf<LikeRev>().apply {
            addAll(likeRevListFromDb)
            addAll(likeRevListFromFile)
        }
        val typeId = merged.first().type
        val mid = merged.first().mid
        val result = mutableSetOf<LikeRev>().apply { addAll(likeRevListFromFile) }


        val fileLikes = likeRevListFromFile.groupBy { Triple(it.pid, it.value, it.uid) }
        val fileLikeKeys = fileLikes.keys
        val dbLikes = likeRevListFromDb.groupBy { Triple(it.pid, it.value, it.uid) }
        val dbLikeKeys = dbLikes.keys
        // val fileLikeKeyCopy = fileLikeKeys.toMutableSet()
        val dbLikeKeysCopy = dbLikeKeys.toMutableSet()
        // If some keys not in file but in db, then update the result to set zero of these types
        dbLikeKeysCopy.removeAll(fileLikeKeys)
        if (dbLikeKeysCopy.isNotEmpty()) {
            LOGGER.info("Some keys not in file but in ba_likes_rev table. Updating them to zero.")
            dbLikeKeysCopy.forEach {
                if ((dbLikes[it]?.size ?: 0) > 0 &&
                    (dbLikes[it]?.firstOrNull()?.total ?: 0) > 0
                ) {
                    LOGGER.info("Zero for type-$typeId, mid-$mid, pid-${it.first}, value-${it.second}")
                }
                result.add(
                    LikeRev(
                        type = typeId,
                        mid = mid,
                        pid = it.first,
                        value = it.second,
                        total = 0,
                        uid = it.third
                    )
                )
            }
        }
        return result.toList() to constructedUserList
    }

    private fun constructUserList(likeRevUsernameFromFile: List<LikeRevUsername>): List<User> {
        val constructedUsers = mutableListOf<User>()
        val userWithoutIdList =
            likeRevUsernameFromFile.map { User(id = null, username = it.username, nickname = it.nickname) }.distinct()

        if (userWithoutIdList.isEmpty()) return emptyList()

        val userRowFromDb = Dao.bgmDao.getUserRowByUsernameList(userWithoutIdList.map { it.username }.distinct())
        val groupByUsername = userRowFromDb.groupBy { it.username }
        userWithoutIdList.forEach {
            if (it.username !in groupByUsername) {
                constructedUsers.add(
                    it.copy(id = it.getId())
                )
            } else if (groupByUsername[it.username]!!.size > 1) {
                val withNegativeId = groupByUsername[it.username]!!
                if (withNegativeId.none { it.id > 0 }) {
                    LOGGER.error("No positive uid found for username ${it.username}")
                    return@forEach
                }
                constructedUsers.add(
                    it.copy(id = withNegativeId.first { it.id > 0 }.id)
                )
            } else {
                constructedUsers.add(
                    it.copy(id = groupByUsername[it.username]!!.first().id)
                )
            }
        }
        return constructedUsers
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


        val fileLikes = likeListFromFile.groupBy { it.pid to it.value }
        val fileLikeKeys = fileLikes.keys
        val dbLikes = likeListFromDb.groupBy { it.pid to it.value }
        val dbLikeKeys = dbLikes.keys
        // val fileLikeKeyCopy = fileLikeKeys.toMutableSet()
        val dbLikeKeysCopy = dbLikeKeys.toMutableSet()
        // If some keys not in file but in db, then update the result to set zero of these types
        dbLikeKeysCopy.removeAll(fileLikeKeys)
        if (dbLikeKeysCopy.isNotEmpty()) {
            LOGGER.info("Some keys not in file but in ba_likes table. Updating them to zero.")
            dbLikeKeysCopy.forEach {
                if ((dbLikes[it]?.size ?: 0) > 0 &&
                    (dbLikes[it]?.firstOrNull()?.total ?: 0) > 0
                ) {
                    LOGGER.info("Zero for type-$typeId, mid-$mid, pid-${it.first}, value-${it.second}")
                }
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
