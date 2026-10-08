# Room, Compose and Navigation ship their own consumer rules.

# ML Kit (camera VIN/HIN scan): its components are looked up at runtime through firebase-components
# registrars, and R8 full mode rewrites those internals so TextRecognition.getClient() hits a null
# component and crashes. Keep ML Kit whole.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.firebase.components.** { *; }
