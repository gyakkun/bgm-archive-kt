package moe.nyamori.bgm.util

import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.git.GitHelper.getPrevProcessedArchiveCommitRef
import moe.nyamori.bgm.model.SpaceType

object TopicListHelper {
    fun getTopicList(spaceType: SpaceType): List<Int> {
        var result = listOf(-1)
        GitHelper.allArchiveRepoListSingleton.forEach { repo ->
            runCatching {
                val topiclistFile = repo.getFileContentAsStringInACommit(
                    repo.getPrevProcessedArchiveCommitRef(),
                    spaceType.name.lowercase() + "/topiclist.txt"
                )
                val tmpResult = topiclistFile.lines().mapNotNull { it.toIntOrNull() }.sorted()
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