# Validation

Research build preparation: 8 September 2026.

## Model validation

The bundled pair is MobileCLIP2-S0, not the earlier CLIP experiment. `scripts/models/prepare_models.py` validates normalised 512-value embeddings against PyTorch for four deterministic synthetic image tensors and ten text queries, including UTF-8 and truncation. It fails before replacing assets if output is non-finite or similarity drops below the stated numerical acceptance thresholds.

See `model-validation.json` for measured values and exact model SHA-256 hashes. These are numerical conversion checks, not a comprehensive photo retrieval-quality benchmark or Pixel speed measurement.

## Android checks

The test suite covers date/time-zone/DST parsing, malformed filters, coordinate distance, embedding validity, SQLite round trips, idempotent indexing, deletion/pruning, OCR literal matching, date/GPS constraints, privacy field purges, permission-filtered results, result limits, and tokenizer equivalence against OpenCLIP.

Final verification completed successfully with JDK 21 and Android SDK 36:

- `:pixel:assembleDebug :pixel:lintDebug :pixel:testDebugUnitTest`: BUILD SUCCESSFUL.
- 21 tests passed; zero failures or skipped tests.
- Android lint: zero errors, 32 advisory warnings. These concern SDK/dependency updates, Pixel-only ARM64 packaging and Kotlin/version-catalog style; some refer to the unused upstream catalog retained in the audit checkout, which is not published in this project.
- APK signature verified (v2); debug-signed research build.
- APK contains ARM64 native libraries only. All five native libraries have ELF load-segment alignment of at least 16 KB; `zipalign -c -P 16 4` passed.
- Packaged minimum and target API: 36. INTERNET, ACCESS_NETWORK_STATE and ACCESS_WIFI_STATE are absent.
- Bundled image/text model hashes match the validated source assets.

APK size: 171,670,196 bytes (about 164 MiB). SHA-256:

```
d7aa0f59289e5f0d916717cacf567402471365d39959a3dc3cf5784924fba9b8
```

The detailed packaging record is in `apk-verification.json`.

## Not verified here

No physical Pixel 10 or Tensor SDK Beta access was available. TPU execution, actual GPU partitioning, overnight scheduling, battery impact and real-device throughput remain to be tested. An APK that compiles is not evidence that its TPU path has executed. Settings makes this distinction explicit.
