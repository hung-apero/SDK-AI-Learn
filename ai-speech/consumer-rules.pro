# Consumer ProGuard rules for the :ai-speech module.
# Spine, kotlinx-coroutines, and Microsoft Speech publish their own keep rules.

# Keep kotlinx-serialization metadata for Gemini DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.baseproject.aispeech.stt.gemini.internal.**$$serializer { *; }
-keepclassmembers class com.baseproject.aispeech.stt.gemini.internal.** {
    *** Companion;
}
-keepclasseswithmembers class com.baseproject.aispeech.stt.gemini.internal.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Public Gemini result types (reflected by callers).
-keep class com.baseproject.aispeech.stt.gemini.GeminiSttResult { *; }
-keep class com.baseproject.aispeech.stt.gemini.OverallScore { *; }
-keep class com.baseproject.aispeech.stt.gemini.WordFeedback { *; }
-keep class com.baseproject.aispeech.stt.gemini.Mistake { *; }
-keep class com.baseproject.aispeech.stt.gemini.NativeRewrites { *; }
