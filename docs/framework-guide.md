# Framework integration guide

## Environments and release

`appEnvironment` accepts `development` (default), `staging`, and `production`. Non-production builds have a separate application identifier. `demoAuth=true` adds another `.demo` suffix, is activated only in debug builds, and is rejected for production. `apiBaseUrl` must use HTTPS and Retrofit requires a trailing slash. Never put API secrets in the URL.

Environment is selected at build time; the installed app cannot silently switch a production session to another server. Each variant must receive its own backend configuration. The debug tools display the application identifier and bounded request summaries. Release builds do not collect HTTP diagnostics or display the debug section.

Release shrinking and resource shrinking are enabled. Retrofit supplies consumer rules; signatures and annotations are retained. The `benchmark` application build uses release optimization with the local debug signing key to support installable smoke tests. Production signing uses all four environment variables: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Partial signing configuration fails early. The manual release workflow builds the production identifier and verifies the AAB signature; it does not publish the app.

Credential preferences, crash records, and downloaded files are excluded from cloud backup and device transfer. Add migration tests whenever a Room schema changes. Both databases export their version 1 schema; there is no existing schema upgrade requiring a migration in this change.

## Authentication

`AuthRemoteDataSource` is the backend boundary. Without an API or demo flag, authentication explicitly reports missing configuration. With an API configured, the reference adapter uses:

- `POST auth/login`, JSON `{ "username": "...", "password": "..." }`.
- `POST auth/refresh`, JSON `{ "refreshToken": "..." }`.
- Successful responses: `{ "accessToken": "...", "refreshToken": "..." }`.
- The reference protocol treats refresh HTTP 401/403 as invalid credentials. Adapt this decision to the actual server contract; temporary connectivity/server failures preserve the session.

Refresh uses a separate unauthenticated client. The protected client serializes refresh and reuses rotated tokens across concurrent 401s. Login requests carry a session generation so logout or a newer login invalidates an older request. Cancellation propagates. `SessionStore` permits test implementations; production storage uses Android Keystore AES/GCM.

Demo authentication is deliberately explicit and isolated. It demonstrates login/navigation/session behavior without proving any real backend integration. Replace the adapter/binding in `AuthBackendModule` for a different protocol.

## Network results

`apiCall` returns typed success, HTTP failure, network failure, or unexpected failure. `apiCallEmpty` accepts successful responses without bodies. `ApiErrorDecoder` defaults to the JSON fields `code` and `message` and can be replaced per API. Error body reading is bounded. `mapBusiness` maps a successful HTTP envelope into a business-level result without imposing a universal success code.

Debug records retain only request method, host, status, and elapsed time, capped at 50 records. They exclude URL paths/queries, headers, bodies, and tokens. User-facing business errors should be translated or mapped by the backend adapter/feature; static screen copy has Chinese and English resources.

## Common request headers

`NetworkClientFactory.create(..., commonHeadersProvider = provider)` accepts a thread-safe `CommonHeadersProvider`. It is invoked for each original same-origin API request, so language and other in-memory values can change without rebuilding Retrofit. Do not perform disk/network IO in the provider. Transparent retries and authentication follow-ups reuse the original request metadata.

The app injects the same `AppCommonHeadersProvider` into protected API and unauthenticated login/refresh clients. Defaults are `Accept: application/json`, `Accept-Language`, `X-App-Version`, `X-App-Version-Code`, `X-App-Platform: android`, `X-Android-Api` and `X-App-Environment`. Adapt field names to the backend contract. No device identifier is included.

Explicit endpoint headers take precedence, case insensitively. OkHttp validates header names/values; provide legal HTTP text. The provider rejects Authorization, Proxy-Authorization, Cookie, Host, Content-Type, Content-Length, Transfer-Encoding, Connection, Accept-Encoding, Range and If-Range. Authentication and body/media metadata remain managed by their dedicated layers.

The provider applies only to the configured scheme/host/port. A network interceptor strips provider-declared header names (including explicit overrides of those names) and Authorization on cross-origin redirects. External `@Url` requests do not receive the provider defaults. Media, image and transfer clients are intentionally independent; add backend-specific adapters when they need authenticated metadata.

## Remote pagination and offline writes

The feed example is separate from the existing local task list. It reads observable Room data, persists the remote cursor, protects pending local edits during refresh, and serializes page/sync operations. Its API is:

