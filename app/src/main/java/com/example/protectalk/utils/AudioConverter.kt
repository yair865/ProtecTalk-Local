package com.example.protectalk.utils

import android.media.*
import android.util.Log
import java.io.*

object AudioConverter {
    private const val TAG = "AudioConverter"

    fun convertM4aToWav(inputFile: File, outputWavFile: File): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                Log.e(TAG, "No audio track found in ${inputFile.name}")
                return false
            }

            extractor.selectTrack(audioTrackIndex)

            val mimeType = format.getString(MediaFormat.KEY_MIME)!!
            val decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmOutputStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputBufferId = decoder.dequeueInputBuffer(10000)
                    if (inputBufferId >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferId,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            decoder.queueInputBuffer(
                                inputBufferId,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferId >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferId)!!
                    val pcmData = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmData)
                    outputBuffer.clear()
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                    pcmOutputStream.write(pcmData)
                    decoder.releaseOutputBuffer(outputBufferId, false)
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            writeWavHeaderAndData(outputWavFile, pcmOutputStream.toByteArray(), format)

            Log.d(TAG, "Conversion success: ${outputWavFile.absolutePath}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Conversion failed: ${e.message}", e)
            return false
        } finally {
            extractor.release()
        }
    }

    private fun writeWavHeaderAndData(outputFile: File, pcmData: ByteArray, format: MediaFormat) {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val bitsPerSample = 16

        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val totalAudioLen = pcmData.size

        val header = ByteArrayOutputStream()
        val writer = DataOutputStream(header)

        writer.writeBytes("RIFF")
        writer.writeIntLE(totalDataLen)
        writer.writeBytes("WAVE")
        writer.writeBytes("fmt ")
        writer.writeIntLE(16)
        writer.writeShortLE(1)
        writer.writeShortLE(channels.toShort())
        writer.writeIntLE(sampleRate)
        writer.writeIntLE(byteRate)
        writer.writeShortLE((channels * bitsPerSample / 8).toShort())
        writer.writeShortLE(bitsPerSample.toShort())
        writer.writeBytes("data")
        writer.writeIntLE(totalAudioLen)

        FileOutputStream(outputFile).use { wavOut ->
            wavOut.write(header.toByteArray())
            wavOut.write(pcmData)
        }
    }

    private fun DataOutputStream.writeIntLE(value: Int) {
        write(value shr 0)
        write(value shr 8)
        write(value shr 16)
        write(value shr 24)
    }

    private fun DataOutputStream.writeShortLE(value: Short) {
        write(value.toInt() shr 0)
        write(value.toInt() shr 8)
    }
}
