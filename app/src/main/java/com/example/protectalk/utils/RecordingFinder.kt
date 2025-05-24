package com.example.protectalk.utils

import android.os.Environment
import java.io.File
import java.util.Locale

object RecordingFinder {
    private val dirs = listOf(
        File(Environment.getExternalStorageDirectory(), "CallRecordings"),
        File(Environment.getExternalStorageDirectory(), "Android/data/com.android.soundrecorder/files"),
        File(Environment.getExternalStorageDirectory(), "MIUI/sound_recorder/call_rec"),
        File(Environment.getExternalStorageDirectory(), "MIUI/sound_recorder"),
        File(Environment.getExternalStorageDirectory(), "Recordings")
    )
    private val exts = setOf("amr","m4a","wav","3gp","mp4")

    fun findLatestRecording(): File? =
        dirs.asSequence()
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension.lowercase(Locale.US) in exts }
            .maxByOrNull { it.lastModified() }
}