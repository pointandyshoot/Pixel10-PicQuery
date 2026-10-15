# 0.3.1 validation — 15 October 2026

- 11 Robolectric API-34 tests passed: bounded layout, tile consent, disable preferences, viewport browser button, empty regions, dateline/boundary checks, zoom size, single-marker viewer, 1,100-photo gallery, and visible subset filtering.
- Java sources compile against Android API 36 and osmdroid 6.1.20. D8 and apktool build succeed.
- 221 of 227 recovered application smali classes are unchanged. Six map/presentation/version classes are replaced or modified; assertions reject unexpected modifications. IndexWorker, EmbeddingEngine, PhotoIndex, PhotoSources, PhotoLibrary and their helpers remain unchanged.
- Other five original dex files and all original resources, assets, models and native libraries are byte-identical. Only manifest version fields, application dex and the new map dex differ, plus the renewed APK signature.
- Package remains au.pointandyshoot.picquery.v2. Version 0.3.1, code 4. Signing certificate matches installed v2; SQLite schema/model identity and photo grants are retained through normal upgrade.
- APK CRC/signature checks passed. zipalign -P 16 passed; all five native libraries retain 16-KB ELF alignment. ARM64 only, minimum/target API 36.
- Physical Pixel gestures, tile connectivity and the full Compose integration need phone confirmation. Robolectric is not a substitute for those device tests. No indexing-speed improvement is claimed.

Final APK SHA-256: `6fccdd0c4dbe5ef0f2c28af76390968c882a63cb3ecfee4732d69a2b31bdda3e`.
