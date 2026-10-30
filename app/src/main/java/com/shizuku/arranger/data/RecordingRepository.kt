package com.shizuku.arranger.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RecordingRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val durationCache = ConcurrentHashMap<String, Long>()

    var folderPath: String
        get() = prefs.getString(KEY_FOLDER, null) ?: DEFAULT_FOLDER
        set(value) = prefs.edit().putString(KEY_FOLDER, value.trim().trimEnd('/')).apply()

    /** Recursively lists audio files (the recorder can be configured to write into sub-folders). */
    fun loadRecordings(): List<Recording> {
        val root = File(folderPath)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            .map { file ->
                val parsed = FileNameParser.parse(file.name)
                Recording(
                    file = file,
                    timestamp = parsed.timestamp ?: file.lastModified(),
                    direction = parsed.direction,
                    phoneNumber = parsed.phoneNumber,
                    sizeBytes = file.length(),
                )
            }
            .sortedByDescending { it.timestamp }
            .toList()
    }

    /** Duration in millis, read from the file's metadata; cached. Call off the main thread. */
    fun durationOf(recording: Recording): Long? {
        val key = recording.file.path + ":" + recording.file.lastModified()
        durationCache[key]?.let { return it }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(recording.file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.also { durationCache[key] = it }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    fun delete(recording: Recording): Boolean = recording.file.delete()

    companion object {
        private const val KEY_FOLDER = "folder"
        val DEFAULT_FOLDER: String =
            File(Environment.getExternalStorageDirectory(), "call_recordings").path
        private val AUDIO_EXTENSIONS = setOf("ogg", "opus", "m4a", "aac", "mp3", "3gp", "amr", "wav", "flac")
    }
}
