package com.baseproject.stt

import kotlinx.coroutines.flow.Flow

abstract class SpeechToTextProvider {

    abstract val events: Flow<SttEvent>

    abstract val isListening: Boolean

    abstract fun startListening(config: SttConfig = SttConfig())

    abstract fun stopListening()

    abstract fun cancel()

    abstract fun destroy()

    open fun isAvailable(): Boolean = true
}
