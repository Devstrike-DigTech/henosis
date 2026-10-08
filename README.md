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
| M2 | `henosis.yaml` config, IR + JSON schema, `heno init`, `heno doctor` | ⬜ Next |
| M3 | Kotlin bridge + Flutter target (plugin + example app) | ⬜ |
| M4 | React Native target (TurboModule + example app) | ⬜ |
| M5 | `heno verify`, Maven input, exception mapping, 3 public SDKs in `docs/compatibility.md` | ⬜ |
| M6 | Launch readiness: quickstart, docs, contributing guide, release workflow | ⬜ |

What is deliberately **not** in v0.1 is listed in [docs/roadmap.md](docs/roadmap.md).

## Requirements

- JDK 17 or newer
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

Inspect the sample SDK that ships with the repository (built automatically by the test suite):

```bash
henosis-cli/build/install/heno/bin/heno inspect fixtures/sample-sdk/build/outputs/aar/sample-sdk-release.aar
```

```bash
henosis-cli/build/install/heno/bin/heno inspect --json fixtures/sample-sdk/build/outputs/aar/sample-sdk-release.aar
```

`inspect` prints every public class and member and tags each one `ok`, `warn` or `unsupported`
according to the [v0.1 type mapping](docs/type-mapping.md), with a reason for every warning and error.

## Commands (v0.1)

| Command | Behaviour | Available |
| --- | --- | --- |
| `heno inspect <sdk> [--json]` | Print the public API tree; each member tagged `ok`, `warn`, `unsupported` | M1 |
| `heno init <aar-or-coords>` | Parse the SDK and write a starter `henosis.yaml` | M2 |
| `heno doctor` | Validate config and types; exit code 1 on any error | M2 |
| `heno generate [--target flutter,rn] [--out dir] [--emit-ir]` | Run doctor, then write the packages | M3, M4 |
| `heno verify` | Build every generated example app and report pass/fail per target | M5 |

Exit codes: `0` ok, `1` validation error, `2` toolchain missing, `3` build failed.

## Repository layout

```
henosis-cli/                 Clikt commands, wiring only
henosis-core/                API model, type system, inspect report (config, IR and doctor in M2)
henosis-parser-android/      AAR/Maven → API model (ASM + Kotlin metadata)
henosis-gen-bridge/          IR → Kotlin bridge classes (M3)
henosis-gen-flutter/         IR → Flutter plugin package (M3)
henosis-gen-react-native/    IR → React Native TurboModule package (M4)
henosis-verify/              builds generated example apps (M5)
fixtures/sample-sdk/         our own Android library covering every v0.1 type (a separate, included Gradle build)
golden/                      expected output for snapshot tests
docs/                        type-mapping.md, roadmap.md (ir.schema.json, config.md from M2)
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

Conventions: Kotlin (JVM 17), ktlint, JUnit 5 + Kotest assertions, conventional commit messages
(`feat(parser): ...`). Output is deterministic: same input, byte-identical output, no timestamps.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). © Devstrike Digital Limited.
