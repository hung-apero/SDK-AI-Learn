package com.baseproject.stt.gemini.internal

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

internal class GeminiAudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File {
        val tmp = File.createTempFile("stt_gemini_", ".m4a", context.cacheDir)
        outputFile = tmp
        recorder = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(32_000)
            setOutputFile(tmp.absolutePath)
            setMaxDuration(60_000)
            prepare()
            start()
        }
        return tmp
    }

    fun stop(): File? {
        val file = outputFile
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            file?.delete()
            release()
            return null
        }
        release()
        return file
    }

    fun cancel() {
        try { recorder?.stop() } catch (_: Throwable) { /* ignore */ }
        release()
        outputFile?.delete()
        outputFile = null
    }

    private fun release() {
        recorder?.release()
        recorder = null
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
}
