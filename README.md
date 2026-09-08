# Pixel10-PicQuery — research proof of concept

Offline photo search for **Pixel 10 / Tensor G5**, Android 16 and later. Adapted from [greyovo/PicQuery](https://github.com/greyovo/PicQuery/tree/4d728e927f617efaf1b94cf0c96325ce5791fdc4); the production module in this project is `pixel`.

**TPU status:** this first build does not bundle Google's gated Tensor SDK runtime or an AOT-compiled Tensor G5 model. It runs the GPU/CPU path. The optional TPU path is implemented, but is not device-validated. Selecting Auto cannot turn an ordinary TFLite model into a TPU model. See [Tensor G5 setup](docs/TENSOR-G5.md).

**Research use:** the app source is MIT. MobileCLIP2-S0 weights are covered separately by the [Apple Machine Learning Research Model licence](LICENSE-MODEL-APPLE.txt). This build is for the requested research/proof-of-concept use, not a commercial product. No Apple endorsement is implied.

## Features

- Natural-language visual search with **MobileCLIP2-S0**, plus image-to-image retrieval.
- Offline, bundled ML Kit Latin-script OCR: signs, menus, receipts and screenshots.
- Date filters and optional photo EXIF GPS filters. Saved place names work without an online geocoder.
- Automatic incremental indexing while charging, with battery/storage constraints and thermal/Battery Saver pauses.
- Manual indexing when unplugged. Work is checkpointed per photo in short, resumable jobs.
- MediaStore change notifications, persistent content triggers and a periodic backstop.
- **Settings → Inference:** requested preference, last successful image backend, text backend, inference duration and fallback reason. CPU partitions may exist in an accelerated invocation; the UI says so instead of claiming every operation used GPU/TPU.
- Read-only photo access, including Android's selected-photo grants. Indexing never alters originals.
- No internet permission, advertising, analytics, query history, cloud backup or device-transfer backup.
- Clear index; turning OCR/GPS off purges those indexed fields.

## Install and use

See [installation and Android Studio instructions](docs/INSTALL.md). The downloadable APK contains both visual models and OCR; no first-launch download is required.

1. Install the APK and choose all photos or selected photos.
2. Tap **Index now**, or plug in and leave automatic indexing enabled.
3. Open **Settings → Inference** after some photos have been processed to see the backend that succeeded.
4. Search `camper beside a river`, `sunset yesterday`, or `text:"Bramwell"`.

| Filter | Meaning |
|---|---|
| `on:2026-09-07` | Photos from that day |
| `after:2026-08-01` | From that date, inclusive |
| `before:2026-09-01` | Before that date, exclusive |
| `today` / `yesterday` | Phone's local time zone |
| `text:"Bramwell station"` | Required phrase in recognised text |
| `near:-17.96,122.24,50` | Latitude, longitude, radius in kilometres |
| `near Broome` | Within 50 km of a place named Broome saved in Settings |

Combine filters with descriptions. GPS access is optional and off initially. Photos without location metadata cannot match a location filter. The app does not know who “Shannan” is: no face identity enrolment or recognition is implemented.

## Build

```sh
python3 -m venv .venv-models
. .venv-models/bin/activate
pip install torch==2.13.0+cpu torchvision==0.28.0+cpu --index-url https://download.pytorch.org/whl/cpu
pip install -r scripts/models/requirements.txt
python scripts/models/prepare_models.py
./gradlew :pixel:testDebugUnitTest :pixel:lintDebug :pixel:assembleDebug
```

Use Python 3.12, JDK 17, Android SDK 36, build tools 36.0.0 and platform-tools. The first build needs internet access on the **development computer**. The Android app never does. Model files are deliberately excluded from Git; the script creates and validates them. For details and model-free UI/test builds, see the installation guide.

## Design and limitations

The new module reuses PicQuery's CLIP BPE tokenizer and adapts its model export tooling. Its app layer replaces the legacy networking/translation, ObjectBox/Room split and UI to keep one private SQLite index with URI-keyed upserts. The upstream source is credited and its audit is in [SECURITY.md](SECURITY.md).

A 512-value float embedding costs 2,048 bytes per photo before SQLite/OCR overhead. Search streams candidate rows and retains the best 100, so it avoids loading every vector into memory. It currently uses an exact vector scan, not an approximate vector index: very large libraries may have slower searches.

This is a first research build. Numerical model validation and automated tests are recorded in [validation notes](docs/VALIDATION.md). Physical Pixel 10 performance, GPU delegation and TPU execution require testing on the actual phone. Similarity scores are rankings, not calibrated probabilities. Captions, video search, face identities and online place lookup are not implemented.
