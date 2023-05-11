package moe.nyamori.bgm

import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.*
import java.io.IOException
import java.util.*

class GeneralTest {
    companion object {
        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            // val commit = GitHelper.jsonRepoSingleton.getRevCommitById("sjfklasjfklsajfldkjsalk")
            val commit = GitHelper.getPrevPersistedJsonCommitRef()
            System.err.println(commit)
        }
    }
}