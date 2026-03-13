# Keep Moshi model adapters
-keepclassmembers class **JsonAdapter { *; }
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-dontwarn okio.**

# Critical: Keep Kotlin metadata for Moshi reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Critical: Keep Kotlin metadata and parameter names for Moshi reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep all shared data models used by Moshi (preserve constructor parameter names)
-keep class com.nexopos.shared.models.** { *; }
-keepclassmembers class com.nexopos.shared.models.** {
    <init>(...);
    <fields>;
}

# Keep network models
-keep class com.nexopos.erp.core.network.** { *; }
-keepclassmembers class com.nexopos.erp.core.network.** {
    <init>(...);
    <fields>;
}

# Retrofit/OkHttp
-keep class retrofit2.** { *; }
-keepattributes *Annotation*
-dontwarn javax.annotation.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Tink / security-crypto optional HTTP & time libraries (KeysDownloader)
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.http.javanet.**
-dontwarn org.joda.time.**
