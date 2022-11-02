package moe.nyamori.bgm.util

object XPathHelper {

    val XP_404_MSG: String = "//div[1]/div[2]/div[1]/div/div/p[1]"

    val XP_GROUP_NAME_ANCHOR: String = "//div[2]/h1/span/a[1]"
    val XP_GROUP_TOPIC_TITLE_H1_TEXT: String = "//div[2]/h1/text()"

    val XP_GROUP_TOPIC_TOP_POST_DIV: String = "//div[3]/div[1]"
    val XP_GROUP_TOPIC_TOP_POST_SMALL_TEXT: String = "//div[3]/div[1]/div[1]/small/text()"
    val XP_GROUP_TOPIC_TOP_POST_USERNAME_ANCHOR: String = "//div[3]/div[1]/a"
    val XP_GROUP_TOPIC_TOP_POST_UID_SPAN: String = "//div[3]/div[1]/a/span"
    val XP_GROUP_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT: String = "//div[3]/div[1]/div[2]/strong/a/text()"
    val XP_GROUP_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT: String = "//div[3]/div[1]/div[2]/span/text()"
    val XP_GROUP_TOPIC_TOP_POST_CONTENT_DIV: String = "//div[3]/div[1]/div[2]/div"

    val XP_GROUP_TOPIC_FOLLOW_POST_DIV_LIST: String = "//div[3]/div[3]/div"
}