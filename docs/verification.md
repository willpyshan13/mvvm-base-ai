# Verification record

Verified on 2026-10-21 with JDK 17, compile/target SDK 36, and an attached Android 7.1.2 / API 25 device (`rk3399-all`). This records observed results, not an assertion that an external backend has been integrated.

## Build and static checks

Passed:

```sh
./gradlew build -PperformanceTests=true :benchmark:assembleBenchmark \
  :app:connectedDebugAndroidTest --no-parallel --max-workers=4
```

The final run completed successfully. Debug, optimized benchmark, and release variants compiled; resource shrinking, R8 optimization, unit tests and lint passed. The optional benchmark and baseline-generator sources compiled. Existing dependency-update and other non-fatal lint warnings were not hidden with a baseline.

## Unit tests

23 distinct cases passed (Android modules also run their release variants):

| Module | Cases | Coverage |
| --- | ---: | --- |
| `core/network` | 11 | Request auth/origin, refresh coordination, cancellation, HTTP/network/business errors, malformed errors, bodyless success |
| `core/common` | 3 | Existing text and byte-size helpers |
| `feature/auth/data` | 4 | Logout versus delayed login, cancellation, transient/revoked refresh, rotated token persistence |
| `feature/home/impl` | 3 | Duplicate submissions, retaining failed drafts, retry, final paging page and paging cancellation |
| `feature/status/data` | 2 | Existing status mapping/configuration behavior |

## Device tests

16 distinct cases passed across the following device test tasks:

| Module | Cases | Coverage |
| --- | ---: | --- |
| `app` | 5 | Independent tabs, draft restoration, deep-link/result handling, protected-link recreation, demo login returning to its target and logout updating the UI |
| `feature/home/data` | 1 | Task persistence after reopening Room |
| `feature/settings/data` | 1 | Settings persistence after reopening DataStore |
| `feature/feed/data` | 3 | Outbox persistence, replay without duplicates, cursor restoration, failed refresh preserving cache, local/server conflict choices |
| `feature/auth/data` | 2 | Encrypted token reopen/clear, corrupted ciphertext invalidation |
| `core/media` | 1 | Restored position, speed/repeat, bounded backward seeking, idempotent release |
| `core/platform` | 3 | Complete download commit, retry and partial cleanup, cancellation stopping the HTTP call |

The app suite was run with `-PdemoAuth=true --no-parallel` to exercise all five cases. Without that flag, the demo-only case is intentionally skipped. The feed persistence tests were also rerun after the final data-layer changes. The final default app run passed its four applicable cases with the demo-only case skipped; the demo run passed all five. A cold launch using `wbase://open/summary` was manually rechecked and displayed the login gate. A discovered auth-restoration/deep-link race and cached-page login-state issue were fixed and covered by these app tests. Test reports remain under each module's `build/reports/` and `build/outputs/androidTest-results/` directories.

## Optimized installation and manual checks

Installed `app-benchmark.apk` using its test signing key, force-stopped the app, then launched it cold. Launch returned `Status: ok`, the process stayed alive, and the startup `FeedSyncWorker` completed successfully without an Android runtime exception in the process log. This validates optimized installation/startup; it is not production keystore or store-distribution validation.

The independent demo APK is generated at `app/build/outputs/demo/will-mvvm-base-demo.apk` and uses `demo` / `demo1234`.

Manually checked the task page, working remote image example, persisted demo feed pagination, and simulated conflict UI. The conflict displayed both “keep local” and “use server” choices. Screenshots:

- [Task page](screenshots/home.png)
- [Examples and system interactions](screenshots/toolbox.png)
- [Cached paginated feed](screenshots/feed.png)
- [Conflict resolution](screenshots/conflict.png)
- [Optimized startup](screenshots/optimized-home.png)
- [Cold protected-link login gate](screenshots/demo-login.png)

## Remaining environment-dependent validation

- Picture-in-picture requires supported API 26+ hardware; this API 25 device cannot exercise it.
- Android 13+ notification permission behavior and modern system-bar behavior need a newer device/emulator. Their code and resources compile, but they were not exercised here.
- Startup/scroll benchmarks and baseline profile generation require API 28+ and a suitable test device; no measured performance result or generated production profile is claimed.
- No real API URL, backend account, crash-reporting provider, production keystore, or verified App Links domain was supplied. Reference adapters and integration boundaries are implemented; actual service contracts and credentials must be configured and integration-tested.
- Static UI has Chinese/English resources; business-adapter messages and example data require product-specific localization as described in the guide.

