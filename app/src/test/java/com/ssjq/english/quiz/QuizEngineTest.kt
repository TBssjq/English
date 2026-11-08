package com.ssjq.english.quiz

import com.ssjq.english.data.TransItem
import com.ssjq.english.data.WordDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEngineTest {

    private fun word(id: String, head: String, cn: String) = WordDetail(
        wordId = id,
        headWord = head,
        usPhone = null,
        ukPhone = null,
        star = 0,
        remMethod = null,
        trans = listOf(TransItem(null, cn)),
        phrases = emptyList(),
        sentences = emptyList(),
        relWords = emptyList(),
        synos = emptyList(),
        antos = emptyList(),
    )

    @Test
    fun 拼写完全一致返回正确() {
        assertTrue(QuizEngine.checkSpelling("abandon", "abandon") is AnswerResult.Correct)
    }

    @Test
    fun 单字母差异返回接近正确() {
        assertTrue(QuizEngine.checkSpelling("abandone", "abandon") is AnswerResult.AlmostCorrect)
    }

    @Test
    fun 差异过大返回错误并带字母差异() {
        val result = QuizEngine.checkSpelling("xyz", "abandon")
        assertTrue(result is AnswerResult.Wrong)
        assertTrue((result as AnswerResult.Wrong).diffInfo.isNotEmpty())
    }

    @Test
    fun 多写一个字母只标出多写的那个() {
        val result = QuizEngine.checkSpelling("dissipatte", "dissipate")
        assertTrue(result is AnswerResult.AlmostCorrect)
        val wrongChars = (result as AnswerResult.AlmostCorrect).diffInfo.filter { !it.isCorrect }
        assertEquals(1, wrongChars.size)
    }

    @Test
    fun 写错一个字母只标出该字母() {
        val result = QuizEngine.checkSpelling("dissipata", "dissipate")
        assertTrue(result is AnswerResult.AlmostCorrect)
        val wrongChars = (result as AnswerResult.AlmostCorrect).diffInfo.filter { !it.isCorrect }
        assertEquals(1, wrongChars.size)
        assertEquals('a', wrongChars[0].char)
    }

    @Test
    fun 空词库不生成题目() {
        assertTrue(QuizEngine.generateQuestions(emptyList(), 20).isEmpty())
    }

    @Test
    fun 固定模式生成指定题数() {
        val words = (1..10).map { word("w$it", "word$it", "释义$it") }
        val questions = QuizEngine.generateQuestions(words, 5, QuizMode.Spelling, seed = 42)
        assertEquals(5, questions.size)
        assertTrue(questions.all { it.mode == QuizMode.Spelling })
    }

    @Test
    fun 词数不足时循环填充且不会连续三题同模式() {
        val words = listOf(word("w1", "word1", "释义1"))
        val questions = QuizEngine.generateQuestions(words, 6, null, seed = 7)
        assertEquals(6, questions.size)
        for (i in 2 until questions.size) {
            val a = questions[i - 2].mode
            val b = questions[i - 1].mode
            val c = questions[i].mode
            assertFalse("第 $i 题出现连续三题相同模式", a == b && b == c)
        }
    }
}
