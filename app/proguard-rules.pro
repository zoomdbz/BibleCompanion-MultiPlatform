# ONNX Runtime's native engine looks up its Java classes and members by name.
# The Android AAR does not bundle consumer rules for these JNI entry points.
-keep class ai.onnxruntime.** { *; }

# Room creates generated database implementations through reflection. R8 full
# mode must retain their public no-argument constructors as well as the classes.
-keep class * extends androidx.room.RoomDatabase { public <init>(); }

# WorkManager also creates input mergers by class name. Its older consumer
# rules retain the classes but omit the constructors required by widgets.
-keep class * extends androidx.work.InputMerger { public <init>(); }
