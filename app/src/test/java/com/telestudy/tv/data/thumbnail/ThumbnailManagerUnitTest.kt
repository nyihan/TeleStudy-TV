package com.telestudy.tv.data.thumbnail

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThumbnailManagerUnitTest {

    @Test
    fun testDecodeMinithumbnailWithNullOrEmpty() {
        assertNull(TelegramThumbnailManager.decodeMinithumbnail(null))
        assertNull(TelegramThumbnailManager.decodeMinithumbnail(byteArrayOf()))
    }

    @Test
    fun testDecodeMinithumbnailDoesNotThrow() {
        val result = TelegramThumbnailManager.decodeMinithumbnail(byteArrayOf(1, 2, 3))
        assertTrue(result == null || result is android.graphics.Bitmap)
    }
}
