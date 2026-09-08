# Third-party notices

## PicQuery

This research app adapts the CLIP BPE tokenizer and MobileCLIP2 export/quantisation tooling from [greyovo/PicQuery](https://github.com/greyovo/PicQuery), revision `4d728e927f617efaf1b94cf0c96325ce5791fdc4`. Original MIT copyright and licence are retained in `LICENSE`. The Android app layer was rewritten for this Pixel-focused research project. The original repository remains the source for historical code reviewed in `SECURITY.md`.

## Apple MobileCLIP2

**Apple Machine Learning Research Model is licensed under the Apple Machine Learning Research Model License Agreement.** The full agreement is in `LICENSE-MODEL-APPLE.txt` and inside the APK assets.

This model derivative exports MobileCLIP2-S0 (`dfndr2b`) to LiteRT: reparameterised image tower with normalised float32 output, and text tower with dynamically quantised int8 weights and float32 activations/output. It uses the same 512-dimensional image/text embedding space. This build is for the user's stated non-commercial research/proof-of-concept purpose. The Apple name is attribution, not endorsement.

Pinned weights: `timm/MobileCLIP2-S0-OpenCLIP`, revision `095906d28bf54d7584dc411e8ffe448f34289e05`. Licence source: `apple/MobileCLIP2-S0`, revision `3136ea51c8ed56b9f9abfab04cb816735aaad6cb`.

## Other components

- OpenCLIP / timm: model construction and numerical reference during development; not Python code running on the phone.
- Google LiteRT 2.2.0: Apache-2.0 runtime, including its bundled third-party notices.
- Google ML Kit Latin text recognition: bundled Android library and OCR assets under Google's SDK terms. The app has no internet permission.
- AndroidX, Jetpack Compose and WorkManager: Apache-2.0.
- Kotlin / kotlinx.coroutines: Apache-2.0.
- CLIP BPE vocabulary: retained from PicQuery/OpenAI CLIP tooling; upstream attribution preserved.

Dependency AAR/JAR licence notices are retained by normal Android packaging. The optional Tensor SDK binaries are not distributed in this build.

MIT License

Copyright (c) 2023 Jiehui Liu

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.