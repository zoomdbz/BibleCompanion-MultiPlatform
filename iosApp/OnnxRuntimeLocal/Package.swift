// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "onnxruntime",
    platforms: [.iOS(.v15), .macOS(.v14)],
    products: [
        .library(name: "onnxruntime", type: .static, targets: ["OnnxRuntimeBindings"]),
    ],
    targets: [
        .target(
            name: "OnnxRuntimeBindings",
            dependencies: ["onnxruntime"],
            path: "objectivec",
            cxxSettings: [.define("SPM_BUILD")]
        ),
        // Bundled in the repo (binaries in Git LFS) instead of downloaded from
        // download.onnxruntime.ai, whose TLS certificate stopped matching its
        // hostname on 2026-11-20 and broke package resolution. See README.md.
        .binaryTarget(
            name: "onnxruntime",
            path: "onnxruntime.xcframework"
        ),
    ],
    cxxLanguageStandard: .cxx17
)
