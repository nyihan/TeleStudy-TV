package com.telestudy.tv.core.util

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class QrGeneratorTest {

    @Test
    fun testGenerateQrBitmap_validContentGeneratesBitmap() {
        val payload = "tg://login?token=AQCBabcdef123456789"
        val bitmap = QrGenerator.generateQrBitmap(payload, sizePx = 256)

        assertNotNull("Generated QR bitmap must not be null for valid payload", bitmap)
        assertEquals(256, bitmap!!.width)
        assertEquals(256, bitmap.height)
    }

    @Test
    fun testGenerateQrBitmap_emptyContentReturnsNull() {
        val bitmap = QrGenerator.generateQrBitmap("", sizePx = 256)
        assertNull("Empty content should return null", bitmap)

        val whitespaceBitmap = QrGenerator.generateQrBitmap("   ", sizePx = 256)
        assertNull("Whitespace content should return null", whitespaceBitmap)
    }
}
