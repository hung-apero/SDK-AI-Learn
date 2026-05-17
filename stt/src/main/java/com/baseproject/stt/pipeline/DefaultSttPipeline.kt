package com.baseproject.stt.pipeline

import com.baseproject.stt.core.AudioSource
import com.baseproject.stt.core.SttConfig
import com.baseproject.stt.core.SttError
import com.baseproject.stt.core.SttEvent
import com.baseproject.stt.core.SttProvider
import com.baseproject.stt.core.SttResult
import com.baseproject.stt.core.StreamingSttProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultSttPipeline<R : SttResult>(
    private val provider: SttProvider<R>,
    private val preprocessors: List<AudioPreprocessor> = emptyList(),
    private val postprocessors: List<ResultPostprocessor> = emptyList(),
) : SttPipeline<R> {

    override suspend fun run(audio: AudioSource, config: SttConfig): R {
        val processed = preprocessors.fold(audio) { acc, p -> p.process(acc, config) }
        if (!provider.supports(processed)) {
            throw SttError.UnsupportedSource(processed, provider.name)
        }
        val raw = provider.recognize(processed, config)
        return postprocessors.fold(raw) { acc, p -> p.process(acc, config) }
    }

    override fun runStream(audio: AudioSource, config: SttConfig): Flow<SttEvent<R>> {
        require(provider is StreamingSttProvider<R>) {
            "${provider.name} doesn't support streaming"
        }
        val streaming = provider as StreamingSttProvider<R>
        return streaming.recognizeStream(audio, config).map { event ->
            when (event) {
                is SttEvent.Final -> {
                    val processed = postprocessors.fold(event.result) { r, p -> p.process(r, config) }
                    SttEvent.Final(processed)
                }
                else -> event
            }
        }
    }
}
