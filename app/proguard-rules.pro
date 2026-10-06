# ONNX Runtime's native engine looks up its Java classes and members by name.
# The Android AAR does not bundle consumer rules for these JNI entry points.
-keep class ai.onnxruntime.** { *; }
