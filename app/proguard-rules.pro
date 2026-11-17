# Keep Go gomobile native bridge & methods accessed via reflection
-keep class go.** { *; }
-dontwarn go.**
-keep class whatsbridge.** { *; }
-dontwarn whatsbridge.**

# Keep Room database & entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
}

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wupp.meow.**$$serializer { *; }
-keepclassmembers class com.wupp.meow.** {
    *** Companion;
}
-keepclasseswithmembers class com.wupp.meow.** {
    kotlinx.serialization.KSerializer serializer(...);
}
