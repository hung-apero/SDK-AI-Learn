package com.baseproject.stt.registry

import com.baseproject.stt.core.AudioSource
import com.baseproject.stt.core.SttConfig
import com.baseproject.stt.core.SttError
import com.baseproject.stt.core.SttEvent
import com.baseproject.stt.core.SttProvider
import com.baseproject.stt.core.SttResult
import com.baseproject.stt.core.StreamingSttProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Tries providers in order. Only falls through on *transient* errors
 * ([SttError.Network], [SttError.Quota], [SttError.Timeout]) — auth/permission/unsupported
 * are propagated immediately since retrying them would just fail again.
 */
class FallbackProvider(
    private val chain: List<SttProvider<SttResult>>,
) : StreamingSttProvider<SttResult> {

    override val name: String = "fallback"

    override fun supports(source: AudioSource): Boolean = chain.any { it.supports(source) }

    override suspend fun recognize(audio: AudioSource, config: SttConfig): SttResult {
        val errors = mutableListOf<Throwable>()
        for (p in chain) {
            if (!p.supports(audio)) continue
            try {
                return p.recognize(audio, config)
            } catch (e: SttError.Network) {
                errors += e
            } catch (e: SttError.Quota) {
                errors += e
            } catch (e: SttError.Timeout) {
                errors += e
            }
            // Non-transient (Auth/PermissionDenied/UnsupportedSource/ProviderFailure): rethrow
        }
        throw SttError.ProviderFailure(
            provider = name,
            cause = errors.firstOrNull(),
            message = "All providers failed: ${errors.map { it.message }}",
        )
    }

    override fun recognizeStream(
        audio: AudioSource,
        config: SttConfig,
    ): Flow<SttEvent<SttResult>> = flow {
        for (p in chain) {
            if (p is StreamingSttProvider<*> && p.supports(audio)) {
                @Suppress("UNCHECKED_CAST")
                emitAll((p as StreamingSttProvider<SttResult>).recognizeStream(audio, config))
                return@flow
            }
        }
        emit(SttEvent.Error(SttError.UnsupportedSource(audio, name)))
    }
}