## Image cache integration and README update

- Added application-wide Coil configuration, bounded memory/disk caches, image retry on connectivity restoration, and Toolbox cache inspection/clearing.
- Updated the Chinese README with implemented capabilities, suitable app categories, and large-app adoption boundaries.
- Attempted `:app:compileDebugKotlin` with JDK 17. Gradle did not start: the environment denied writing `/Users/will/.gradle/wrapper/dists/gradle-8.13-bin/.../gradle-8.13-bin.zip.lck`. This change has no successful compilation or device validation recorded.
- Confirmed cache builder/inspection method signatures against the locally cached Coil 3.3.0 artifact. Device checks remain necessary for offline cache hits, reconnect retry, cache eviction and clearing.

## Task and account/help additions (2026-10-22)

- The task example now includes editing, completion state, delete confirmation, paged search and status filters. Room schema version 2 adds the completion column with a 1-to-2 migration.
- Settings now has a personal-center page with an editable nickname persisted in DataStore, explicit local-only scope, sign-in state, and sign-out confirmation. Help describes the current task/settings/sample flows; the privacy page summarizes the configured local storage, authentication, network, file/cache, and telemetry behavior when an endpoint is configured. Feedback accepts a draft and opens the system share chooser; About reads the installed app label and version.
- XML resources and the exported Room schema JSON were parsed locally. Kotlin compilation could not be completed: the Gradle wrapper could not write its lock under `/Users/will/.gradle`, and direct Gradle startup could not load its macOS native platform library. No unit or device tests were run for these additions.
- Remote account profile sync, registration, password recovery, and account deletion still require an account backend. The privacy summary and agreement explanation do not replace product-specific policy/terms review.


## Follow-up review of task and personal-center changes (2026-10-22)

The review found and corrected these behavioral defects:

- Failed task edits closed the editor and discarded its draft. The editor now closes only after a successful write, shows save errors, and prevents edits during submission.
- Repository fallback filtering happened after pagination. Search and status filtering are now required in the repository query, and `%` / `_` are treated literally.
- Prepending after a refresh could skip tasks when initial/page sizes differed; refresh positions could jump after old pages were dropped. Offset keys and absolute positions are now covered by regression tests.
- Personal-center system Back skipped the settings page, an asynchronously loaded nickname could replace an unsaved restored draft, and Emoji avatar initials could split a surrogate pair. These behaviors were corrected.
- Cache/reset success messages appeared before persistence succeeded. Reset now writes preferences atomically, restores the default system theme/cache limit, and preserves the local nickname. Feedback drafts survive closing/reopening the dialog, and share-launch failure is handled.

Validation was performed with JDK 17 using the installed Gradle 8.13 distribution and `-Dorg.gradle.native=false`:

```sh
gradle -Dorg.gradle.native=false :app:assembleDebug :app:compileDebugAndroidTestKotlin \
  :feature:home:impl:testDebugUnitTest \
  :feature:home:impl:lintDebug :feature:settings:impl:lintDebug \
  :feature:home:data:lintDebug :feature:settings:data:lintDebug \
  :feature:home:data:compileDebugAndroidTestKotlin \
  :feature:settings:data:compileDebugAndroidTestKotlin \
  --no-daemon --max-workers=2 --continue --console=plain --quiet
```

- Eight task unit tests passed with zero failures/errors, covering save/retry behavior, submission serialization, pagination, cancellation, and query forwarding.
- Debug APK assembly and application/data-layer Android test compilation passed. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
- Home/settings UI and data Lint passed with no errors. Settings and both data modules have no reported issues; Home UI retains three warnings in the existing summary/resource code (resource lookup and plural wording).
- Host SQLite checks ran the actual migration SQL against schema v1 and compared the resulting columns with schema v2; existing rows/default completion state and literal search/status queries passed. XML/string references were checked across default, Chinese, and English resources.
- Android migration, DataStore persistence, and personal-center UI regression tests were added/updated and compiled, but were not executed: `adb devices` returned no attached devices, and no emulator binary is installed. This is not device or visual validation.

## Self-developed FFmpeg player migration (2026-10-22)

