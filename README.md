# Henosis

**One source. Every platform.**

Henosis (Greek ἕνωσις, "oneness") is an open-source CLI that takes a native **Android SDK**
(an `.aar` file or Maven coordinates) plus a small `henosis.yaml`, and generates ready-to-run
cross-platform packages:

- a **Flutter plugin** (Dart API, Android plugin glue, example app)
- a **React Native module** (New Architecture TurboModule, TypeScript API, example app)

One config, one command (`heno`), no hand edits.

**Facade-first.** You list only the classes and methods you want exposed. Henosis reads their real
signatures from the SDK's bytecode and Kotlin metadata, builds one intermediate representation
(IR), and every target generator reads only the IR. A new framework is a new generator, not a new
tool.

```
.aar / Maven ──► Parser ──► Facade selection + doctor ──► IR (JSON)
                                                          │
                     ┌────────────────────┬───────────────┼────────────────────┐
                     ▼                    ▼               ▼                    ▼
              Kotlin bridge        Flutter target   React Native target   (MAUI, v0.2)
                     └────────────────────┴───────────────┴──► heno verify (builds example apps)
```

## Status

Henosis is pre-release. Work happens one milestone at a time; this table is the source of truth.

| Milestone | Scope | Status |
| --- | --- | --- |
| M1 | Gradle multi-module skeleton, sample SDK fixture, `heno --version`, `heno inspect` with golden test | ✅ Done |
| M2 | `henosis.yaml` config, IR + JSON schema, `heno init`, `heno doctor`, `generate --emit-ir` | ✅ Done |
| M3 | Kotlin bridge + Flutter target (plugin + example app) | ✅ Done |
| M4 | React Native target (TurboModule + example app) | ✅ Done |
| M5 | `heno verify`, Maven input, exception mapping, 3 public SDKs in `docs/compatibility.md` | ⬜ Next |
| M6 | Launch readiness: quickstart, docs, contributing guide, release workflow | ⬜ |

What is deliberately **not** in v0.1 is listed in [docs/roadmap.md](docs/roadmap.md).

## Requirements

- JDK 17 or newer
- Flutter 3.47.6 or newer, only to build the generated Flutter packages
- Node 22+ with Corepack (Yarn 4), only to build the generated React Native packages
- The Android SDK (platform 36, build-tools 36.0.0) with `ANDROID_HOME` set, or a
  `fixtures/sample-sdk/local.properties` containing `sdk.dir=...`. It is only needed to build the
  sample SDK fixture that the tests run against.

## Try it

```bash
./gradlew :henosis-cli:installDist
```

```bash
henosis-cli/build/install/heno/bin/heno --version
```

The repository ships a sample SDK (built automatically by the test suite) and a config for it,
[fixtures/henosis.yaml](fixtures/henosis.yaml). Inspect the SDK's public API:

```bash
henosis-cli/build/install/heno/bin/heno inspect fixtures/sample-sdk/build/outputs/aar/sample-sdk-release.aar
```

`inspect` prints every public class and member and tags each one `ok`, `warn` or `unsupported`
according to the [v0.1 type mapping](docs/type-mapping.md), with a reason for every warning and error.

Validate the config against the SDK, one line per finding with a fix hint:

```bash
henosis-cli/build/install/heno/bin/heno doctor -c fixtures/henosis.yaml
```

Generate both packages (and `ir.json`, the intermediate representation every generator reads):

```bash
henosis-cli/build/install/heno/bin/heno generate -c fixtures/henosis.yaml --emit-ir --out henosis-out
```

The Flutter plugin lands in `henosis-out/flutter/sample_sdk/` and the React Native package in
`henosis-out/react-native/sample-sdk/`, each with an `example/` app that calls every binding.
Pass `--target flutter` or `--target rn` for one of them.

```bash
cd henosis-out/flutter/sample_sdk/example && flutter pub get && flutter build apk --debug
```

```bash
cd henosis-out/react-native/sample-sdk && yarn install && yarn typecheck && cd example/android && ./gradlew assembleDebug
```

To start from your own SDK, let `heno init` write a starter config with every public class
listed and commented out, then uncomment what you want to expose
([config reference](docs/config.md)):

```bash
henosis-cli/build/install/heno/bin/heno init path/to/your-sdk.aar
```

## Commands (v0.1)

| Command | Behaviour | Available |
| --- | --- | --- |
| `heno inspect [sdk] [--json]` | Print the public API tree; each member tagged `ok`, `warn`, `unsupported` | M1 |
| `heno init <aar>` | Parse the SDK and write a starter `henosis.yaml` (Maven coordinates in M5) | M2 |
| `heno doctor [-c henosis.yaml]` | Validate config and types; exit code 1 on any error | M2 |
| `heno generate [--target flutter,rn] [--out dir] [--emit-ir]` | Run doctor, then write the packages; `--emit-ir` writes `ir.json` | M3, M4 |
| `heno verify` | Build every generated example app and report pass/fail per target | M5 |

Exit codes: `0` ok, `1` validation error, `2` toolchain missing, `3` build failed.

## Repository layout

```
henosis-cli/                 Clikt commands, wiring only
henosis-core/                config model, API model, type system, IR model, doctor rules, inspect/init renderers
henosis-parser-android/      AAR/Maven → API model (ASM + Kotlin metadata)
henosis-gen-bridge/          IR → Kotlin bridge classes shared by every Android target (KotlinPoet)
henosis-gen-flutter/         IR → Flutter plugin package with example app
henosis-gen-react-native/    IR → React Native TurboModule package with example app
henosis-verify/              builds generated example apps (M5)
fixtures/sample-sdk/         our own Android library covering every v0.1 type (a separate, included Gradle build)
fixtures/henosis.yaml        config for the sample SDK
golden/                      expected output for snapshot tests
docs/                        config.md, type-mapping.md, ir.schema.json, roadmap.md
```

## Developing

```bash
./gradlew build
```

Golden files under `golden/` are compared byte-for-byte. When a change to the parser or a
generator is intended, regenerate them and review the diff:

```bash
./gradlew updateGolden
```

The e2e workflow (`.github/workflows/e2e.yml`) generates both packages from the sample SDK. For
Flutter it runs `dart analyze --fatal-infos` and builds the example's debug APK with Flutter 3.47.6;
for React Native it runs `tsc --noEmit` and builds the example's debug APK with React Native 0.87.

Conventions: Kotlin (JVM 17), ktlint, JUnit 5 + Kotest assertions, conventional commit messages
(`feat(parser): ...`). Output is deterministic: same input, byte-identical output, no timestamps.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). © Devstrike Digital Limited.
