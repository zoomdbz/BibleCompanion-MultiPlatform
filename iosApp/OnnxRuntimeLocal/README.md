# ONNX Runtime iOS package

This local Swift package uses the Microsoft ONNX Runtime 1.24.2 Objective-C
bindings. The source files and `LICENSE` were extracted without modification
from `https://download.onnxruntime.ai/pod-archive-onnxruntime-objc-1.24.2.zip`.
The archive's SHA-256 is
`ab102b30f15c44b08dc6fc32683abeafd216ff6bcabdf74fbac37fbb19d80eed`.

The package downloads Microsoft's 1.24.2 C binary from the URL in `Package.swift`.
Its SHA-256 matches the value in Microsoft's 1.24.2 Swift package manifest.
The app links only the `onnxruntime` product and imports `OnnxRuntimeBindings`.
