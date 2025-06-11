package com.example.protectalk.utils

import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.util.Locale

/**
 * Utility class responsible for locating the most recent call recording
 * across common recording directories and supported formats.
 */
object RecordingFinder {

    // == Logging ==
    private const val LOG_TAG: String = "RecordingFinder"

    // == Supported audio extensions ==
    private val SUPPORTED_AUDIO_EXTENSIONS: Set<String> = setOf("amr", "m4a", "wav", "3gp", "mp4")

    // == Directories commonly used for call recordings ==
    @RequiresApi(Build.VERSION_CODES.S)
    private val COMMON_CALL_RECORDING_DIRECTORIES: List<File> = listOf(
        File(Environment.getExternalStorageDirectory(), "CallRecordings"),
        File(Environment.getExternalStorageDirectory(), "Android/data/com.android.soundrecorder/files"),
        File(Environment.getExternalStorageDirectory(), "MIUI/sound_recorder/call_rec"),
        File(Environment.getExternalStorageDirectory(), "MIUI/sound_recorder"),
        File(Environment.getExternalStorageDirectory(), "Recordings"),
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS),
            "Call"
        )
    )

    /**
     * Searches predefined directories and returns the latest modified audio file.
     *
     * @return The most recently modified audio file matching supported formats, or null if none found.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun findLatestRecording(): File? {
        Log.d(LOG_TAG, "🔍 Searching common directories for latest recording file...")

        val latestRecordingFile: File? = COMMON_CALL_RECORDING_DIRECTORIES
            .asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { dir -> dir.walkTopDown() }
            .filter { file ->
                file.isFile &&
                        file.extension.lowercase(Locale.US) in SUPPORTED_AUDIO_EXTENSIONS
            }
            .maxByOrNull { it.lastModified() }

        Log.d(
            LOG_TAG,
            if (latestRecordingFile != null)
                "✅ Latest recording found: ${latestRecordingFile.absolutePath}"
            else
                "⚠️ No valid recording found in known locations."
        )

        return latestRecordingFile
    }
}
