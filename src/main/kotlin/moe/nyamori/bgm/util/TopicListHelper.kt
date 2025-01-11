package moe.nyamori.bgm.util

import moe.nyamori.bgm.git.GitHelper.getFileContentAsStringInACommit
import moe.nyamori.bgm.git.GitHelper.getPrevProcessedArchiveCommitRef
import moe.nyamori.bgm.git.GitRepoHolder
import moe.nyamori.bgm.model.SpaceType
import moe.nyamori.bgm.util.GitCommitIdHelper.sha1Str

object TopicListHelper {
    fun getTopicList(
        spaceType: SpaceType,
        prevProcessedCommitRevIdFileName: String,
        preferJgit: Boolean,
        gitRepoHolder: GitRepoHolder
    ): List<Int> {
        var result = listOf(-1)
        gitRepoHolder.allArchiveRepoListSingleton.forEach { repo ->
            runCatching {
                val topiclistFile = repo.getFileContentAsStringInACommit(
                    repo.getPrevProcessedArchiveCommitRef(
                        prevProcessedCommitRevIdFileName, preferJgit
                    ).sha1Str(),
                    spaceType.name.lowercase() + "/topiclist.txt",
                    preferJgit
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