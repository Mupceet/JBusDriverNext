# ============================================================
# Kotlin
# ============================================================
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ============================================================
# Gson – reflection-based serialization
# ============================================================
-keepattributes Signature
-keepattributes *Annotation*

# Only keep internal reflection infrastructure (ConstructorConstructor,
# ReflectiveTypeAdapterFactory, UnsafeAllocator, etc.).
# Let R8 shrink unused TypeAdapters (Locale, URL, UUID, BigDecimal, etc.)
-keep class com.google.gson.internal.** { *; }

-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep all domain model classes used in Gson serialization.
# Gson maps JSON keys to field names by reflection – R8 must not rename them.
-keep class me.jbusdriver.modern.domain.model.** { <fields>; }

# Keep the global Gson instance and extension helpers
-keep class me.jbusdriver.modern.core.GsonExtKt { *; }

# ============================================================
# Room
# ============================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ============================================================
# OkHttp
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**

# ============================================================
# Jsoup
# ============================================================
# No keep rules needed — Jsoup doesn't use reflection. All classes are
# reachable via direct method calls from NetClient, HtmlParser, and magnet loaders.
-dontwarn org.jsoup.**

# ============================================================
# Serializable (ILink extends Serializable)
# ============================================================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================
# Hilt / Dagger
# ============================================================
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Hilt entry points
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# ============================================================
# Compose – runtime needs only
# ============================================================
-dontwarn androidx.compose.**
