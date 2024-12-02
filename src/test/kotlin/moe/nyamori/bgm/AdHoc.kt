package moe.nyamori.bgm

object AdHoc {
    @JvmStatic
    fun main(args: Array<String>) {
        val faceKeyGifUrlMapping = mapOf(
            "0" to "44",
            "140" to "101",
            "80" to "41",
            "54" to "15",
            "85" to "46",
            "104" to "65",
            "88" to "49",
            "62" to "23",
            "79" to "40",
            "53" to "14",
            "122" to "83",
            "92" to "53",
            "118" to "79",
            "141" to "102",
            "90" to "51",
            "76" to "37",
            "60" to "21",
            "128" to "89",
            "47" to "08",
            "68" to "29",
            "137" to "98",
            "132" to "93"
        )
        val csv = """
            140,212663
            54,154055
            141,24282
            79,18443
            0,18191
            118,17749
            104,15003
            90,12783
            122,11854
            80,9078
        """.trimIndent()
        csv.lines().forEachIndexed { idx, str ->
            val split = str.split(",")
            val gifUrl = "https://bgm.tv/img/smiles/tv/${faceKeyGifUrlMapping[split[0]]!!}.gif"
            val out = "${idx+1}. [img=21x21]$gifUrl[/img] ${split[1]}"
            System.err.println(out)
        }
    }
}