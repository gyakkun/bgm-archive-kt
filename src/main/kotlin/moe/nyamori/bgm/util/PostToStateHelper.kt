package moe.nyamori.bgm.util

import moe.nyamori.bgm.model.Post

object PostToStateHelper {
    fun fromPostHtmlToState(postHtml: String): Long {
        if (!postHtml.startsWith("<span class=\"tip")) return Post.STATE_NORMAL
        if (postHtml.contains("内容已被用户删除")) return Post.STATE_DELETED
        if (postHtml.contains("删除了回复")) return Post.STATE_DELETED
        if (postHtml.contains("因违反")) return Post.STATE_ADMIN_DELETED
        return Post.STATE_NORMAL
    }
}