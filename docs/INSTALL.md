# Installation

## Install the supplied APK

1. Download `Pixel10-PicQuery-research.apk` on the Pixel 10.
2. Open it from Downloads. If Android asks, allow that browser/file manager to **install unknown apps**, then install. This permission belongs to the installer, not PicQuery; you can turn it off afterwards.
3. Open **Pixel Photo Search**. Choose all photos or selected photos.
4. Tap **Index now** for the first test. Automatic runs normally wait until the phone is charging and Battery Saver is off. Keep the phone cool during the initial library scan.
5. After at least one image has been indexed, inspect **Settings → Inference**. The preference and the successful backend are shown separately.

This APK is a debug-signed research build, not a Play Store release. Android's debug certificate is not a production signing identity. A build made on another computer may have a different key: use your own stable signing key for future releases, or uninstall before replacing it (which deletes the local index). Original photos are unaffected.

## Android Studio

1. **Get from Version Control**, URL `https://github.com/pointandyshoot/Pixel10-PicQuery.git`. For an existing checkout use **Git → Pull**.
2. In SDK Manager install **Android SDK Platform 36**, **Android SDK Build-Tools 36.0.0**, and **Android SDK Platform-Tools**.
3. Use JDK 17 or a compatible newer bundled Gradle JDK. The wrapper pins Gradle 9.4.1; AGP is 9.2.0.
4. In a terminal at the repository root, run the model preparation commands from the README. Model export uses several gigabytes of temporary space and downloads model weights; allow at least 12 GB free space and preferably 16 GB RAM.
5. Gradle Sync. Select the **pixel** run configuration/module.
6. On the phone, enable Developer options → USB debugging. Connect USB, approve your computer, select the Pixel 10 in Android Studio and click Run.

Command line build/install:

```sh
./gradlew :pixel:testDebugUnitTest :pixel:lintDebug :pixel:assembleDebug
adb install -r pixel/build/outputs/apk/debug/pixel-debug.apk
```

Windows: use `gradlew.bat` and activate the Python environment using `.venv-models\Scripts\activate`. Model export is best run on Linux/WSL because LiteRT conversion tooling has platform requirements.

## Model preparation

`python scripts/models/prepare_models.py` exports the pinned MobileCLIP2-S0 checkpoint to a float32 image encoder, dynamically quantises text weights to int8, checks both towers against their PyTorch reference, then packages only the validated models. It writes checksums and an index model identity. Updated model identities trigger re-embedding on the next scan.

The licence is separate from the app's MIT source licence. This repository uses these assets only for the requested research proof of concept. Models are bundled, never fetched by the Android app.

With `-PallowMissingModels=true`, a model-free checkout can compile and run the UI/OCR/date functionality, but visual search will report missing models. Such an APK must not be described as the complete visual-search build. Model files, compiler products and private signing keys should not be committed.

## Troubleshooting

- **Nothing indexed automatically:** plug in, turn Battery Saver off, let the phone cool, confirm photo permission and Automatic indexing. Android schedules background work opportunistically. A force-stopped app must be opened again.
- **CPU shown:** read the fallback reason. TPU needs a compiled Tensor G5 pack; GPU support varies with model operations and device drivers. CPU is a functional fallback.
- **Location query has no results:** enable Use photo locations, grant original metadata access, and rescan. Many screenshots or shared photos have no GPS. Add place names in Settings or use explicit coordinates.
- **Changed the selected photos:** Manage photo access. Searches intersect the index with current MediaStore visibility; the next full scan also prunes inaccessible records.
- **Unreadable photos:** corrupt or temporarily inaccessible media is skipped and can be retried. The app searches images physically accessible on the phone, not cloud-only Google Photos items.
- **Indexing failure:** note the exception class and backend details shown in Settings; do not send private photographs or logs unnecessarily.
