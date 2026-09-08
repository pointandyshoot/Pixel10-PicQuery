# Validation

Research build preparation: 8 September 2026.

## Model validation

The bundled pair is MobileCLIP2-S0, not the earlier CLIP experiment. `scripts/models/prepare_models.py` validates normalised 512-value embeddings against PyTorch for four deterministic synthetic image tensors and ten text queries, including UTF-8 and truncation. It fails before replacing assets if output is non-finite or similarity drops below the stated numerical acceptance thresholds.

See `model-validation.json` for measured values and exact model SHA-256 hashes. These are numerical conversion checks, not a comprehensive photo retrieval-quality benchmark or Pixel speed measurement.

## Android checks

The test suite covers date/time-zone/DST parsing, malformed filters, coordinate distance, embedding validity, SQLite round trips, idempotent indexing, deletion/pruning, OCR literal matching, date/GPS constraints, privacy field purges, permission-filtered results, result limits, and tokenizer equivalence against OpenCLIP.

Final build/test outcomes and APK inspection are recorded when packaging completes.

## Not verified here

No physical Pixel 10 or Tensor SDK Beta access was available. TPU execution, actual GPU partitioning, overnight scheduling, battery impact and real-device throughput remain to be tested. An APK that compiles is not evidence that its TPU path has executed. Settings makes this distinction explicit.
