package com.example.protectalk.managers

import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException

class CallRecorderManager(
    private val context: Context,
    private val chunkCallback: (File) -> Unit,
    private val errorCallback: (Exception) -> Unit
) {
    private var recorder: MediaRecorder? = null
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "CallRecorderManager"
    private var chunkIndex = 0

    // record duration per chunk in ms
    private val CHUNK_DURATION_MS = 12_000L

    fun start() {
        Log.d(TAG, "Starting internal call recorder...")
        startSegment()
    }

    fun stop() {
        Log.d(TAG, "Stopping internal call recorder...")
        handler.removeCallbacksAndMessages(null)
        recorder?.run {
            try { stop() } catch (_: Exception) {}
            release()
        }
        recorder = null
    }

    private fun startSegment() {
        val file = newOutputFile()
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
        }
        try {
            recorder?.prepare()
            recorder?.start()
            Log.d(TAG, "Recording chunk #$chunkIndex → ${file.name}")
        } catch (e: IOException) {
            Log.e(TAG, "Prepare/start failed: ${e.localizedMessage}")
            errorCallback(e)
            return
        }
        handler.postDelayed({ stopSegment(file) }, CHUNK_DURATION_MS)
    }

    private fun stopSegment(file: File) {
        recorder?.run {
            try { stop() } catch (_: Exception) {}
            release()
        }
        recorder = null
        Log.d(TAG, "Finished chunk #$chunkIndex → ${file.name}")
        chunkCallback(file)
        chunkIndex++
        startSegment()
    }

    private fun newOutputFile(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "calls")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "call_chunk_${System.currentTimeMillis()}_$chunkIndex.mp4")
    }
}