- Replaced the Media3 playback stack with the self-developed FFmpeg 9.0.2 software player adapted from `mvvm-will-base/base_module/module_player`. The app now owns its playback controller, lifecycle/surface handling, audio output, seek/speed/repeat state, HTTP(S) bridge, cache integration, local/content/asset source handling, and HLS/DASH URI rewriting. Media3 runtime and build aliases were removed.
- The native build packages ARM64 and ARMv7 libraries. DASH support was enabled by building static libxml2 2.15.4 for each ABI; `core/media/src/main/cpp/build_ffmpeg_android.sh` and `build_libxml2_android.sh` document reproducible source checksums and build steps. HTTPS requests use the app's OkHttp-backed local bridge; FFmpeg itself does not contain the TLS stack.
- Host native tests passed for clock/seek/queue cancellation and ordering (including 1,000 queue cycles). On the attached rk3399 ARM64 Android 7.1.2/API 25 device, all 7 `core:media` instrumented tests passed: controller playback/seek/speed/loop/release, rendered video frames, HLS and DASH audio/video segment playback, URI rewriting, cache and byte-range behavior. All 13 APM unit tests passed.
- `:app:assembleDebug` and minified `:app:assembleBenchmark` succeeded. `:core:media:lintDebug` and `:app:lintDebug` reported no errors (the app reported existing/general warnings, including unsupported x86_64 native ABI coverage). Dependency inspection found no Media3 runtime dependency, and both APK DEX payloads contain no `androidx.media3` classes. The optimized benchmark APK was installed and cold-launched on device; an HTTPS sample rendered video and produced no `FfmpegPlayer` or Android runtime error logs, exercising JNI callbacks after R8.
- Screenshot of optimized playback: [FFmpeg player](screenshots/ffmpeg-player.png). APKs: [debug](../app/build/outputs/apk/debug/app-debug.apk) and [optimized benchmark](../app/build/outputs/apk/benchmark/app-benchmark.apk).
- Limits: decoding is software-only and packaged for ARM64/ARMv7; x86 ABIs, hardware decode and DRM are not provided. HTTP cache follows origin cache policy and does not promise offline playback for incomplete transfers. Picture-in-picture could not be exercised on this API 25 device.

## Equipment inspection example (2026-10-22)

- Added `feature/inspection/{api,domain,data,impl}` and a fourth navigation tab. The example saves equipment, location, notes, three checks, completion state and an optional document photo URI in its own Room database. Its version 1 schema is exported.
- With JDK 17 and the existing Gradle cache, `:feature:inspection:impl:compileDebugKotlin :app:compileDebugKotlin :checkModuleBoundaries` succeeded. `:app:compileReleaseKotlin` also succeeded. These commands compile code and do not package an APK/AAB.
- `:feature:inspection:data:lintDebug :feature:inspection:impl:lintDebug :app:lintDebug` succeeded. The new UI module's two performance hints about boxed Compose integer state were corrected; a second UI Lint run reported zero issues. Existing app warnings remain in its lint report.
- No automated tests or device run were performed for this example. Its photo access depends on the selected document provider retaining the item and read grant.

## Adaptive UI, shared dialogs, palettes, and app language (2026-10-22)

- Added live window width/height classes, responsive bottom/side navigation, bounded large-window content, an adaptive inspection grid, wrapping action rows, keyboard-aware scrolling, and edge-to-edge system-bar styling. Added reusable information, input, choice, and progress dialogs alongside the existing confirmation dialog.
- Settings now persists system/light/dark appearance and blue/green/purple palettes. It exposes system/Chinese/English app language through AppCompat, declares supported locales to Android, and updates the API language header. Main UI status and error strings were moved to resources; demo data and backend content remain data rather than translated UI copy.
- With JDK 17, `:app:compileDebugKotlin :app:compileReleaseKotlin :checkModuleBoundaries :core:designsystem:lintDebug :feature:settings:impl:lintDebug :feature:inspection:impl:lintDebug :app:lintDebug --offline --quiet` completed successfully. Additional Lint runs for home, feed, toolbox, media, auth, and status UI modules also succeeded. No checked module reported a Lint error; settings and inspection UI reported no issues, while several other modules retain warnings or hints. This verification compiled code and ran Lint; it did not build an APK/AAB or run unit/device tests.
- Device behavior remains unverified for actual folding hinges, IME and system navigation combinations, maximal font/display scaling, TalkBack, and the app-language activity recreation path. Those scenarios require a supported device or emulator matrix before release.
