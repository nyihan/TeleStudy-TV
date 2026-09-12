package com.telestudy.tv

import com.telestudy.tv.core.tdlib.NativeLoadResult
import com.telestudy.tv.core.tdlib.TDLibNativeLoader
import org.junit.Assert.*
import org.junit.Test

class NativeLoaderTest {

    @Test
    fun testNativeLoadResultStructure() {
        val successResult = NativeLoadResult(isSuccess = true, libraryName = "tdjni")
        assertTrue(successResult.isSuccess)
        assertEquals("tdjni", successResult.libraryName)
        assertNull(successResult.errorMessage)

        val failureResult = NativeLoadResult(
            isSuccess = false,
            libraryName = "tdjni",
            errorMessage = "UnsatisfiedLinkError"
        )
        assertFalse(failureResult.isSuccess)
        assertEquals("tdjni", failureResult.libraryName)
        assertEquals("UnsatisfiedLinkError", failureResult.errorMessage)
    }

    @Test
    fun testNativeLoaderInvocationDoesNotThrow() {
        // Calling loadNativeLibrary on JVM host unit test will safely return a result
        // without crashing the test runner even if host OS lacks the Android ELF shared object.
        val result = TDLibNativeLoader.loadNativeLibrary()
        assertNotNull(result)
        assertEquals("tdjni", result.libraryName)
    }
}
