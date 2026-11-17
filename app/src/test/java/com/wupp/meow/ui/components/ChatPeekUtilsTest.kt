package com.wupp.meow.ui.components

import com.wupp.meow.data.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPeekUtilsTest {

    private fun msg(
        id: String,
        timestamp: Long = 1_000L,
        isFromMe: Boolean = false,
        senderJid: String = "15551234567@s.whatsapp.net",
        senderName: String = "Alice",
        body: String = "hello",
        msgType: String = "TEXT",
        isDeletedBySender: Boolean = false,
        isViewOnce: Boolean = false,
        durationSec: Int = 0
    ) = MessageEntity(
        id = id,
        chatJid = "15551234567@s.whatsapp.net",
        senderJid = senderJid,
        senderName = senderName,
        isFromMe = isFromMe,
        timestamp = timestamp,
        body = body,
        msgType = msgType,
        isDeletedBySender = isDeletedBySender,
        isViewOnce = isViewOnce,
        durationSec = durationSec
    )

    @Test
    fun orderMessagesForPeek_filtersHiddenStubsCapsAndReversesToChronological() {
        val descending = listOf(
            msg(id = "4", timestamp = 4000L, body = "latest"),
            msg(id = "stub", timestamp = 3500L, body = "", msgType = "UNKNOWN"),
            msg(id = "3", timestamp = 3000L, body = "middle"),
            msg(id = "2", timestamp = 2000L, body = "older", isDeletedBySender = true),
            msg(id = "1", timestamp = 1000L, body = "oldest")
        )

        val ordered = orderMessagesForPeek(descending, maxMessages = 3)
        assertEquals(listOf("2", "3", "4"), ordered.map { it.id })
    }

    @Test
    fun resolvePeekSenderLabel_onlyShowsIncomingGroupSenders() {
        val outgoingGroup = msg(id = "1", isFromMe = true, senderName = "Me")
        val incomingDm = msg(id = "2", isFromMe = false, senderName = "Alice")
        val incomingGroupNamed = msg(id = "3", isFromMe = false, senderName = "Bob")
        val incomingGroupFallback = msg(
            id = "4",
            isFromMe = false,
            senderName = "",
            senderJid = "15559876543:12@s.whatsapp.net"
        )

        assertNull(resolvePeekSenderLabel(outgoingGroup, isGroupChat = true))
        assertNull(resolvePeekSenderLabel(incomingDm, isGroupChat = false))
        assertEquals("Bob", resolvePeekSenderLabel(incomingGroupNamed, isGroupChat = true))
        assertEquals("15559876543", resolvePeekSenderLabel(incomingGroupFallback, isGroupChat = true))
    }

    @Test
    fun resolvePeekMessageText_formatsTextDeletedAndVoiceMessages() {
        assertEquals("Hey there", resolvePeekMessageText(msg(id = "1", body = "  Hey there  ")))
        assertEquals("🚫 Secret", resolvePeekMessageText(msg(id = "2", body = "Secret", isDeletedBySender = true)))
        assertEquals("🎤 Voice message (1:05)", resolvePeekMessageText(msg(id = "3", msgType = "PTT", body = "", durationSec = 65)))
    }
}
