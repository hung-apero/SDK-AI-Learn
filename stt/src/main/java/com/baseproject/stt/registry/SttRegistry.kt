package com.baseproject.stt.registry

import com.baseproject.stt.core.SttProvider
import com.baseproject.stt.core.SttResult

/**
 * Holds registered [SttProvider]s by [SttProvider.name].
 *
 * Two lookup styles:
 *  - [get] — by name; returns the erased-generic provider for generic call sites.
 *  - [getTyped] — by concrete class; returns the strongly-typed provider so callers
 *    keep access to provider-specific result fields.
 */
class SttRegistry {

    private val byName = mutableMapOf<String, SttProvider<*>>()

    fun <R : SttResult> register(provider: SttProvider<R>): SttRegistry {
        byName[provider.name] = provider
        return this
    }

    fun get(name: String): SttProvider<*> =
        byName[name] ?: error("No STT provider '$name'. Have: ${byName.keys}")

    inline fun <reified P : SttProvider<*>> getTyped(): P =
        all().filterIsInstance<P>().firstOrNull()
            ?: error("No provider of type ${P::class.simpleName}")

    fun all(): Collection<SttProvider<*>> = byName.values
}
