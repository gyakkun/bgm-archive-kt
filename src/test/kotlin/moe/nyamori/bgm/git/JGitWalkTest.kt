package moe.nyamori.bgm.git

import moe.nyamori.bgm.git.GitHelper.processHistory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Files

@Disabled
class JGitWalkTest {
    private fun guardedTempGitRepo(action: (repo: Repository, ascCommitList: List<RevCommit>) -> Unit) {
        val tempDir = Files.createTempDirectory("jgit-test").toFile()
        val git = Git.init().setDirectory(tempDir).call()

        Files.write(tempDir.toPath().resolve("init-A.txt"), "A".toByteArray())
        git.add().addFilepattern("init-A.txt").call()
        val commitA = git.commit().setMessage("Commit A").call()

        Files.write(tempDir.toPath().resolve("b.txt"), "B".toByteArray())
        git.add().addFilepattern("b.txt").call()
        val commitB = git.commit().setMessage("Commit B").call()

        Files.write(tempDir.toPath().resolve("c.txt"), "C".toByteArray())
        git.add().addFilepattern("c.txt").call()
        val commitC = git.commit().setMessage("Commit C").call()

        Files.write(tempDir.toPath().resolve("d.txt"), "D".toByteArray())
        git.add().addFilepattern("d.txt").call()
        val commitD = git.commit().setMessage("Commit D").call()

        val commitList = listOf(commitA, commitB, commitC, commitD)

        val repo = git.repository
        try {
            action(repo, commitList)
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

    @Test
    fun testProcessHistory() {
        guardedTempGitRepo { repo, ascCommitList ->
            val firstCommit = ascCommitList.first()
            val lastCommit = ascCommitList.last()
            repo.processHistory(
                JGitCommitAdapter(firstCommit).sha1,
                JGitCommitAdapter(lastCommit).sha1,
                true
            ) { slimCommit, changedFileList ->
                System.err.println("processing commit ${slimCommit.shortMessage}")
                System.err.println("changed files ${changedFileList}")
            }
        }
    }

    @Test
    fun testWalk() {
        guardedTempGitRepo { repo, ascCommitList ->
            val (commitA, commitB, commitC, commitD) = ascCommitList
            println("--- Standard Walk (exclusive of bottom) ---")
            val walk = RevWalk(repo)
            walk.markStart(repo.parseCommit(commitD))
            walk.markUninteresting(repo.parseCommit(commitA))
            walk.sort(RevSort.REVERSE, true)

            println("Bottom: ${commitA.name.substring(0, 7)} (Commit A)")
            println("Top:    ${commitD.name.substring(0, 7)} (Commit D)")

            var next = walk.next()
            while(next!=null){
                println("Walk yielded: ${next.name.substring(0, 7)} - ${next.shortMessage}")
                next = walk.next()
            }

//            walk.forEach { commit ->
//                println("Walk yielded: ${commit.name.substring(0, 7)} - ${commit.shortMessage}")
//            }
            walk.dispose()

        }
    }
}
