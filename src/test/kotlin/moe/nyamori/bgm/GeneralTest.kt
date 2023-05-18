package moe.nyamori.bgm

import com.vladsch.flexmark.util.misc.FileUtil
import moe.nyamori.bgm.git.GitHelper
import moe.nyamori.bgm.model.*
import java.io.File
import java.io.IOException
import java.util.*

class GeneralTest {
    companion object {
        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            System.err.println("hello world")
            //val oldMask = File("E:\\[ToBak]\\Desktop_Win10\\old_sub_hid_mask.txt")
            //val newMask = File("E:\\[ToBak]\\Desktop_Win10\\new_sub_hid_mask.txt")
            //val fromFileToBs = fun(file: File): BitSet {
            //    val longArr = FileUtil.getFileContent(file)!!.lines().mapNotNull { it.toLongOrNull() }.toLongArray()
            //    return BitSet.valueOf(longArr)
            //}
            //val oldBs = fromFileToBs(oldMask)
            //val newBs = fromFileToBs(newMask)
            //val newClone = newBs.clone() as BitSet
//
            //newBs.xor(oldBs)
            //newBs.and(newClone)
//
            //System.err.println("Not masked in old but masked in new ${newBs}")
        }
    }
}