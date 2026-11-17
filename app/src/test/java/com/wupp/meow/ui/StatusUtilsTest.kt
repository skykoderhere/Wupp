package com.wupp.meow.ui

import com.wupp.meow.data.db.StatusEntity
import com.wupp.meow.ui.components.isOwnStatusSender
import com.wupp.meow.ui.components.isStatusMediaType
import com.wupp.meow.ui.components.partitionOwnAndContactStatuses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusUtilsTest {

    @Test
    fun isOwnStatusSender_matchesDeviceSuffixedAndNormalizedJids() {
        val own = "923001234567:0@s.whatsapp.net"
        assertTrue(isOwnStatusSender("923001234567@s.whatsapp.net", own))
        assertTrue(isOwnStatusSender("923001234567:24@s.whatsapp.net", own))
        assertTrue(isOwnStatusSender("me@s.whatsapp.net", ""))
        assertFalse(isOwnStatusSender("923009999999@s.whatsapp.net", own))
        assertFalse(isOwnStatusSender("923001234567@s.whatsapp.net", ""))
    }

    @Test
    fun partitionOwnAndContactStatuses_keepsOwnStoriesOutOfContactFeed() {
        val ownJid = "6281234567890@s.whatsapp.net"
        val mine = StatusEntity(
            id = "s1",
            senderJid = "6281234567890:15@s.whatsapp.net",
            senderName = "@osyawicane",
            caption = "v2",
            msgType = "VIDEO",
            mimeType = "video/mp4",
            timestamp = 2000L
        )
        val friend = StatusEntity(
            id = "s2",
            senderJid = "6289876543210@s.whatsapp.net",
            senderName = "Alice",
            caption = "Hello",
            msgType = "IMAGE",
            mimeType = "image/jpeg",
            timestamp = 1500L
        )

        val (own, contacts) = partitionOwnAndContactStatuses(listOf(mine, friend), ownJid)
        assertEquals(listOf("s1"), own.map { it.id })
        assertEquals(listOf("s2"), contacts.map { it.id })
    }

    @Test
    fun isStatusMediaType_separatesMediaFromTextStatus() {
        assertTrue(isStatusMediaType("VIDEO", "video/mp4"))
        assertTrue(isStatusMediaType("IMAGE", ""))
        assertTrue(isStatusMediaType("", "video/quicktime"))
        assertFalse(isStatusMediaType("TEXT", ""))
        assertFalse(isStatusMediaType("TEXT", "text/plain"))
    }
}
