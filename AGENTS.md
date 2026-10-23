# Repository Guidelines

## Project Structure

This Kotlin and Jetpack Compose app is organized into Gradle modules. `app/` owns application startup, Hilt configuration, and the Navigation 3 back stack. Shared infrastructure lives in `core/common`, `core/network`, `core/designsystem`, `core/media`, and `core/navigation`. Put focused cross-feature helpers in `core/common`, shared UI in `core/designsystem`, player infrastructure in `core/media`, and keep network and navigation code in their dedicated modules. Each `feature/<name>/` may contain `api`, `domain`, `data`, and `impl` modules: keep navigation keys in `api`, framework independent contracts in `domain`, persistence and remote implementations in `data`, and screens, ViewModels, and entry builders in `impl`. Shared Gradle convention plugins live in `build-logic/`.

## Build and Test

Only build, package, run tests, run lint or other validation, or install, launch, or operate the app on a device/emulator when the user explicitly requests those actions. A request to add or change code does not authorize packaging or validation. By default, make the requested code changes only; do not initiate these actions autonomously.

Use JDK 17 and Android SDK API 36. Run commands from the repository root:

```sh
./gradlew build
./gradlew :core:network:test :feature:status:data:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest :feature:home:data:connectedDebugAndroidTest :feature:settings:data:connectedDebugAndroidTest
./gradlew lint
```

`build` compiles debug and release variants, runs unit tests, and checks lint. Instrumented tests require a connected device or emulator. GitHub Actions runs these checks for pushes and pull requests.

## Kotlin and Compose Style

Use four spaces for indentation, `UpperCamelCase` types, and `lowerCamelCase` members. Keep each public type in a focused file. Prefer stateless screen composables: collect state in route composables and pass state and callbacks into screens. Keep business operations in ViewModels and domain contracts. Do not introduce Data Binding. Declare dependency versions in `gradle/libs.versions.toml` and use catalog aliases.

## Persistence and Navigation

Features should communicate through API contracts or serializable navigation keys, not implementation dependencies. Register destinations through Hilt multibindings. Export Room schemas and add a migration with every schema change; cover persistence behavior with tests. Keep credentials out of source control and pass signing material through local environment variables or GitHub Actions secrets.

## Commits and Pull Requests

Use short imperative commit subjects, such as `Add profile feature`. PRs should describe user-visible changes, list verification commands and results, link related issues, and include screenshots for UI changes. Keep each change focused on one feature or infrastructure concern.
