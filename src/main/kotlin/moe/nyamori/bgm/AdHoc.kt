package moe.nyamori.bgm

import moe.nyamori.bgm.db.DSProvider
import java.io.File
import java.util.regex.Pattern
import kotlin.text.Charsets.UTF_8

fun main0() {
    val path = "E:\\[ToBak]\\Desktop_Win10\\240824_Black_Myth_unpack\\videocap\\txt"
    val sizeMap = mutableMapOf<String, MutableSet<String>>()
    val file = File(path)
    file.walkTopDown().filter { it.isFile }.forEach {
        val fileList = mutableListOf<String>()
        val sizeList = mutableListOf<String>()
        it.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            if (trimmed.startsWith("b1") && trimmed.endsWith("wem")) {
                sizeMap.putIfAbsent(trimmed, mutableSetOf())
                fileList.add(trimmed)
            } else if (trimmed.first().isDigit() && trimmed.last() == 'B') {
                sizeList.add(trimmed)
            }
        }
        if (fileList.size != sizeList.size) {
            // System.err.println("size not match: file = ${fileList.size} size = ${sizeList.size} - ${it.absolutePath} ")
        } else {
            for (i in fileList.indices) {
                val f = fileList[i]
                val s = sizeList[i]
                sizeMap[f]!!.add(s.replace("\\s+".toRegex(), ""))
            }
        }
    }
    // sizeMap.filter { it.value.size > 1 }.forEach { System.err.println(it) }
    val sorted = sizeMap.mapValues { it.value.first() }.filter { it.value.endsWith("MB") }
        .mapValues { it.value.replace("MB".toRegex(), "").toFloat() }
        .entries.sortedBy { -it.value }
        .filter { "Chinese" !in it.key && ("English" !in it.key) }
        .map { it.key.split("/").last().toCharArray().filter { it.isDigit() }.joinToString(separator = "") }
        .joinToString(separator = "|", prefix = "(", postfix = ")")
    System.err.println(sorted)
}


fun main() {
    DSProvider.ds.connection.use { conn ->
        conn.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA main.journal_mode;").use { rs ->
                while (rs.next()) {
                    val res = rs.getString(1)
                    System.err.println(res)
                }
            }
        }
    }
}

fun main1() {

    val f = File("E:\\[ToBak]\\Desktop_Win10\\240302_mce_gup_f4.new.txt")
    var lines = f.readLines(UTF_8)
    // System.err.println(lines)
    var sb = StringBuffer()
    var res = mutableListOf<String>()
    for (i in lines.indices) {
        if (lines[i].contains("curl")) {
            res.add(sb.toString())
            sb = StringBuffer()
        }
        if (lines[i].contains("Range:")) continue
        sb.append(lines[i])
    }
    res.add(sb.toString())


    var i = res.iterator()
    while (i.hasNext()) {
        var l = i.next()
        if (!l.contains(".m4s")) {
            i.remove()
            continue
        }
        if (l.contains(":8082")) {
            i.remove()
            continue
        }
        if (l.contains("https://data.bilibili.com")) {
            i.remove()
            continue
        }
    }
    val pat = Pattern.compile("^.*/(?<fn>[0-9]+-[0-9]-[0-9]+\\.m4s).*$")
    val urlMap = mutableMapOf<String, String>()
    for (i in res) {
        val mat = pat.matcher(i)
        if (mat.matches()) {
            // System.err.println(mat.group("fn"))
            val fn = mat.group("fn")
            urlMap.putIfAbsent(fn, i)
        }
    }

//    System.err.println(urlMap)

    for ((k, i) in urlMap) {
        var j = i.replace("\\\\  -H 'range: bytes=\\d+-\\d+' \\\\".toRegex(), " \\ ")
        j = j.replace(" +\\\\ +".toRegex(), " ")
        j = j.removeSuffix(";")
        // System.err.println(j + " -o $k")
    }
    val pairMap = mutableMapOf<String, MutableList<String>>()
    for (k in urlMap.keys) {
        val id = k.split("-")[0]
        pairMap.putIfAbsent(id, mutableListOf())
        pairMap[id]!!.add(k)
    }
    var counter = 0
    var conc = ""
    for ((k, v) in pairMap) {
        if (v.size != 2) continue
        // System.err.println("ffmpeg -i ${v[0]} -i ${v[1]} -acodec copy -vcodec copy ${++counter}.mp4")
        // ffmpeg -i concat:"1.mpg|2.mpg|3.mpg" -c copy output.mp4
        conc += "${++counter}.mp4" + "|"
    }
    conc.removeSuffix("|")
    System.err.println("ffmpeg -i concat:\"$conc\" -c copy output.mp4")
}