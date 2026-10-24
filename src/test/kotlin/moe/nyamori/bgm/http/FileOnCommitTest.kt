package moe.nyamori.bgm.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileOnCommitTest {

    @Test
    fun testLegalDomains() {
        assertEquals(setOf("bgm.tv", "bangumi.tv", "chii.in"), FileOnCommit.LEGAL_DOMAINS)
        assertEquals("bgm.tv", FileOnCommit.DEFAULT_DOMAIN)
        assertEquals("bgm-domain", FileOnCommit.BGM_DOMAIN_PARAM)
    }

    @Test
    fun testInjectBgmDomainToRelativeUrl() {
        // Plain relative link
        assertEquals(
            "/group/wiki?bgm-domain=bangumi.tv",
            FileOnCommit.injectBgmDomainToRelativeUrl("/group/wiki", "bangumi.tv")
        )

        // Relative link with query parameter using &amp;
        assertEquals(
            "/report?type=7&amp;id=123&amp;bgm-domain=bangumi.tv",
            FileOnCommit.injectBgmDomainToRelativeUrl("/report?type=7&amp;id=123", "bangumi.tv")
        )

        // Relative link with query parameter using &
        assertEquals(
            "/report?type=7&id=123&bgm-domain=bangumi.tv",
            FileOnCommit.injectBgmDomainToRelativeUrl("/report?type=7&id=123", "bangumi.tv")
        )

        // Relative link with hash fragment
        assertEquals(
            "/group/topic/1?bgm-domain=bangumi.tv#post_2",
            FileOnCommit.injectBgmDomainToRelativeUrl("/group/topic/1#post_2", "bangumi.tv")
        )

        // Relative link with both query and hash fragment
        assertEquals(
            "/report?type=7&bgm-domain=bangumi.tv#anchor",
            FileOnCommit.injectBgmDomainToRelativeUrl("/report?type=7#anchor", "bangumi.tv")
        )

        // Protocol-relative URL should NOT be touched
        assertEquals(
            "//lain.bgm.tv/pic/user/1.jpg",
            FileOnCommit.injectBgmDomainToRelativeUrl("//lain.bgm.tv/pic/user/1.jpg", "bangumi.tv")
        )

        // Absolute URL should NOT be touched
        assertEquals(
            "https://bgm.tv/user/1",
            FileOnCommit.injectBgmDomainToRelativeUrl("https://bgm.tv/user/1", "bangumi.tv")
        )

        // Already contains bgm-domain should NOT be duplicated
        assertEquals(
            "/m?bgm-domain=bangumi.tv",
            FileOnCommit.injectBgmDomainToRelativeUrl("/m?bgm-domain=bangumi.tv", "bangumi.tv")
        )

        // JavaScript / anchor links should NOT be touched
        assertEquals(
            "javascript:void(0);",
            FileOnCommit.injectBgmDomainToRelativeUrl("javascript:void(0);", "bangumi.tv")
        )
        assertEquals(
            "#top",
            FileOnCommit.injectBgmDomainToRelativeUrl("#top", "bangumi.tv")
        )
    }

    @Test
    fun testCleanQueryString() {
        assertNull(FileOnCommit.cleanQueryString(null))
        assertNull(FileOnCommit.cleanQueryString(""))
        assertNull(FileOnCommit.cleanQueryString("bgm-domain=bangumi.tv"))
        assertEquals("type=7", FileOnCommit.cleanQueryString("bgm-domain=bangumi.tv&type=7"))
        assertEquals("type=7&id=123", FileOnCommit.cleanQueryString("type=7&id=123&bgm-domain=bangumi.tv"))
        assertEquals("a=1&b=2", FileOnCommit.cleanQueryString("a=1&bgm-domain=bangumi.tv&b=2"))
        assertEquals("foo=bar", FileOnCommit.cleanQueryString("foo=bar"))
    }

    @Test
    fun testHtmlModifierWithBangumiDomain() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
            <link href="/css/mobile.css?r412" rel="stylesheet">
            <script type="text/javascript">
            var SITE_URL = 'https://bgm.tv', CHOBITS_VER = 'r412';
            </script>
            <link href="//lain.bgm.tv/pic/cover/c.jpg" rel="image">
            </head>
            <body>
            <a href="https://bgm.tv/group/topic/123">topic</a>
            <a href="https://chii.in/subject/456">subject</a>
            <a href="https://bangumi.tv/user/789">user</a>
            <a href="/group/wiki">wiki</a>
            <a href="/report?type=7&amp;id=378905">report</a>
            <a href="/group/topic/123#post_456">reply</a>
            <img src="//lain.bgm.tv/pic/user/l/avatar.jpg" />
            <img src="http://lain.bgm.tv/pic/crt/l/char.jpg" />
            <img src="https://lain.bgm.tv/pic/prsn/l/person.jpg" />
            <p>Signature (visit bgm.tv/blog/1)</p>
            </body>
            </html>
        """.trimIndent()

        val modified = FileOnCommit.htmlModifier(sampleHtml, "bangumi.tv")

        // Main domain references must be replaced with bangumi.tv
        assertTrue(modified.contains("var SITE_URL = 'https://bangumi.tv'"))
        assertTrue(modified.contains("href=\"https://bangumi.tv/group/topic/123\""))
        assertTrue(modified.contains("href=\"https://bangumi.tv/subject/456\""))
        assertTrue(modified.contains("href=\"https://bangumi.tv/user/789\""))
        assertTrue(modified.contains("Signature (visit bangumi.tv/blog/1)"))

        // Relative links must be injected with bgm-domain
        assertTrue(modified.contains("href=\"/group/wiki?bgm-domain=bangumi.tv\""))
        assertTrue(modified.contains("href=\"/report?type=7&amp;id=378905&amp;bgm-domain=bangumi.tv\""))
        assertTrue(modified.contains("href=\"/group/topic/123?bgm-domain=bangumi.tv#post_456\""))

        // lain.bgm.tv must NOT be changed
        assertTrue(modified.contains("//lain.bgm.tv/pic/cover/c.jpg"))
        assertTrue(modified.contains("//lain.bgm.tv/pic/user/l/avatar.jpg"))
        assertTrue(modified.contains("http://lain.bgm.tv/pic/crt/l/char.jpg"))
        assertTrue(modified.contains("https://lain.bgm.tv/pic/prsn/l/person.jpg"))
        assertFalse(modified.contains("lain.bangumi.tv"))

        // Footer scripts must use bangumi.tv
        assertTrue(modified.contains("<script src=\"https://bangumi.tv/min/g=ui?r412\" type=\"text/javascript\"></script>"))
        assertTrue(modified.contains("<script src=\"https://bangumi.tv/min/g=mobile?r412\" type=\"text/javascript\"></script>"))
    }

    @Test
    fun testHtmlModifierWithChiiInDomain() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
            <link href="/css/mobile.css?r100" rel="stylesheet">
            <script type="text/javascript">
            var SITE_URL = 'https://bgm.tv', CHOBITS_VER = 'r100';
            </script>
            </head>
            <body>
            <a href="https://bgm.tv/group/topic/1">link</a>
            <a href="/m/timeline">timeline</a>
            <img src="//lain.bgm.tv/pic/user/l/avatar.jpg" />
            </body>
            </html>
        """.trimIndent()

        val modified = FileOnCommit.htmlModifier(sampleHtml, "chii.in")

        assertTrue(modified.contains("var SITE_URL = 'https://chii.in'"))
        assertTrue(modified.contains("href=\"https://chii.in/group/topic/1\""))
        assertTrue(modified.contains("href=\"/m/timeline?bgm-domain=chii.in\""))
        assertTrue(modified.contains("//lain.bgm.tv/pic/user/l/avatar.jpg"))
        assertFalse(modified.contains("lain.chii.in"))
        assertTrue(modified.contains("<script src=\"https://chii.in/min/g=ui?r100\" type=\"text/javascript\"></script>"))
    }

    @Test
    fun testHtmlModifierFallbackOnIllegalDomain() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
            <link href="/css/mobile.css?r100" rel="stylesheet">
            <script type="text/javascript">
            var SITE_URL = 'https://chii.in', CHOBITS_VER = 'r100';
            </script>
            </head>
            <body>
            <a href="https://bangumi.tv/group/topic/1">link</a>
            <a href="/m">mobile</a>
            <img src="//lain.bgm.tv/pic/user/l/avatar.jpg" />
            </body>
            </html>
        """.trimIndent()

        // fast.bgm.tv is a subdomain and should fall back to bgm.tv
        val modifiedFast = FileOnCommit.htmlModifier(sampleHtml, "fast.bgm.tv")
        assertTrue(modifiedFast.contains("var SITE_URL = 'https://bgm.tv'"))
        assertTrue(modifiedFast.contains("href=\"https://bgm.tv/group/topic/1\""))
        assertTrue(modifiedFast.contains("href=\"/m?bgm-domain=bgm.tv\""))
        assertTrue(modifiedFast.contains("<script src=\"https://bgm.tv/min/g=ui?r100\" type=\"text/javascript\"></script>"))
        assertFalse(modifiedFast.contains("fast.bgm.tv"))

        // evil domain fallback
        val modifiedEvil = FileOnCommit.htmlModifier(sampleHtml, "evilbgm.tv")
        assertTrue(modifiedEvil.contains("var SITE_URL = 'https://bgm.tv'"))
        assertTrue(modifiedEvil.contains("href=\"/m?bgm-domain=bgm.tv\""))
        assertFalse(modifiedEvil.contains("evilbgm.tv"))
    }

    @Test
    fun testLinkHandlerContainsReferrerDomainLogic() {
        val html = LinkHandler.html
        assertTrue(html.contains("getReferrerDomain"))
        assertTrue(html.contains("document.referrer"))
        assertTrue(html.contains("\"bgm.tv\""))
        assertTrue(html.contains("\"bangumi.tv\""))
        assertTrue(html.contains("\"chii.in\""))
        assertTrue(html.contains("?bgm-domain="))
        assertFalse(html.contains("?domain="))
    }
}