- `GET items?offset=0&limit=20` → `{ "items": [{ "id": "001", "title": "Example", "version": 1 }], "nextOffset": 20 }`.
- `nextOffset: null` ends pagination. Cursors must advance.
- `PUT items/{id}` receives `{ "id": "001", "title": "Edited", "version": 1 }` and returns the updated item/version.
- HTTP 409 returns the latest server item in the same item format.

Local edits are an outbox represented by pending Room rows. WorkManager schedules synchronization after a write and on application startup, retries transient failures with backoff, and leaves pending changes intact after exhausted retries. Manual sync remains available. UUID identifiers and version checking make replay safe with a matching backend. A conflict retains local text and the remote version until the user selects local or server data. Matching server text after a lost response is accepted as already synchronized.

When no API is configured, an explicitly labelled, separately persisted on-device demo server supplies 65 rows. It supports simulated offline mode and version conflicts. It does not represent a real network service. Configure a real API to exercise actual server pagination. The sample uses an explicit “load next page” action; the original local task sample demonstrates Paging 3. This feed uses a repository cursor rather than coupling its domain contracts to Paging or Room.

The example implements add/edit synchronization, not a universal multi-device merge engine. Extend its contract for deletions, account-scoped data, server-specific cursors, and entity-specific merge rules when a business feature requires them.

## Navigation and UI

Tasks, Examples, Settings, and Inspections own independent typed back stacks and saveable state containers. The shell derives `AppWindowInfo` from its current Compose constraints, so resizing, split screen, and rotation update layout without a device-model rule. Width classes are compact below 600 dp, medium below 840 dp, and expanded otherwise; a navigation rail is used when width is at least medium and height is not compact (480 dp or taller). Main content is centered with a 840/960 dp maximum on medium/expanded windows; fullscreen and PiP content bypass this limit. The inspection list uses an adaptive grid. Input pages scroll, keyboard-aware screens use IME padding, and action groups use wrapping rows. The Activity uses edge-to-edge system bars and IME resize behavior.

Shared components include form fields, submitting buttons, image loading/retry, list footers, snackbars, and page statuses. `core/designsystem/components` also exposes `AppConfirmDialog`, `AppInfoDialog`, `AppInputDialog`, `AppChoiceDialog`, and `AppProgressDialog`. Existing task, feed, inspection, and settings flows use the matching shared dialogs. Provide localized labels and keep confirmation or save work in the ViewModel. Business actions stay in ViewModels; screen drafts and scroll state use saveable Compose state. Task input clears only after a successful save, and duplicate submissions are blocked.

`NavigationResultKey<T>` declares a namespaced, serializable result contract. `Navigator.returnResult(key, value)` returns a typed value to the caller; call `consumeResult(key)` after handling it. The shell saves encoded results across recreation and clears them on logout. Use serializable navigation keys for structured screen arguments.

Supported deep links use an allowlisted route:

```text
wbase://open/toolbox
wbase://open/video
wbase://open/feed
wbase://open/settings
wbase://open/summary
```

Protected destinations still pass through the session gate. Incoming links wait for authentication restoration and the destination stack selection before navigating; cached screens observe the live authentication flow so logout updates immediately. Unknown routes are ignored. Notifications use the same destination handling. Add verified HTTPS App Links only after supplying an owned domain and its Digital Asset Links configuration.

Appearance mode is light, dark, or system; color palette is blue, green, or purple. Both persist in DataStore, and the existing boolean dark-mode preference migrates naturally. The settings page also offers system, Chinese, and English app language through `AppCompatDelegate`; `locales_config.xml` exposes those locales to the Android 13+ system language settings, while AppCompat persists the choice on older devices. Static UI strings are resolved from localized resources and API `Accept-Language` follows the selected app language. Existing sample Feed content, user data, and backend messages do not translate automatically. Localized form errors should remain resource IDs or error codes until rendered by the UI.

Window classes are a reusable layout rule, not a universal pixel-scaling solution. New screens should respond to available width/height, font scale, IME and system insets. There is no hinge-specific pane or occlusion handling yet; verify unfolded/folded layouts, large font and display scaling, narrow split windows, landscape, gesture/three-button navigation, TalkBack, and keyboard use on the supported device matrix.

## Permissions, images, files, notifications

`AppPermissionGate` handles a single runtime permission, denial/rationale, the system-settings route for permanent denial, and refresh after returning to the foreground. The notification demo uses it on Android 13+. Earlier devices use system notification settings without a runtime notification permission.

