import Foundation
import shared
import onnxruntime

/// Implements IosQueryEncoder (Kotlin protocol) using ONNX Runtime ObjC.
/// Session creation is deferred until the first encode() call so launch
/// doesn't pay the model-load cost when the user never searches.
class OnnxEncoder: NSObject, IosQueryEncoder {

    private var session: ORTSession?
    private var sessionInitTried: Bool = false
    private let sessionInitLock = NSLock()

    private func ensureSession() -> ORTSession? {
        if let s = session { return s }
        sessionInitLock.lock()
        defer { sessionInitLock.unlock() }
        if let s = session { return s }
        if sessionInitTried { return nil }
        sessionInitTried = true
        guard let modelPath = Bundle.main.path(
            forResource: "model_quantized", ofType: "onnx", inDirectory: "embedding"
        ) else { return nil }
        do {
            let env = try ORTEnv(loggingLevel: .warning)
            let opts = try ORTSessionOptions()
            try opts.setIntraOpNumThreads(2)
            session = try ORTSession(env: env, modelPath: modelPath, sessionOptions: opts)
            return session
        } catch {
            print("OnnxEncoder init failed: \(error)")
            return nil
        }
    }

    func isReady() -> Bool {
        // Reflect lazy-init contract: return true if a session exists OR can be
        // created on demand. The Kotlin caller then proceeds to encode(),
        // which performs the actual lazy load.
        if session != nil { return true }
        // Avoid initializing in isReady; just report whether a session was
        // attempted and failed. First encode() will retry.
        return !sessionInitTried || session != nil
    }

    func encode(inputIds: KotlinLongArray, attentionMask: KotlinLongArray) -> KotlinFloatArray? {
        guard let session = ensureSession() else { return nil }
        let seqLen = Int(inputIds.size)

        do {
            let idsData = longArrayToData(inputIds, count: seqLen)
            let maskData = longArrayToData(attentionMask, count: seqLen)
            let shape: [NSNumber] = [1, NSNumber(value: seqLen)]

            let idsTensor = try ORTValue(
                tensorData: NSMutableData(data: idsData),
                elementType: .int64, shape: shape
            )
            let maskTensor = try ORTValue(
                tensorData: NSMutableData(data: maskData),
                elementType: .int64, shape: shape
            )

            let outputs = try session.run(
                withInputs: ["input_ids": idsTensor, "attention_mask": maskTensor],
                outputNames: Set(["last_hidden_state"]),
                runOptions: nil
            )

            guard let output = outputs["last_hidden_state"] else { return nil }
            let outputData = try output.tensorData() as Data
            let totalFloats = seqLen * 384
            let requiredBytes = totalFloats * MemoryLayout<Float>.size
            // Validate buffer length so an unexpected model output shape or
            // runtime mismatch can't index out-of-bounds and crash the app.
            guard outputData.count >= requiredBytes else {
                print("OnnxEncoder: short output buffer: \(outputData.count) < \(requiredBytes)")
                return nil
            }
            let result = KotlinFloatArray(size: Int32(totalFloats))

            outputData.withUnsafeBytes { raw in
                let floats = raw.bindMemory(to: Float.self)
                for i in 0..<totalFloats {
                    result.set(index: Int32(i), value: floats[i])
                }
            }
            return result
        } catch {
            print("OnnxEncoder inference failed: \(error)")
            return nil
        }
    }

    private func longArrayToData(_ arr: KotlinLongArray, count: Int) -> Data {
        var data = Data(count: count * 8)
        data.withUnsafeMutableBytes { raw in
            let ptr = raw.bindMemory(to: Int64.self)
            for i in 0..<count {
                ptr[i] = arr.get(index: Int32(i))
            }
        }
        return data
    }
}
