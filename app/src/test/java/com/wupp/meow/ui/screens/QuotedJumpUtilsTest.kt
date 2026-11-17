package com.wupp.meow.ui.screens

import com.wupp.meow.data.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class QuotedJumpUtilsTest {

    private fun msg(id: String) = MessageEntity(
        id = id,
        chatJid = "12345@s.whatsapp.net",
        senderJid = "12345@s.whatsapp.net",
        senderName = "Alice",
        isFromMe = false,
        timestamp = 1000L,
        body = "msg $id",
        msgType = "TEXT"
    )

    @Test
    fun findLoadedQuotedItemIndex_findsExactMessageAcrossNullPlaceholders() {
        val list = listOf(msg("m3"), null, msg("m2"), msg("m1"))
        assertEquals(2, findLoadedQuotedItemIndex(list, " m2 "))
        assertEquals(0, findLoadedQuotedItemIndex(list, "m3"))
        assertEquals(-1, findLoadedQuotedItemIndex(list, "missing"))
        assertEquals(-1, findLoadedQuotedItemIndex(list, "   "))
    }
}
