package com.wupp.meow.ui.screens

import com.wupp.meow.data.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDeleteDialogModeTest {

    private fun baseMsg(
        isFromMe: Boolean = false,
        isDeletedBySender: Boolean = false
    ) = MessageEntity(
        id = "m1",
        chatJid = "12345@s.whatsapp.net",
        senderJid = if (isFromMe) "me@s.whatsapp.net" else "12345@s.whatsapp.net",
        senderName = "Sender",
        isFromMe = isFromMe,
        timestamp = 1000L,
        body = "hello",
        msgType = "TEXT",
        isDeletedBySender = isDeletedBySender
    )

    @Test
    fun antiDeletedOrRevokedMessage_promptsPermanentDeviceDelete() {
        assertEquals(
            MessageDeleteDialogMode.PERMANENT_DELETE_CAPTURED,
            resolveMessageDeleteDialogMode(baseMsg(isFromMe = false, isDeletedBySender = true))
        )
        assertEquals(
            MessageDeleteDialogMode.PERMANENT_DELETE_CAPTURED,
            resolveMessageDeleteDialogMode(baseMsg(isFromMe = true, isDeletedBySender = true))
        )
    }

    @Test
    fun outgoingActiveMessage_offersDeleteForEveryoneAndDeleteForMe() {
        assertEquals(
            MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS,
            resolveMessageDeleteDialogMode(baseMsg(isFromMe = true, isDeletedBySender = false))
        )
    }

    @Test
    fun incomingActiveMessage_promptsDeleteForMe() {
        assertEquals(
            MessageDeleteDialogMode.DELETE_FOR_ME_INCOMING,
            resolveMessageDeleteDialogMode(baseMsg(isFromMe = false, isDeletedBySender = false))
        )
    }
}
