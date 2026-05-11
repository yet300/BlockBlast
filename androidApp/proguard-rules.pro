# ─────────────────────────────────────────────────────────────────────────
# BlockBlast app-specific R8 keep rules.
#
# Library consumer rules (AndroidX, Kotlin, Kotlinx, Google Play Services,
# Firebase, AdMob, UMP, Compose, Decompose, MVIKotlin, multiplatform-settings,
# Metro) are shipped inside each library's AAR/JAR — do NOT duplicate them
# here. Re-adding them broadens what R8 keeps and hurts app size.
#
# R8 Full Mode (AGP 9.0 default) is in use. Only rules below are strictly
# necessary for this project; add new ones with a justification comment.
# ─────────────────────────────────────────────────────────────────────────

# ── kotlinx.serialization ────────────────────────────────────────────────
# GameState / Grid / Piece / Polyomino / Position / FeedbackType / GameEvent
# plus Decompose navigation `Config` classes are serialized via the compiler
# plugin's generated `$serializer` companions. R8 full mode can't see the
# reflective `::class.serializer()` lookup the plugin emits, so keep the
# generated `Companion` + its `serializer()` method on any `@Serializable`
# type. Narrow to our own domain packages — we don't need to protect
# library-side serializers.
-keepclassmembers @kotlinx.serialization.Serializable class ge.yet.blokblast.domain.model.** {
    public static ** Companion;
}
-keepclassmembers @kotlinx.serialization.Serializable class ge.yet.blokblast.data.** {
    public static ** Companion;
}
-keepclassmembers @kotlinx.serialization.Serializable class ge.yet.blockblast.feature.** {
    public static ** Companion;
}
-keepclasseswithmembers class ge.yet.blokblast.domain.model.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ge.yet.blokblast.data.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ge.yet.blockblast.feature.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Room / WorkManager (transitive) ──────────────────────────────────────
# We don't use Room or WorkManager directly, but WorkManager is pulled in
# transitively by Play Services Ads / UMP and auto-initialises through
# `androidx.startup`. Under R8 full mode, `Room.getGeneratedImplementation`
# does a `Class.forName("<DbName>_Impl")` lookup that R8 can't trace — the
# generated `WorkDatabase_Impl` gets stripped/renamed, startup crashes
# with `Unable to get provider androidx.startup.InitializationProvider`.
#
# Keep:
#   * any RoomDatabase subclass's default constructor (so `newInstance()` works)
#   * Room's runtime internals that the generated `_Impl` needs
#   * the annotated schema classes
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class ** extends androidx.room.RoomDatabase$Callback
-keep class * extends androidx.room.migration.Migration
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-dontwarn androidx.room.paging.**
