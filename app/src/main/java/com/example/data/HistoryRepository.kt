package com.example.data

import android.content.Context
import android.net.Uri
import com.example.core.model.ImageFormat
import com.example.core.model.ProcessedResult
import com.example.core.model.ToolType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class HistoryRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("pocket_photo_history", Context.MODE_PRIVATE)
    private val _history = MutableStateFlow<List<ProcessedResult>>(emptyList())
    val history: StateFlow<List<ProcessedResult>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val raw = prefs.getString("saved_jobs", null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ProcessedResult>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val toolName = obj.optString("tool", ToolType.COMPRESS.name)
                val formatName = obj.optString("format", ImageFormat.JPEG.name)
                val item = ProcessedResult(
                    tool = try { ToolType.valueOf(toolName) } catch (_: Exception) { ToolType.COMPRESS },
                    originalUri = Uri.parse(obj.getString("originalUri")),
                    originalSizeBytes = obj.getLong("originalSizeBytes"),
                    originalWidth = obj.getInt("originalWidth"),
                    originalHeight = obj.getInt("originalHeight"),
                    resultUri = Uri.parse(obj.getString("resultUri")),
                    resultSizeBytes = obj.getLong("resultSizeBytes"),
                    resultWidth = obj.getInt("resultWidth"),
                    resultHeight = obj.getInt("resultHeight"),
                    format = try { ImageFormat.valueOf(formatName) } catch (_: Exception) { ImageFormat.JPEG },
                    extraDetails = obj.optString("extraDetails", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
                list.add(item)
            }
            _history.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addResult(result: ProcessedResult) {
        val updated = listOf(result) + _history.value.filter { it.resultUri != result.resultUri }.take(25)
        _history.value = updated
        saveToDisk(updated)
    }

    fun deleteItem(result: ProcessedResult) {
        val updated = _history.value.filter { it.resultUri != result.resultUri }
        _history.value = updated
        saveToDisk(updated)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("saved_jobs").apply()
    }

    private fun saveToDisk(list: List<ProcessedResult>) {
        try {
            val arr = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("tool", item.tool.name)
                    put("originalUri", item.originalUri.toString())
                    put("originalSizeBytes", item.originalSizeBytes)
                    put("originalWidth", item.originalWidth)
                    put("originalHeight", item.originalHeight)
                    put("resultUri", item.resultUri.toString())
                    put("resultSizeBytes", item.resultSizeBytes)
                    put("resultWidth", item.resultWidth)
                    put("resultHeight", item.resultHeight)
                    put("format", item.format.name)
                    put("extraDetails", item.extraDetails)
                    put("timestamp", item.timestamp)
                }
                arr.put(obj)
            }
            prefs.edit().putString("saved_jobs", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
