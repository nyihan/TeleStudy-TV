package com.telestudy.tv

import com.telestudy.tv.core.device.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeviceTypeTest {

    @Test
    fun testDeviceTypeEnumValues() {
        val types = DeviceType.values()
        assertEquals(3, types.size)
        assertNotNull(DeviceType.valueOf("PHONE"))
        assertNotNull(DeviceType.valueOf("TABLET"))
        assertNotNull(DeviceType.valueOf("TV"))
    }
}
