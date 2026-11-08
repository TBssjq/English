package com.ssjq.english.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun 版本信息解析出全部字段() {
        val body = """
            {
              "latestVersion": "2.2",
              "downloadUrl": "https://example.com/app-release.apk",
              "forceUpdate": true,
              "changelog": "1. 新增复习\n2. 修复崩溃"
            }
        """.trimIndent()
        val version = AppUpdateManager.parseVersion(body)
        requireNotNull(version)
        assertEquals("2.2", version.latestVersion)
        assertEquals("https://example.com/app-release.apk", version.downloadUrl)
        assertTrue(version.forceUpdate)
        assertTrue(version.changelog.contains("新增复习"))
    }

    @Test
    fun 缺少版本号按解析失败处理() {
        assertNull(AppUpdateManager.parseVersion("""{ "downloadUrl": "https://example.com/a.apk" }"""))
    }

    @Test
    fun 服务端返回网页时按解析失败处理而不是崩溃() {
        val html = "<!DOCTYPE html><html><body>502 Bad Gateway</body></html>"
        assertNull(AppUpdateManager.parseVersion(html))
        assertNull(AppUpdateManager.parseNotice(html))
    }

    @Test
    fun 残缺的JSON按解析失败处理而不是崩溃() {
        assertNull(AppUpdateManager.parseVersion("""{ "latestVersion": """))
        assertNull(AppUpdateManager.parseNotice("""{ "title": "公告" """))
    }

    @Test
    fun 根节点不是对象时按解析失败处理() {
        assertNull(AppUpdateManager.parseVersion("[1, 2, 3]"))
        assertNull(AppUpdateManager.parseNotice("\"纯字符串\""))
    }

    @Test
    fun 单条公告结构解析成功() {
        val body = """
            {
              "title": "停机维护",
              "content": "今晚 23:00 维护",
              "is_force": true,
              "btn_text": "查看详情",
              "url": "https://example.com/notice"
            }
        """.trimIndent()
        val notice = AppUpdateManager.parseNotice(body)
        requireNotNull(notice)
        assertEquals("停机维护", notice.title)
        assertTrue(notice.isForce)
        assertEquals("查看详情", notice.btnText)
        assertEquals("https://example.com/notice", notice.url)
    }

    @Test
    fun 更新日志结构被归一化成公告() {
        val body = """
            {
              "latest": "2.2",
              "updated": "2026-11-08",
              "entries": [
                { "version": "2.2", "date": "2026-11-08", "changes": ["新增复习", "修复崩溃"] },
                { "version": "2.1", "date": "2026-09-23", "changes": ["修复闪退"] }
              ]
            }
        """.trimIndent()
        val notice = AppUpdateManager.parseNotice(body)
        requireNotNull(notice)
        assertTrue(notice.title.contains("2.2"))
        assertTrue(notice.content.contains("v2.1"))
        assertTrue(notice.content.contains("修复闪退"))
    }

    @Test
    fun 空对象没有公告() {
        assertNull(AppUpdateManager.parseNotice("{}"))
    }

    @Test
    fun 公告标识随内容变化() {
        val first = NoticeData(title = "公告", content = "第一版内容")
        val same = NoticeData(title = "公告", content = "第一版内容")
        val changed = NoticeData(title = "公告", content = "第二版内容")
        assertEquals(first.seenKey(), same.seenKey())
        assertNotEquals(first.seenKey(), changed.seenKey())
    }

    @Test
    fun 版本比较按数字段进行() {
        assertTrue(AppUpdateManager.isVersionNewer("2.10", "2.9"))
        assertTrue(AppUpdateManager.isVersionNewer("2.2", "2.1"))
        assertTrue(AppUpdateManager.isVersionNewer("2.2.1", "2.2"))
        assertFalse(AppUpdateManager.isVersionNewer("2.2", "2.2.0"))
        assertFalse(AppUpdateManager.isVersionNewer("2.1", "2.2"))
        assertFalse(AppUpdateManager.isVersionNewer("2.2", "2.2"))
    }
}
