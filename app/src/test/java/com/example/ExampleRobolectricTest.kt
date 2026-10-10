package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.ImageMetadata
import com.example.core.model.PassportPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Pocket Photo Tools", appName)
    }

    @Test
    fun `test formatBytes formatting`() {
        assertEquals("500 B", ImageMetadata.formatBytes(500))
        assertEquals("100.0 KB", ImageMetadata.formatBytes(102400))
        assertEquals("2.00 MB", ImageMetadata.formatBytes(2097152))
    }

    @Test
    fun `test passport presets exist`() {
        assertTrue(PassportPresets.list.isNotEmpty())
        val us = PassportPresets.list.first { it.id == "us_passport" }
        assertEquals(600, us.targetPxWidth)
        assertEquals(600, us.targetPxHeight)
    }
}
