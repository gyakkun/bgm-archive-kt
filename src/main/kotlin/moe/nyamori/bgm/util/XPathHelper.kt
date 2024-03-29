package moe.nyamori.bgm.util

object XPathHelper {


    const val XP_404_MSG: String = "/div[1]/div[2]/div[1]/div/div/p[1]"

    const val XP_GROUP_NAME_ANCHOR: String = "/div[2]/h1/span/a[1]"
    const val XP_GROUP_TOPIC_TITLE_H1_TEXT: String = "/div[2]/h1/text()"

    const val XP_GROUP_TOPIC_TOP_POST_DIV: String = "/div[3]/div[1]"
    const val XP_GROUP_TOPIC_TOP_POST_DATE_SMALL_TEXT: String = "/div[3]/div[1]/div[1]/small/text()"
    const val XP_GROUP_TOPIC_TOP_POST_AVATAR_USERNAME_ANCHOR: String = "/div[3]/div[1]/a"
    const val XP_GROUP_TOPIC_TOP_POST_UID_SPAN: String = "/div[3]/div[1]/a/span"
    const val XP_GROUP_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT: String = "/div[3]/div[1]/div[2]/strong/a/text()"
    const val XP_GROUP_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT: String = "/div[3]/div[1]/div[2]/span/text()"
    const val XP_GROUP_TOPIC_TOP_POST_CONTENT_DIV: String = "/div[3]/div[1]/div[2]/div"

    const val XP_GROUP_TOPIC_FOLLOW_POST_DIV_LIST: String = "/div[3]/div[3]/div"


    const val XP_SUBJECT_NAME_ANCHOR: String = "/div[2]/div[1]/h1/span/a[1]"
    const val XP_SUBJECT_TOPIC_TITLE_H1_TEXT: String = "/div[2]/div[1]/h1/text()"

    const val XP_SUBJECT_TOPIC_TOP_POST_DIV: String = "/div[2]/div[2]/div[1]"
    const val XP_SUBJECT_TOPIC_TOP_POST_DATE_SMALL_TEXT: String = "/div[2]/div[2]/div[1]/div[1]/small/text()"
    const val XP_SUBJECT_TOPIC_TOP_POST_AVATAR_USERNAME_ANCHOR: String = "/div[2]/div[2]/div[1]/a"
    const val XP_SUBJECT_TOPIC_TOP_POST_UID_SPAN: String = "/div[2]/div[2]/div[1]/a/span"
    const val XP_SUBJECT_TOPIC_TOP_POST_USER_NICKNAME_ANCHOR_TEXT: String =
        "/div[2]/div[2]/div[1]/div[2]/strong/a/text()"
    const val XP_SUBJECT_TOPIC_TOP_POST_USER_SIGN_SPAN_TEXT: String = "/div[2]/div[2]/div[1]/div[2]/span/text()"
    const val XP_SUBJECT_TOPIC_TOP_POST_CONTENT_DIV: String = "/div[2]/div[2]/div[1]/div[2]/div"

    const val XP_SUBJECT_TOPIC_FOLLOW_POST_DIV_LIST: String = "/div[2]/div[2]/div[3]/div"


    const val XP_FLOOR_ANCHOR = "div[contains(@class,\"re_info\")]/small/a[contains(@class,\"floor-anchor\")]"
    const val XP_FLOOR_DATE_SMALL_TEXT = "div[contains(@class,\"re_info\")]/small/text()"
    const val XP_FLOOR_USER_NAME_ANCHOR = "a"
    const val XP_FLOOR_USER_STYLE_BG_SPAN = "a/span"
    const val XP_FLOOR_USER_NICKNAME_ANCHOR_TEXT = "div[2]/span[contains(@class,\"userInfo\")]/strong/a/text()"
    const val XP_FLOOR_USER_SIGN_SPAN_TEXT = "div[2]/span[contains(@class,\"userInfo\")]/span/text()"
    const val XP_FLOOR_CONTENT = "div/div/div[contains(@class,\"message\")]"
    const val XP_SUB_FLOOR_DIV_LIST = "div/div/div[contains(@class,\"topic_sub_reply\")]/div"
    const val XP_SUB_FLOOR_USER_NICKNAME_ANCHOR_TEXT = "div[2]/strong[contains(@class,\"userName\")]/a/text()"
    const val XP_SUB_FLOOR_CONTENT = "div[2]/div[contains(@class,\"cmt_sub_content\")]"

    const val XP_TOPIC_SILENT_SPAN = "span[contains(@class, \"badgeStateSilent\")]"
    const val XP_TOPIC_CLOSED_SPAN = "span[contains(@class, \"badgeStateClosed\")]"
    const val XP_TOPIC_REOPEN_SPAN = "span[contains(@class, \"badgeStateReopen\")]"
    const val XP_TOPIC_DISABLED_FLOOR_DATE_SPAN = "//span[contains(@class,\"tip_j\")]"
    const val XP_TOPIC_DISABLED_FLOOR_AUTHOR_ANCHOR = "//a[contains(@class, \"post_author_\")]"
}