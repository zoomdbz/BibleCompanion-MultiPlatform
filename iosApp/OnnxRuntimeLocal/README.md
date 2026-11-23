# ONNX Runtime iOS package

This local Swift package uses the Microsoft ONNX Runtime 1.24.2 Objective-C
bindings. The source files and `LICENSE` were extracted without modification
from `https://download.onnxruntime.ai/pod-archive-onnxruntime-objc-1.24.2.zip`.
The archive's SHA-256 is
`ab102b30f15c44b08dc6fc32683abeafd216ff6bcabdf74fbac37fbb19d80eed`.

The 1.24.2 C binary is bundled as `onnxruntime.xcframework`; its two static
libraries are stored in Git LFS (see `.gitattributes`). It used to be
downloaded from `https://download.onnxruntime.ai/pod-archive-onnxruntime-c-1.24.2.zip`,
but on 2026-11-20 that host began serving a certificate for `*.azureedge.net`
only. Xcode refused the download and package resolution failed on Bitrise.

Provenance of the bundled framework:

- Source: NuGet package `Microsoft.ML.OnnxRuntime` 1.24.2, from
  `https://api.nuget.org/v3-flatcontainer/microsoft.ml.onnxruntime/1.24.2/microsoft.ml.onnxruntime.1.24.2.nupkg`.
- The package's SHA-512 (base64) is
  `drZ8ja/CPE8grQljcFfCAh9KhzcBgmzsnidKBKIBetEUM4eSdaCR+aHVmXqa6n+7dYm0nQNgKLGTthaPmdn0YA==`,
  matching the nuget.org catalog entry published 2026-02-19.
- The framework is `runtimes/ios/native/onnxruntime.xcframework.zip` from that
  package: static `onnxruntime.framework`, version 1.24.2, ORT API 24,
  MinimumOSVersion 15.1.
- Kept slices: `ios-arm64` and `ios-arm64_x86_64-simulator`, byte for byte.
  The `ios-arm64_x86_64-maccatalyst` slice was removed, along with its entry
  in the xcframework `Info.plist`; the app does not target Mac Catalyst.
- SHA-256 of the bundled libraries:
  - `ios-arm64/onnxruntime.framework/onnxruntime`:
    `53260b33e2de639a1f77c31cccdbbc5f15c254190ced74b7acf4a386a0330e72`
  - `ios-arm64_x86_64-simulator/onnxruntime.framework/onnxruntime`:
    `a49fd2277770009c6bd6db925af1c69f5213214a5058abef5efcb3d13934a039`

To move to a later ONNX Runtime, replace the framework the same way and update
the bindings in `objectivec/` from the matching objc pod archive.
The app links only the `onnxruntime` product and imports `OnnxRuntimeBindings`.
