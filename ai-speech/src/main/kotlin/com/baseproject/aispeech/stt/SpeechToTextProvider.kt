package com.baseproject.aispeech.stt

import android.content.Context
import kotlinx.coroutines.flow.Flow

interface SpeechToTextProvider {

    val events: Flow<SttEvent>

    val isListening: Boolean

    suspend fun prepare(context: Context)

    fun startListening()

    fun stopListening()

    fun cancel()

    fun destroy()

    fun isAvailable(): Boolean = true
}
