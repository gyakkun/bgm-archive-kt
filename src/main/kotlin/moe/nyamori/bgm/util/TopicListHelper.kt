package moe.nyamori.bgm.util

import moe.nyamori.bgm.config.toRepoDtoOrThrow
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.git.GitHelper.getPrevProcessedArchiveCommitRef
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.GitCommitIdHelper.sha1Str

object TopicListHelper {
    fun getTopicList(spaceType: SpaceType): List<Int> {
        var result = listOf(-1)
        GitHelper.allArchiveRepoListSingleton.forEach { repo ->
            if (repo.toRepoDtoOrThrow().isStatic) return@forEach
            runCatching {
                val topicListFile = repo.getFileContentAsStringInACommit(
                    repo.getPrevProcessedArchiveCommitRef().sha1Str(),
                    spaceType.name.lowercase() + "/topiclist.txt"
                )
                val tmpResult = topicListFile.lines().mapNotNull { it.toIntOrNull() }.sorted()
                if (tmpResult.max() > result.max()) {
                    result = tmpResult
                }
            }.onFailure {
                // ignore
            }
        }
        return result
    }
}