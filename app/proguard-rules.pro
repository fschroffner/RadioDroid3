# RadioDroid 3 ProGuard / R8 Rules

# --- Kotlin & Coroutines ---
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, SourceFile, LineNumberTable

# --- Gson & Serialization ---
-keepattributes EnclosingMethod
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Retain all DTOs and models serialized/deserialized with Gson
-keep class com.github.fschroffner.radiodroid3.data.remote.dto.** { *; }
-keep class com.github.fschroffner.radiodroid3.proxy.ProxySettings { *; }
-keep class com.github.fschroffner.radiodroid3.station.live.metadata.lastfm.data.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.github.fschroffner.radiodroid3.data.local.entity.** { *; }
-keep class com.github.fschroffner.radiodroid3.history.TrackHistoryEntry { *; }

# --- Domain Models, Data Classes & Parcelables ---
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepnames class com.github.fschroffner.radiodroid3.domain.model.** { *; }
-keepnames class com.github.fschroffner.radiodroid3.station.DataRadioStation { *; }
-keepnames class com.github.fschroffner.radiodroid3.station.live.** { *; }
-keepnames class com.github.fschroffner.radiodroid3.players.PlayState { *; }
-keepnames class com.github.fschroffner.radiodroid3.players.selector.PlayerType { *; }
-keepnames class com.github.fschroffner.radiodroid3.service.PauseReason { *; }

# --- Media3 / ExoPlayer ---
-dontwarn androidx.media3.**
-keep class androidx.media3.session.** { *; }

# --- OkHttp & Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# --- Picasso ---
-dontwarn com.squareup.picasso.**

# --- Iconics ---
-keep class com.mikepenz.iconics.** { *; }
-keep class * implements com.mikepenz.iconics.typeface.ITypeface { *; }

# --- String similarity libraries ---
-keep class info.debatty.java.stringsimilarity.** { *; }
-keep class me.xdrop.fuzzywuzzy.** { *; }

# --- Custom Views & Preferences referenced from XML ---
-keep class com.github.fschroffner.radiodroid3.views.** { *; }
-keep class com.github.fschroffner.radiodroid3.views.IntEditTextPreference { *; }
-keepclassmembers class * extends androidx.preference.Preference {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# --- Coil & Compose ---
-dontwarn coil.**
-dontwarn androidx.compose.**
