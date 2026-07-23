# Quiet Studio
-keep class com.quietstudio.transcription.whisper.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.quietstudio.**$$serializer { *; }
-keepclassmembers class com.quietstudio.** { *** Companion; }
-keepclasseswithmembers class com.quietstudio.** { kotlinx.serialization.KSerializer serializer(...); }
