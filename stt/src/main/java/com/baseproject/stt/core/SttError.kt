package com.baseproject.stt.core

/**
 * Typed errors a provider can surface. Callers (and the fallback chain) should
 * pattern-match to decide whether to retry, switch provider, or stop.
 */
sealed class SttError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class PermissionDenied(val permission: String) :
        SttError("Permission denied: $permission")

    class UnsupportedSource(source: AudioSource, provider: String) :
        SttError("$provider does not support $source")

    class Network(cause: Throwable) : SttError("Network error", cause)

    class Auth(message: String) : SttError(message)

    class Quota(message: String) : SttError(message)

    class Timeout(ms: Long) : SttError("Timed out after $ms ms")

    class ProviderFailure(
        provider: String,
        cause: Throwable? = null,
        message: String? = null,
    ) : SttError(message ?: "$provider failed", cause)
}
