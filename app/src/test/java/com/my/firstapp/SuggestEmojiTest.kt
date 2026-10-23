package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestEmojiTest {
    @Test
    fun specificKeywordBeatsGenericSingleCharacter() {
        // 「面包」比通用的「面」更具体，不能被「面」抢走
        assertEquals("🍰", suggestEmoji("面包新语"))
        assertEquals("🍰", suggestEmoji("好利来面包店"))
        // 没有更具体的词时，通用的「面」照样能用
        assertEquals("🍜", suggestEmoji("兰州牛肉面"))
        assertEquals("🍜", suggestEmoji("楼下小面馆"))
    }

    @Test
    fun commonShopsStillMatch() {
        assertEquals("🧋", suggestEmoji("蜜雪冰城"))
        assertEquals("🍔", suggestEmoji("麦当劳"))
        assertEquals("🍲", suggestEmoji("杨国福麻辣烫"))
        assertEquals("🥟", suggestEmoji("沙县小吃"))
        assertEquals("☕", suggestEmoji("瑞幸咖啡"))
    }

    @Test
    fun englishKeywordsAreCaseInsensitive() {
        assertEquals("☕", suggestEmoji("Manner Coffee"))
        assertEquals("🍔", suggestEmoji("KFC"))
    }

    @Test
    fun unrelatedNameGivesNoSuggestion() {
        assertNull(suggestEmoji("今天吃什么"))
        assertNull(suggestEmoji(""))
    }
}
