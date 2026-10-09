# Changelog

All notable changes to Henosis are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-10-09

First public release.

### Added

- `heno init`, `inspect`, `doctor`, `generate` and `verify`.
- Android input from a local `.aar`/`.jar` or Maven coordinates (Apache Maven Resolver), with
  Kotlin Multiplatform umbrella artifacts parsed through their `-jvm`/`-android` sibling.
- Parser over ASM and Kotlin metadata: nullability, `suspend`, data classes, enums, default
  arguments, Java platform types, and `@Deprecated` levels from bytecode.
- `henosis.yaml` with path-precise errors, method selectors for overloads (`divide(Int, Int)`,
  `... as alias`), and a doctor that reports one line per finding with a fix hint.
- A target-agnostic IR with a published JSON Schema (`docs/ir.schema.json`).
- Flutter target: plugin over `MethodChannel`/`EventChannel`, immutable Dart data classes, enums,
  `HenosisException`, an example app, and a generated test proving the error mapping.
- React Native target: New Architecture TurboModules with Codegen specs, typed TypeScript facades,
  `HenosisError`, events via `NativeEventEmitter`, `ArrayBuffer` for bytes, and an example app.
- A Kotlin bridge shared by both Android targets (KotlinPoet).
- `heno verify` building the generated example apps with the documented exit codes.
- Compatibility runs for Okio, osmdroid and ZXing (`docs/compatibility.md`).

[Unreleased]: https://github.com/devstrike/henosis/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/devstrike/henosis/releases/tag/v0.1.0