The file/image examples use the system document picker with persistable read grants when supported. The camera launches the system camera with a FileProvider URI restricted to `cache/camera/`; it does not request broad storage access. Cancelled selection does not replace the existing file. Private camera output is temporary and can be cleared by Android.

`AppImages.install()` is called at application startup and registers a shared Coil 3 ImageLoader. Memory cache is bounded to 20% of maximum heap, capped at 64 MiB; disk cache is 128 MiB under `cache/image_cache`. The dedicated unauthenticated OkHttp client uses 15-second connect, 30-second read and 60-second call timeouts. `AppNetworkImage` uses layout-sized decoding, crossfade, loading/retry states and automatic retry of failed images after an observed offline-to-online transition. It accepts `contentScale` and `cacheEnabled`; use `cacheEnabled = false` for sensitive images. Provide a meaningful description for informative images, and null for decorative ones.

`AppImages.inspect(context)` and `AppImages.clear(context)` are suspending APIs for cache inspection and clearing, demonstrated in Toolbox. Clearing does not remove already rendered images or cancel active requests; these may populate cache again. Cache keys follow Coil defaults: use versioned URLs for changed content. Default Coil disk caching does not implement HTTP Cache-Control expiry; authenticated image loading, account-specific cache namespaces and business invalidation need explicit adapters. Cache storage is temporary and can be evicted by Android.

## Background file transfers

`AppTransfers.enqueue(context, httpsUrl, uploadUri?)` schedules persistent work. Downloads are private files under `files/downloads`; uploads are raw `application/octet-stream` POST requests. Multipart forms and authentication require backend-specific adapters. Downloads support validated HTTP range resume. An `Idempotency-Key` equal to the work ID is reused across retries; upload servers must honor it to avoid duplicate processing.

Transfers are bounded to 100 MiB and an eight-minute call timeout, support progress/cancellation, retry IO errors, HTTP 429 and 5xx with exponential backoff, and fail permanent HTTP/input errors. Output is committed only after a successful, complete download. Downloads retain partial files on cancellation and transient failures. When a strong ETag or Last-Modified is available, retries send Range and If-Range; a full 200 response safely restarts the file. Invalid or unsatisfied ranges retry, with 416 discarding the stale partial file. Without a validator, downloads restart to avoid mixing remote versions. WorkManager waits for connectivity and retries with backoff; retry exhaustion requires manual resume. Use `AppTransfers.enqueue(context, url, resumeId = originalTaskId)` after the previous task finishes to reuse its partial file; retain the original task ID across subsequent resumes. Do not resume an active task concurrently. Retained partial files remain in private storage until resumed or app data is cleared. This is reliable scheduled work, not an unlimited foreground transfer service. Downloads remain private until you explicitly add an export/share flow.

## Media

