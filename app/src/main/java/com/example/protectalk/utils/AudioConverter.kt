package com.example.protectalk.utils

import android.media.*
import android.util.Log
import java.io.*
import java.nio.ByteBuffer

/**
 * Utility object to convert compressed M4A recordings to raw PCM WAV format,
 * preserving sample rate, channel count, and metadata.
 */
object AudioConverter {

    // == Logging ==
    private const val LOG_TAG: String = "AudioConverter"

    // == WAV Format ==
    private const val WAV_BITS_PER_SAMPLE: Int = 16

    // == Timeout Constants ==
    private const val TIMEOUT_US: Long = 10_000

    /**
     * Converts an M4A file to a WAV file using MediaCodec decoding.
     *
     * @param inputFile The M4A file to decode.
     * @param outputWavFile The WAV file to output.
     * @return True if the conversion succeeded, false otherwise.
     */
    fun convertM4aToWav(inputFile: File, outputWavFile: File): Boolean {
        val mediaExtractor = MediaExtractor()
        try {
            mediaExtractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (trackIndex in 0 until mediaExtractor.trackCount) {
                val format: MediaFormat = mediaExtractor.getTrackFormat(trackIndex)
                val mimeType: String? = format.getString(MediaFormat.KEY_MIME)

                if (mimeType != null && mimeType.startsWith("audio/")) {
                    audioTrackIndex = trackIndex
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.e(LOG_TAG, "No audio track found in ${inputFile.name}")
                return false
            }

            mediaExtractor.selectTrack(audioTrackIndex)

            val mimeType: String = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val decoder: MediaCodec = MediaCodec.createDecoderByType(mimeType)

            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            val pcmOutputStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()

            var inputEOSReached = false
            var outputEOSReached = false

            while (!outputEOSReached) {
                if (!inputEOSReached) {
                    val inputBufferId: Int = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferId >= 0) {
                        val inputBuffer: ByteBuffer = decoder.getInputBuffer(inputBufferId)!!
                        val sampleSize: Int = mediaExtractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferId,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEOSReached = true
                        } else {
                            val presentationTimeUs: Long = mediaExtractor.sampleTime
                            decoder.queueInputBuffer(
                                inputBufferId,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                0
                            )
                            mediaExtractor.advance()
                        }
                    }
                }

                val outputBufferId: Int = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferId >= 0) {
                    val outputBuffer: ByteBuffer = decoder.getOutputBuffer(outputBufferId)!!
                    val pcmChunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmChunk)
                    outputBuffer.clear()

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputEOSReached = true
                    }

                    pcmOutputStream.write(pcmChunk)
                    decoder.releaseOutputBuffer(outputBufferId, false)
                }
            }

            decoder.stop()
            decoder.release()
            mediaExtractor.release()

            val finalPcmData: ByteArray = pcmOutputStream.toByteArray()
            writeWavFile(outputWavFile, finalPcmData, audioFormat)

            Log.d(LOG_TAG, "Successfully converted to WAV: ${outputWavFile.absolutePath}")
            return true

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Conversion failed: ${e.message}", e)
            return false
        } finally {
            mediaExtractor.release()
        }
    }

    /**
     * Writes a WAV file header and PCM data to the output file.
     *
     * @param outputFile The destination WAV file.
     * @param pcmData The raw PCM data.
     * @param format MediaFormat containing audio metadata.
     */
    private fun writeWavFile(outputFile: File, pcmData: ByteArray, format: MediaFormat) {
        val sampleRate: Int = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount: Int = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val byteRate: Int = sampleRate * channelCount * WAV_BITS_PER_SAMPLE / 8
        val totalDataLength: Int = pcmData.size + 36
        val audioDataLength: Int = pcmData.size

        val headerStream = ByteArrayOutputStream()
        val headerWriter = DataOutputStream(headerStream)

        headerWriter.writeBytes("RIFF")
        headerWriter.writeIntLE(totalDataLength)
        headerWriter.writeBytes("WAVE")
        headerWriter.writeBytes("fmt ")
        headerWriter.writeIntLE(16) // Sub chunk1Size for PCM
        headerWriter.writeShortLE(1) // AudioFormat PCM = 1
        headerWriter.writeShortLE(channelCount.toShort())
        headerWriter.writeIntLE(sampleRate)
        headerWriter.writeIntLE(byteRate)
        headerWriter.writeShortLE((channelCount * WAV_BITS_PER_SAMPLE / 8).toShort()) // BlockAlign
        headerWriter.writeShortLE(WAV_BITS_PER_SAMPLE.toShort()) // BitsPerSample
        headerWriter.writeBytes("data")
        headerWriter.writeIntLE(audioDataLength)

        FileOutputStream(outputFile).use { wavOut ->
            wavOut.write(headerStream.toByteArray())
            wavOut.write(pcmData)
        }
    }

    /**
     * Writes a 32-bit integer in little-endian order.
     */
    private fun DataOutputStream.writeIntLE(value: Int) {
        write(value shr 0)
        write(value shr 8)
        write(value shr 16)
        write(value shr 24)
    }

    /**
     * Writes a 16-bit short in little-endian order.
     */
    private fun DataOutputStream.writeShortLE(value: Short) {
        write(value.toInt() shr 0)
        write(value.toInt() shr 8)
    }
}
