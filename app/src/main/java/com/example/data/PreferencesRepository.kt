package com.example.data

import android.content.Context
import com.example.core.model.ImageFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pocket_photo_settings", Context.MODE_PRIVATE)

    private val _defaultFormat = MutableStateFlow(
        try {
            ImageFormat.valueOf(prefs.getString("default_format", ImageFormat.JPEG.name) ?: ImageFormat.JPEG.name)
        } catch (_: Exception) {
            ImageFormat.JPEG
        }
    )
    val defaultFormat: StateFlow<ImageFormat> = _defaultFormat.asStateFlow()

    private val _defaultQuality = MutableStateFlow(prefs.getInt("default_quality", 85))
    val defaultQuality: StateFlow<Int> = _defaultQuality.asStateFlow()

    private val _includeCutGuidelines = MutableStateFlow(prefs.getBoolean("include_cut_guidelines", true))
    val includeCutGuidelines: StateFlow<Boolean> = _includeCutGuidelines.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("dark_theme", false))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun setDefaultFormat(format: ImageFormat) {
        _defaultFormat.value = format
        prefs.edit().putString("default_format", format.name).apply()
    }

    fun setDefaultQuality(quality: Int) {
        _defaultQuality.value = quality
        prefs.edit().putInt("default_quality", quality).apply()
    }

    fun setIncludeCutGuidelines(include: Boolean) {
        _includeCutGuidelines.value = include
        prefs.edit().putBoolean("include_cut_guidelines", include).apply()
    }

    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        prefs.edit().putBoolean("dark_theme", enabled).apply()
    }
}
