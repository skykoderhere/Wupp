package com.wupp.meow.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatEmojiTextUtilsTest {

    @Test
    fun insertTextAtSelection_insertsAtCursorInMiddle() {
        val initial = TextFieldValue(text = "Hello world", selection = TextRange(5))
        val updated = insertTextAtSelection(initial, " 😄")
        assertEquals("Hello 😄 world", updated.text)
        assertEquals(TextRange(8), updated.selection)
    }

    @Test
    fun insertTextAtSelection_replacesSelectedRange() {
        val initial = TextFieldValue(text = "Hello bad world", selection = TextRange(6, 9))
        val updated = insertTextAtSelection(initial, "🔥")
        assertEquals("Hello 🔥 world", updated.text)
        assertEquals(TextRange(6 + "🔥".length), updated.selection)
    }

    @Test
    fun deleteBeforeSelection_deletesWholeSurrogateEmoji() {
        val initial = TextFieldValue(text = "Hi 😄!", selection = TextFieldValue("Hi 😄").text.length.let(::TextRange))
        val updated = deleteBeforeSelection(initial)
        assertEquals("Hi !", updated.text)
        assertEquals(TextRange(3), updated.selection)
    }

    @Test
    fun deleteBeforeSelection_deletesWholeSkinToneAndZwjSequence() {
        val skinTone = "\uD83D\uDC4D\uD83C\uDFFD" // 👍🏽
        val withSkinTone = TextFieldValue(text = "yo $skinTone", selection = TextRange(3 + skinTone.length))
        val afterSkinToneDelete = deleteBeforeSelection(withSkinTone)
        assertEquals("yo ", afterSkinToneDelete.text)
        assertEquals(TextRange(3), afterSkinToneDelete.selection)

        val zwjFamily = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67" // 👨‍👩‍👧
        val withZwj = TextFieldValue(text = zwjFamily, selection = TextRange(zwjFamily.length))
        val afterZwjDelete = deleteBeforeSelection(withZwj)
        assertEquals("", afterZwjDelete.text)
        assertEquals(TextRange(0), afterZwjDelete.selection)
    }

    @Test
    fun deleteBeforeSelection_handlesStartCursorAndSelection() {
        val atStart = TextFieldValue(text = "abc", selection = TextRange(0))
        assertEquals(atStart, deleteBeforeSelection(atStart))

        val reversedSelection = TextFieldValue(text = "abcdef", selection = TextRange(4, 1))
        val deleted = deleteBeforeSelection(reversedSelection)
        assertEquals("aef", deleted.text)
        assertEquals(TextRange(1), deleted.selection)
    }
}
