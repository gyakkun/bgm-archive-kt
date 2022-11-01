package moe.nyamori.bgm.util

object XPathHelper {

    val XP_404_MSG: String = "//div[1]/div[2]/div[1]/div/div/p[1]"

    val XP_GROUP_NAME: String = "//div[2]/h1/span/a[1]"
    val XP_GROUP_TOPIC_TITLE: String = "//div[2]/h1/text()"
    val XP_GROUP_TOPIC_TOP_POST_DIV: String = "//div[3]/div[1]"
    val XP_GROUP_TOPIC_TOP_POST_SMALL_NUM_AND_DATE: String = "//div[3]/div[1]/div[1]/small/text()"
    val XP_GROUP_TOPIC_TOP_POST_A_FOR_USERNAME: String = "//div[3]/div[1]/a"
    val XP_GROUP_TOPIC_TOP_POST_A_FOR_USERID: String = "//div[3]/div[1]/a/span"

}