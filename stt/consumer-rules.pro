# Consumer ProGuard rules for the :stt module.

# Keep kotlinx-serialization metadata for Gemini DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.baseproject.stt.gemini.internal.**$$serializer { *; }
-keepclassmembers class com.baseproject.stt.gemini.internal.** {
    *** Companion;
}
-keepclasseswithmembers class com.baseproject.stt.gemini.internal.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Public Gemini result types (reflected by callers).
-keep class com.baseproject.stt.gemini.GeminiSttResult { *; }
-keep class com.baseproject.stt.gemini.OverallScore { *; }
-keep class com.baseproject.stt.gemini.WordFeedback { *; }
-keep class com.baseproject.stt.gemini.Mistake { *; }
-keep class com.baseproject.stt.gemini.NativeRewrites { *; }
