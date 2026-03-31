package com.theveloper.pixelplay.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.model.EnhancedDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("download_preferences", Context.MODE_PRIVATE)
    
    private val gson = Gson()
    
    companion object {
        private const val KEY_DOWNLOAD_STATES = "download_states"
        private const val KEY_DOWNLOAD_QUEUE = "download_queue"
    }

    private val _downloadStates = MutableStateFlow<Map<String, EnhancedDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, EnhancedDownloadState>> = _downloadStates.asStateFlow()

    init {
        loadDownloadStates()
    }

    private fun loadDownloadStates() {
        try {
            val json = sharedPreferences.getString(KEY_DOWNLOAD_STATES, null)
            if (json != null) {
                val type = object : TypeToken<Map<String, EnhancedDownloadState>>() {}.type
                val states = gson.fromJson<Map<String, EnhancedDownloadState>>(json, type) ?: emptyMap()
                _downloadStates.value = states
                Timber.d("Loaded ${states.size} download states from preferences")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading download states from preferences")
        }
    }

    fun saveDownloadState(downloadState: EnhancedDownloadState) {
        try {
            val currentStates = _downloadStates.value.toMutableMap()
            currentStates[downloadState.songId] = downloadState
            _downloadStates.value = currentStates
            
            // Save to SharedPreferences
            val json = gson.toJson(currentStates)
            sharedPreferences.edit()
                .putString(KEY_DOWNLOAD_STATES, json)
                .apply()
            
            Timber.d("Saved download state for song: ${downloadState.songId}")
        } catch (e: Exception) {
            Timber.e(e, "Error saving download state for song: ${downloadState.songId}")
        }
    }

    fun removeDownloadState(songId: String) {
        try {
            val currentStates = _downloadStates.value.toMutableMap()
            currentStates.remove(songId)
            _downloadStates.value = currentStates
            
            // Save to SharedPreferences
            val json = gson.toJson(currentStates)
            sharedPreferences.edit()
                .putString(KEY_DOWNLOAD_STATES, json)
                .apply()
            
            Timber.d("Removed download state for song: $songId")
        } catch (e: Exception) {
            Timber.e(e, "Error removing download state for song: $songId")
        }
    }

    fun getDownloadState(songId: String): EnhancedDownloadState? {
        return _downloadStates.value[songId]
    }

    fun clearAllDownloadStates() {
        try {
            _downloadStates.value = emptyMap()
            sharedPreferences.edit()
                .remove(KEY_DOWNLOAD_STATES)
                .apply()
            Timber.d("Cleared all download states")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing download states")
        }
    }

    fun getCompletedDownloads(): List<EnhancedDownloadState> {
        return _downloadStates.value.values.filter { it.isComplete }
    }

    fun getActiveDownloads(): List<EnhancedDownloadState> {
        return _downloadStates.value.values.filter { it.isDownloading }
    }

    fun getFailedDownloads(): List<EnhancedDownloadState> {
        return _downloadStates.value.values.filter { it.error != null && !it.isComplete }
    }
}
