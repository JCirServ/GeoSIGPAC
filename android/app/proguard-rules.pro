# === SIGPAC ESPECÍFICO ===

# Mantener modelos de datos (Gson + Serialización)
-keep class com.geosigpac.cirserv.model.** { *; }
-keepclassmembers class com.geosigpac.cirserv.model.** { *; }

# MapLibre
-keep class org.maplibre.** { *; }
-keep class com.mapbox.** { *; }
-dontwarn org.maplibre.**
-dontwarn com.mapbox.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# ACRA (si lo usas)
-keep class org.acra.** { *; }

# Preservar anotaciones y firmas
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod