package com.ssjq.english.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSchedulerTest {

    @Test
    fun 不认识时等级归零且次日复习() {
        assertEquals(0, ReviewScheduler.nextLevel(5, 0))
        assertEquals(1L, ReviewScheduler.intervalDays(0, 0))
    }

    @Test
    fun 模糊时等级降一级且间隔缩短() {
        assertEquals(2, ReviewScheduler.nextLevel(3, 1))
        assertEquals(0, ReviewScheduler.nextLevel(0, 1))
        assertEquals(2L, ReviewScheduler.intervalDays(2, 1))
    }

    @Test
    fun 认识时等级递增且间隔按二次幂增长() {
        assertEquals(1, ReviewScheduler.nextLevel(0, 2))
        assertEquals(1L, ReviewScheduler.intervalDays(0, 2))
        assertEquals(2L, ReviewScheduler.intervalDays(1, 2))
        assertEquals(8L, ReviewScheduler.intervalDays(3, 2))
        assertEquals(256L, ReviewScheduler.intervalDays(8, 2))
    }

    @Test
    fun 等级存在上限() {
        assertEquals(8, ReviewScheduler.nextLevel(8, 2))
        assertEquals(256L, ReviewScheduler.intervalDays(8, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun 非法评分直接抛出() {
        ReviewScheduler.nextLevel(1, 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun 非法间隔评分直接抛出() {
        ReviewScheduler.intervalDays(1, -1)
    }
}
