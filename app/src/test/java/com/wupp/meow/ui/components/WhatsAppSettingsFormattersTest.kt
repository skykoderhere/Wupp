package com.wupp.meow.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppSettingsFormattersTest {

    @Test
    fun formatBytesCompact_formatsUnitsCleanly() {
        assertEquals("0 B", formatBytesCompact(-5))
        assertEquals("512 B", formatBytesCompact(512))
        assertEquals("1.5 KB", formatBytesCompact(1536))
        assertEquals("2.0 MB", formatBytesCompact(2L * 1024L * 1024L))
    }

    @Test
    fun formatPrivacyValueLabel_mapsServerValues() {
        assertEquals("Everyone", formatPrivacyValueLabel("all"))
        assertEquals("My contacts", formatPrivacyValueLabel("contacts"))
        assertEquals("Same as last seen", formatPrivacyValueLabel("match_last_seen"))
        assertEquals("Silence unknown callers", formatPrivacyValueLabel("known"))
        assertEquals("Nobody", formatPrivacyValueLabel("none"))
    }

    @Test
    fun formatDisappearingTimerLabel_andFontScaleLabel_formatPresets() {
        assertEquals("Off", formatDisappearingTimerLabel(0))
        assertEquals("24 hours", formatDisappearingTimerLabel(86_400))
        assertEquals("7 days", formatDisappearingTimerLabel(604_800))
        assertEquals("90 days", formatDisappearingTimerLabel(7_776_000))

        assertEquals("Small", formatFontScaleLabel(0.85f))
        assertEquals("Medium", formatFontScaleLabel(1.0f))
        assertEquals("Large", formatFontScaleLabel(1.15f))
    }
}
