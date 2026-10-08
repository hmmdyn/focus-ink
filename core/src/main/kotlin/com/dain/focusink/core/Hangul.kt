package com.dain.focusink.core

/** 앞 낱말의 받침에 맞춰 조사를 고른다. 예: josa("휴대폰", "이에요", "예요") → "휴대폰이에요" */
object Hangul {
    fun hasBatchim(word: String): Boolean {
        val c = word.trim().lastOrNull { it.isLetterOrDigit() } ?: return false
        if (c in '가'..'힣') return (c.code - 0xAC00) % 28 != 0
        // 숫자·영문은 읽는 소리 기준으로 대략 판단한다
        return c in "0136781lmnLMN"
    }

    fun josa(word: String, withBatchim: String, withoutBatchim: String): String =
        word + if (hasBatchim(word)) withBatchim else withoutBatchim
}
