# ===================================================================
# Forgeprom Code Studio v2.0 — ProGuard / R8 Rules
# ===================================================================

# 1. Native JNI & LLM Engine bindings
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.forgeprom.codestudio.core.hardware.** { *; }
-keep class com.forgeprom.codestudio.data.ai.** { *; }

# 2. Room Database entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# 3. Serialization
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class com.forgeprom.codestudio.domain.model.** { *; }
