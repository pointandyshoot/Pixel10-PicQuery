# Security and privacy review

Scope: source inspection of upstream PicQuery at `4d728e927f617efaf1b94cf0c96325ce5791fdc4`, the new `pixel` module, build/model sources and final merged Android permissions. This is a focused engineering review, not an independent penetration test or a guarantee that native dependencies contain no vulnerabilities.

## Findings and changes

| Upstream finding | Treatment in this build |
|---|---|
| INTERNET/network/Wi-Fi permissions despite offline positioning | Manifest merger explicitly removes them, including transitive requests |
| Android backup enabled | Backup disabled; cloud/device-transfer exclusion rules cover private app storage |
| Search/translation/photo details logged | No app query/photo/OCR/GPS logging, no analytics or log-upload controls |
| Separate Room and ObjectBox paths; repeated inserts can duplicate embeddings | One private SQLite index, URI primary key and replace/upsert semantics |
| A 1,000-thumbnail buffer and large batches | One bounded decode at a time; per-photo checkpointing; direct bounded centre crop |
| Generic GPU handling and no truthful user-facing diagnostics | Dedicated inference thread, explicit GPU/CPU fallback, settings with invocation status and limitations |
| Changed/deleted/revoked photo access can leave stale derived records | Full successful snapshot pruning; search intersects current readable MediaStore URIs |
| Unclosed image input streams and unbounded full-image decode paths | Structured resource closure and bounded ImageDecoder sizes |
| Asset reuse based on byte length alone | APK-bundled model pair with recorded SHA-256 and model identity; no downloaded executable/model import path |
| App code licence differs from MobileCLIP weights | Full research model licence/attribution included; intended purpose explicitly research |

## Data and permissions

- App-private SQLite stores URI, file label, capture/modified date, source generation fingerprint, 512-value vector, optional OCR and optional EXIF coordinates.
- No server, account, advertising identifier, external index export, network model download, background location tracking or original photo writes.
- READ_MEDIA_IMAGES / READ_MEDIA_VISUAL_USER_SELECTED read only authorised photos. ACCESS_MEDIA_LOCATION is optional and only reads coordinates already embedded in originals; it is not GPS tracking permission.
- WorkManager contributes protected scheduling components and wake-lock/boot-related permissions. Exported scheduling services are Android permission-protected. The only user-facing exported activity is the launcher.
- The app sends a content URI to another installed app only when the user presses Open original, with a temporary read grant. Android may display that photo in the receiving app under that app's own privacy behaviour.
- Turning OCR/GPS off purges those stored fields. Clear photo index cancels indexing, serialises deletion against the writer and disables automatic indexing. Photos and saved places remain. Uninstall or Android Clear storage removes all app data.
- Native LiteRT and ML Kit can emit their own diagnostic messages; app code does not log content. The delivered test APK is debug-signed/debuggable. Use a private stable release key and a release build for production-grade deployment.

## Residual risks and limits

Android app sandbox/encryption protects local storage; the database is not separately password-encrypted. A rooted/compromised/unlocked device or an authorised debugger can access data. Flash storage does not offer an app-level forensic-erasure guarantee. Sensitive local indexes should be treated like the photos themselves.

Model and dependency code is third-party. Model preparation downloads a pinned Hugging Face checkpoint and pinned source revisions on the development computer; the app cannot make network requests. Runtime files are model data, not executable Python checkpoints. A research model licence does not authorise commercial distribution. TPU binaries must come from the authorised SDK and match the compiler version.
