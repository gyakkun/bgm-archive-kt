package moe.nyamori.bgm.util

import moe.nyamori.bgm.model.Post
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostToStateHelperTest {
    @Test
    fun r414DeletedPost() {
        val post = "<span class=\"tip_collapsed\">删除了回复</span>"
        val state = PostToStateHelper.fromPostHtmlToState(post)
        assertEquals(state, Post.STATE_DELETED)
    }

    @Test
    fun r414AdminDeletedPost() {
        val post =
            "<span class=\"tip_collapsed\">因违反「<a class=\"l\" href=\"/about/guideline\">社区指导原则</a>」已被删除</span>"
        val state = PostToStateHelper.fromPostHtmlToState(post)
        assertEquals(state, Post.STATE_ADMIN_DELETED)
    }

    @Test
    fun r415ViolativeCollapsePost() {
        val post =
            "<span class=\"post_content_collapsed\"><span class=\"tip_collapsed\">因含有「<a class=\"l\" href=\"/about/guideline\">社区指导原则</a>」不提倡的内容被折叠</span></span>\n<div class=\"content\">\n 傻逼\n</div>"
        val state = PostToStateHelper.fromPostHtmlToState(post)
        assertEquals(state, Post.STATE_VIOLATIVE)
    }

    @Test
    fun r427GeneralCollapsePost() {
        val post =
            "<span class=\"post_content_collapsed\"><span class=\"tip_collapsed\">回复被折叠</span></span>\n<div class=\"content\">\n 阅。\n</div>"
        val state = PostToStateHelper.fromPostHtmlToState(post)
        assertEquals(state, Post.STATE_COLLAPSED)
    }

    @Test
    fun r427AdminDeletedPost() {
        val post =
            "<span class=\"tip_collapsed\">回复违反「<a href=\"/about/guideline\" class=\"l\">社区指导原则</a>」被删除</span>"
        val state = PostToStateHelper.fromPostHtmlToState(post)
        assertEquals(state, Post.STATE_ADMIN_DELETED)
    }
}