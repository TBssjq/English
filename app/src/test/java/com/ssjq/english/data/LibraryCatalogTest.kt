package com.ssjq.english.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogTest {

    @Test
    fun 当前词库排在最前() {
        val available = listOf("CET4_1", "CET4_2", "CET4_3", "CET4luan_1", "BEC_2")
        val order = LibraryCatalog.searchOrder("CET4_2", available)
        assertEquals("CET4_2", order.first())
    }

    @Test
    fun 同子分类与同分类优先于其它分类() {
        val available = listOf("CET4_1", "CET4_2", "CET4_3", "CET4luan_1", "BEC_2")
        val order = LibraryCatalog.searchOrder("CET4_2", available)
        val other = order.indexOf("BEC_2")
        assertTrue("同子分类应排在其它分类之前", order.indexOf("CET4_1") < other)
        assertTrue("同子分类应排在其它分类之前", order.indexOf("CET4_3") < other)
        assertTrue("同分类应排在其它分类之前", order.indexOf("CET4luan_1") < other)
    }

    @Test
    fun 结果包含全部可用词库且无重复() {
        val available = listOf("CET4_1", "CET4_2", "BEC_2", "BEC_3")
        val order = LibraryCatalog.searchOrder("CET4_1", available)
        assertEquals(order.size, order.distinct().size)
        available.forEach { assertTrue("缺少 $it", it in order) }
    }

    @Test
    fun 不返回未打包的词库() {
        // CET4_3 属于同一子分类但不在 available 中，不应进入顺序
        val available = listOf("CET4_1", "CET4_2")
        val order = LibraryCatalog.searchOrder("CET4_1", available)
        assertTrue("CET4_3" !in order)
        assertEquals(2, order.size)
    }

    @Test
    fun 带后缀传入同样生效() {
        val available = listOf("CET4_1", "CET4_2")
        val order = LibraryCatalog.searchOrder("CET4_1.db", available)
        assertEquals("CET4_1", order.first())
    }
}
