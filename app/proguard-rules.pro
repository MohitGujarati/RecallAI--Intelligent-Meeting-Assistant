# ============================================================
# RecallAI — ProGuard / R8 rules
# ============================================================

# Keep source file names and line numbers in crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ───────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { *; }
-keepclassmembers class kotlin.coroutines.** { *; }

# ── Gson DTOs (no @SerializedName — field names must survive) ─
# Remote DTOs
-keep class com.mohit.recall_ai.data.remote.dto.** { *; }
# Internal response classes used with gson.fromJson inside API services
-keep class com.mohit.recall_ai.data.remote.api.GeminiSummaryService$* { *; }
-keep class com.mohit.recall_ai.data.remote.api.GeminiTranscriptionService$* { *; }
# GeminiStreamingClient (in api/ root package)
-keep class com.mohit.recall_ai.api.** { *; }
# Tool registry DTOs
-keep class com.mohit.recall_ai.data.remote.tools.** { *; }
# FunctionCallEvent inner class in GeminiLiveClient
-keep class com.mohit.recall_ai.data.remote.api.GeminiLiveClient$* { *; }

# ── Room entities (column names are matched by reflection) ────
-keep class com.mohit.recall_ai.data.local.entity.** { *; }
-keep class com.mohit.recall_ai.data.local.dao.** { *; }

# ── Enums (used in Room TypeConverters + when expressions) ───
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    *;
}

# ── Sealed classes (exhaustive when checks need all subclasses)
-keep class com.mohit.recall_ai.model.** { *; }
-keep class com.mohit.recall_ai.service.LiveAiState { *; }
-keep class com.mohit.recall_ai.service.LiveAiState$* { *; }
-keep class com.mohit.recall_ai.service.RecordingStateHolder { *; }

# ── Hilt / Dagger ─────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}
# Hilt-generated components — keep all generated classes
-keep class **_HiltComponents* { *; }
-keep class **_Hilt_* { *; }
-keep class *_MembersInjector { *; }
-keep class *_Factory { *; }

# ── WorkManager @HiltWorker ───────────────────────────────────
-keep class com.mohit.recall_ai.worker.** { *; }
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── OkHttp / WebSocket (Live AI uses raw WebSocket) ──────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okhttp3.internal.ws.** { *; }

# ── Retrofit ──────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# ── Gson ──────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Firebase (bundled rules cover most, these fill gaps) ──────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── BroadcastReceivers (AlarmManager + Boot) ─────────────────
-keep class com.mohit.recall_ai.receiver.** { *; }

# ── Services ──────────────────────────────────────────────────
-keep class com.mohit.recall_ai.service.RecordingService { *; }
-keep class com.mohit.recall_ai.service.LiveAiService { *; }

# ── Application class ─────────────────────────────────────────
-keep class com.mohit.recall_ai.RecallApplication { *; }
