package com.wupp.meow.ui.components

import com.wupp.meow.core.service.resolveOutboundMediaDispatch
import com.wupp.meow.data.media.OutboundQualityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutboundMediaDispatchTest {

    @Test
    fun resolveOutboundMediaDispatch_keepsVideosAsVideoEvenInOriginalMode() {
        assertEquals(
            "VIDEO" to "hd",
            resolveOutboundMediaDispatch("video/mp4", OutboundQualityMode.ORIGINAL, isVoiceNote = false)
        )
        assertEquals(
            "VIDEO" to "standard",
            resolveOutboundMediaDispatch("video/quicktime", OutboundQualityMode.STANDARD, isVoiceNote = false)
        )
    }

    @Test
    fun resolveOutboundMediaDispatch_preservesDocumentsAndImagesCorrectly() {
        assertEquals(
            "DOCUMENT" to "original",
            resolveOutboundMediaDispatch("image/png", OutboundQualityMode.ORIGINAL, isVoiceNote = false)
        )
        assertEquals(
            "IMAGE" to "hd",
            resolveOutboundMediaDispatch("image/jpeg", OutboundQualityMode.HD, isVoiceNote = false)
        )
        assertEquals(
            "DOCUMENT" to "hd",
            resolveOutboundMediaDispatch("application/pdf", OutboundQualityMode.HD, isVoiceNote = false)
        )
        assertEquals(
            "PTT" to "standard",
            resolveOutboundMediaDispatch("audio/ogg", OutboundQualityMode.STANDARD, isVoiceNote = true)
        )
    }

    @Test
    fun computeVideoDisplayRatio_accountsForRotationAndPixelAspectRatio() {
        // Landscape 1920x1080 -> 16:9
        assertEquals(16f / 9f, computeVideoDisplayRatio(1920, 1080)!!, 0.001f)
        // Rotated portrait 1920x1080 at 90 degrees -> 9:16
        assertEquals(9f / 16f, computeVideoDisplayRatio(1920, 1080, rotationDegrees = 90)!!, 0.001f)
        assertEquals(9f / 16f, computeVideoDisplayRatio(1920, 1080, rotationDegrees = 270)!!, 0.001f)
        // Non-square pixel SAR
        assertEquals(1.5f, computeVideoDisplayRatio(720, 720, rotationDegrees = 0, pixelWidthHeightRatio = 1.5f)!!, 0.001f)
        // Invalid dimensions
        assertNull(computeVideoDisplayRatio(0, 1080))
        assertNull(computeVideoDisplayRatio(1920, -1))
    }
}
