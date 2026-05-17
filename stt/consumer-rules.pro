# Consumer ProGuard rules for the :stt module
# Apps that include this module will inherit these rules.

# Azure Speech SDK relies on reflection for native bindings — keep its classes.
-keep class com.microsoft.cognitiveservices.speech.** { *; }
-keepclassmembers class com.microsoft.cognitiveservices.speech.** { *; }

# OkHttp / Okio platform shims
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
