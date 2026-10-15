# 0.3.1 map update

Install over **Pixel Photo Search v2 0.2.0 or 0.3.0**. Do not uninstall. Package, signing identity, database schema, model identity, folder access and indexing implementation are preserved.

## Map behaviour

- The map sits inside a clipped, fixed-layout native container. Gestures stay inside it.
- **Enable map tiles** asks before contacting OpenStreetMap. Online tiles remain optional. The inline status distinguishes off, loading, available and failed. **Map options** offers retry and disable.
- Single-photo markers show thumbnails and open an image viewer. Multiple-photo markers open a scrollable thumbnail grid at every zoom level. The viewer offers previous/next and Open original.
- **Photos in view** browses all currently supplied map photos inside the viewport; it has no additional 1,000-photo cap.
- Existing **Search this area** applies the viewport to the main search. Text/OCR/date filters combine with it. Switch to Photos to browse those results; clear the area to widen the search again. Searching text in Map mode changes the marker dataset. The existing semantic search retains its top-1,000 result limit; an unfiltered map retains all accessible located records.
- Photos without indexed GPS cannot appear on the map. No online geocoder, face recognition or indexing-speed changes are introduced.

## Recovery build status

The original 0.2/0.3 Kotlin source was lost during workspace maintenance before it was pushed to GitHub. The signed 0.3.0 APK and private signing backup survived. Rather than substitute the older 0.1 indexing/database implementation, this update preserves the original 0.3 runtime and changes only map presentation classes and version metadata. The Java map sources and asserted bytecode integration hooks are here. This is a recovery build, **not a claim that the full 0.3 Kotlin project has been restored**. The root `pixel` Gradle module remains the original 0.1 source baseline.

`build.py` starts from one explicitly supplied APK, verified by SHA-256. It rebuilds the modified application dex, adds the new map dex, and copies every other dex, resource, asset, model and native library directly from the original. It does not download an APK or touch a phone's index. Compile-only `LocatedPhoto` stubs are excluded from the APK. Do not package them.

Required base: `Pixel10-PicQuery-0.3.0-arm64.apk`

```
f5acbf5ffdf25ca9f079eccd3b78168e4ce4610592b9d8b4f918f8a553c02c05
```

Use JDK 17+ and Python 3.12. In a private tools directory supply:

- `apktool.jar`: Apktool 3.0.3, official iBotPeaches/Apktool release.
- `r8.jar`: Google `com.android.tools:r8:8.13.17`.
- `kotlin.jar`: Maven Central `org.jetbrains.kotlin:kotlin-stdlib:2.3.21`.
- `osmdroid.jar`: `classes.jar` extracted from `org.osmdroid:osmdroid-android:6.1.20` AAR.
- `sdk/android-36/android.jar`: Android platform 36 r02.
- `sdk/android-16/`: Android build-tools 36.0.0 extracted directory, including executable zipalign and lib/apksigner.jar.

```sh
python map-update/build.py --base-apk /private/Pixel10-PicQuery-0.3.0-arm64.apk \
  --tools /private/tools --keystore /private/debug.keystore \
  --work /tmp/picquery-map-build --output /private/Pixel10-PicQuery-0.3.1-arm64.apk
```

Use the original private v2 signing backup. Never commit it. Certificate SHA-256 must be `2d41d5de493ea99e55b7bb5232e85b8894d236f6c4c2d9d5200ea41bb6223245`. Another signing key cannot upgrade the installed app.

## Privacy and validation

Online map requests reveal viewed tile regions and IP address to OSM. Photo bytes, OCR, queries and precise coordinate records are not sent. Tile configuration retains the identified User-Agent, HTTPS, visible attribution, local caching and seven-day cache extension. No bulk tile download. Photo viewers only decode local content URIs using bounded images; Open original grants temporary read access to the chosen external viewer.

Tests use Robolectric API 34 for native map controls/layout, tile consent, region/date-line boundaries, marker selection, full area galleries and filtered-dataset visibility. Android compilation uses API 36. Physical Pixel pinch gestures, actual phone tile connectivity and real photo access still need device confirmation. See `VALIDATION.md` for final packaging results.