The shared player supports progressive media, HLS, and DASH streaming, observable controls, lifecycle pause, audio focus, repeat, seeking, and speed. Position is saved per source and restored after recreation. Fullscreen switches orientation and hides shell/system controls. Lifecycle handling follows [Android’s PiP guidance](https://developer.android.com/develop/ui/views/picture-in-picture). PiP is offered only when Android and the device support it; leaving PiP into the background pauses playback.

Streaming cache is bounded and may be cleared by Android. Capacity changes wait for active players to close. Toolbox displays usage and clears the video cache only when no player is active. Offline downloading is separate: the example downloads direct MP4 files with WorkManager and plays the resulting private URI. HLS/DASH offline manifests, DRM, background audio, and media notification sessions are not implied by this MP4 download example.

## Diagnostics and performance

`core:apm` is a separate Android library module using the OpenTelemetry Android Agent and OTLP/HTTP. `AppApm.initialize(context, ApmConfiguration(...))` enables the Agent's default Android instrumentation when an HTTPS OTLP endpoint is configured. Manual APIs cover handled errors, structured logs, events, page views, counters, histogram measurements, breadcrumbs and synchronous/suspending business traces. Retrofit/OkHttp requests are instrumented through generic hooks in `core:network`; the public Coil image client, resumable file transfer worker and Media3 video upstream are also instrumented for HTTPS timing and transfer sizes without propagating trace headers to external hosts. Video cache hits are excluded from network transfer telemetry. The module samples process/device memory, CPU, battery, thermal state and storage while an Activity is foregrounded; disable or tune this with `ApmConfiguration`. The Agent buffers telemetry on disk while offline. `AppDiagnostics` remains in `core:platform` for local last-crash/startup diagnostics and delegates the fatal exception to the previously installed handler, so the Agent can capture it.

The module also records Activity first-draw time, foreground frame intervals and slow/frozen-frame windows, and runs a configurable main-thread watchdog that reports suspected stalls with the main-thread stack. This watchdog is a suspected-ANR signal and does not replace the system ANR trace. Room databases wired with `AppApm.roomQueryCallback()` emit SQL rule IDs for `SELECT *`, unbounded UPDATE/DELETE, AUTOINCREMENT, `INSERT OR REPLACE`, and leading-wildcard LIKE without exporting SQL or bind values. File and other controlled I/O can be wrapped with `AppApm.trackIo` or `trackFileIo` to record duration and main-thread/slow-I/O signals. The app's Debug variant includes LeakCanary for heap-dump based Activity/Fragment/View/ViewModel leak analysis; it is excluded from Release.

For post-build size inspection, run `python3 tools/apm/apk_inspector.py path/to/existing.apk --output apm-apk-report.json --html apm-apk-report.html`. It reads an existing APK and reports large entries, DEX method/class counts, ABI splits, stored compressible files, no-alpha PNGs, byte-identical entries and heuristic resource basename candidates absent from DEX strings. It does not build an APK, parse binary manifest metadata, prove resource reachability, or replace Android Lint/R8 analysis.

Methods annotated with `@ApmMonitoredMethod` are instrumented by the AGP ASM transform for duration, slow-method and failure events; constructors and suspend functions are excluded. `AppApm.inspectBitmap(bitmap, owner)` can compare live software Bitmap pixel content at explicit call sites, with a pixel cap and weak references. Debug startup enables StrictMode leaked-Closeable/Cursor checks. Runtime snapshots include Native heap size and process thread count. GWP-ASan can be selected with `-PapmGwpAsanMode=always` on supported Android versions; it is sampled native memory-safety detection, not allocation leak attribution. SQLite checks are SQL-pattern heuristics and do not run EXPLAIN or inspect indexes. APK resource candidates are also heuristic, not proof of reachability.

This is a client SDK integration layer, not a full hosted APM service. It does not implement exact jank stack attribution, system ANR trace capture, full native allocation hooks, MemGuard, continuous profiling, or session replay. Logcat bridging and automatic Compose click capture are not enabled; record product-specific actions explicitly with `AppApm.recordEvent`. File I/O diagnostics cover only operations explicitly wrapped by callers. Room checks cover databases configured with the callback. Breadcrumbs attach only to manually reported exceptions. Collector, dashboards, alerting, data retention and R8 symbolication need a backend or separate service integration. Thermal and power-save values are foreground snapshots, not change-event extensions. Media3 transfer metrics use a separate namespace because TransferListener does not expose HTTP success or failure.

Set `-PapmEndpoint=https://your-collector.example:4318` to configure the OTLP/HTTP collector base URL. The release workflow reads the non-secret repository variable `APM_ENDPOINT` and passes it as this Gradle property. An empty endpoint disables remote collection and does not block app startup. This repository does not include a collector, dashboard or credentials; deploy an OTLP-compatible backend. Never package long-lived collector secrets in the APK; put internet-facing collection behind an HTTPS gateway with rate limits and abuse protection. The agent can collect crash stack traces, app/device state and performance/network telemetry. Configure user disclosure/consent and attribute redaction to match the product privacy policy.

The debug startup number is an approximate process-to-first-frame diagnostic. Use the benchmark for repeatable startup/frame measurements:

```sh
./gradlew -PperformanceTests=true :benchmark:assembleBenchmark
./gradlew -PperformanceTests=true :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.will.mvvmbase.benchmark.StartupBenchmark
./gradlew -PperformanceTests=true :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.will.mvvmbase.benchmark.BaselineProfileGenerator
```

Performance tests require API 28+ and an appropriate physical device or rooted test emulator for baseline collection. The generator writes baseline artifacts under benchmark test outputs; inspect the generated profile and add the validated profile to `app/src/main/baseline-prof.txt` for distribution. No fabricated baseline or timing claim is bundled. Emulator measurements should be treated as regression indicators, not representative production latency.

The video download screen observes validated internet connectivity and shows offline/reconnected/retrying messages. Weak connection hints use measured download throughput below 50 KiB/s or eight seconds without progress; they are heuristics, not radio signal measurements.
