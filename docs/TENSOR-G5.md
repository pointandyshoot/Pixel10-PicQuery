# Tensor G5 acceleration

The default research APK **does not claim active TPU execution**. Google Tensor SDK Beta requires approved access and ahead-of-time compilation. An ordinary TFLite model cannot be compiled for Tensor G5 on the phone.

Official documentation checked during implementation:

- https://developers.google.com/edge/litert/next/tensor-sdk
- https://developers.google.com/edge/litert/next/npu

## Implemented path

`EmbeddingEngine` runs on a dedicated single thread, preserving GPU thread affinity. Auto selects a Tensor G5 AOT candidate only when the device reports a Google Tensor G5, `image_model_tensor_g5.tflite` is packaged, and `libLiteRtDispatch.so` exists in the app's native library directory. It creates a LiteRT 2.2.0 `CompiledModel` with an NPU request and a dispatch directory. Failures fall through to the ordinary model on GPU, then four-thread CPU. Creation and invocation failures are both handled. Text encoding uses CPU; bundled ML Kit manages its own OCR backend.

An accelerated invocation can contain CPU partitions. The public Kotlin API used here does not expose a reliable per-operation execution breakdown. The settings screen therefore reports the successful requested invocation and this limitation, rather than equating device capability with proven full-TPU execution.

## To complete TPU validation

1. Obtain Google's Tensor SDK Beta through the official signup and follow the associated SDK licence/distribution terms.
2. Compile the **same MobileCLIP2-S0 image tower** with the Tensor G5 AOT compiler, using compatible compiler/runtime versions. Export must retain float32 NCHW `[1,3,256,256]` input and 512 float output. Use representative calibration data if quantisation is required, and validate output similarity/retrieval before packaging.
3. Add the resulting model as `pixel/src/main/assets/image_model_tensor_g5.tflite` and the SDK-approved arm64 runtime dependencies under `pixel/src/main/jniLibs/arm64-v8a/`. Configure extraction (`useLegacyPackaging = true`) if the dispatch libraries need filesystem paths. Do not commit gated binaries or credentials.
4. Verify SDK dispatch filename/dependencies against the delivered SDK version; adapt the path gate if Google changes packaging. This optional integration has not been exercised with the gated SDK.
5. Build and test on the Pixel 10. Use the SDK profiler/benchmark tools to confirm which operations actually execute on TPU. Compare vectors against the CPU reference and measure indexing speed/thermal behaviour before calling the build TPU-validated.

No proprietary SDK, access approval, compiled Tensor model or physical Pixel was available in this environment. The repository provides a concrete integration point and a working fallback build; obtaining those assets and performing hardware validation remain separate work.
