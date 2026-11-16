# Proguard rules for ThoughtDrop
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}
