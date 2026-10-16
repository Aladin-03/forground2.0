# R8 rules for the release build. AndroidX Room, Hilt/Dagger, Compose, Navigation and kotlinx.serialization ship
# their own consumer rules; the ones below are belt-and-braces for the reflection this app relies on.

# Readable stack traces from release crashes (retrace with app/build/outputs/mapping/release/mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room: the generated ForegroundDatabase_Impl is created reflectively by Room.databaseBuilder, and entity fields
# are matched to the exported schema by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class com.aladin.foreground2.core.data.db.** { *; }

# Hilt: generated components and entry points (BootReceiver uses EntryPointAccessors) are looked up by class.
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep @dagger.hilt.EntryPoint interface *
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# kotlinx.serialization: type-safe navigation routes resolve serializer() on @Serializable route objects.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    public static <1> INSTANCE;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
