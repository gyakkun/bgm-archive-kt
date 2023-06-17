package moe.nyamori.bgm.util

import moe.nyamori.bgm.model.Post

object PostToStateHelper {
    fun fromPostHtmlToState(postHtml: String): Long {
        if (postHtml.startsWith("<span class=\"tip")) {
            if (postHtml.contains("内容已被用户删除")) return Post.STATE_DELETED
            if (postHtml.contains("删除了回复")) return Post.STATE_DELETED
            if (postHtml.contains("违反") && postHtml.contains("被删除")) return Post.STATE_ADMIN_DELETED
        } else if (postHtml.startsWith("<span class=\"post_content_collapsed\">")) {
            if (postHtml.contains("因含有") && postHtml.contains("被折叠")) return Post.STATE_VIOLATIVE
            if (postHtml.contains("回复被折叠")) return Post.STATE_COLLAPSED
        }
        return Post.STATE_NORMAL
    }
}