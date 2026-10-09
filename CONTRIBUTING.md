# Contributing to Henosis

Thanks for helping. This page is the short version of how the project works; the long version is
the code, which is small on purpose.

## Ground rules

- **Clean room.** Never contribute code, assets, names or internal knowledge from an employer or
  client. Fixtures and demos use our own sample SDK or public open-source SDKs only.
- **Deterministic output.** Same input, byte-identical output. Sort everything, no timestamps.
- **Fail loudly.** Unsupported input is reported by `heno doctor` with the member and a fix hint.
  Generation never silently drops a member.
- **Scope discipline.** Anything outside v0.1 goes to `docs/roadmap.md`, not into the code.
- **Current versions.** Verify a dependency's current stable version before adding it; do not rely
  on remembered numbers. Record it in `gradle/libs.versions.toml`.

## Setting up

Requirements: JDK 17+ and the Android SDK (platform 36, build-tools 36.0.0) with `ANDROID_HOME`
set, or `fixtures/sample-sdk/local.properties` containing `sdk.dir=...`. Flutter 3.47+ and Node 22+
with Corepack are only needed to build generated packages.

```bash
./gradlew build
```

This compiles every module, builds the sample SDK (`fixtures/sample-sdk`, an included Gradle
build on the Android Gradle Plugin), runs ktlint, and runs every unit and golden test. The CLI is
installed with:

```bash
./gradlew :henosis-cli:installDist
```

## Project layout

| Module | Role |
| --- | --- |
| `henosis-core` | Config model, API model, type rules, IR model, doctor, inspect/init renderers, `CodeWriter` |
| `henosis-parser-android` | `.aar`/`.jar`/Maven → API model (ASM, Kotlin metadata, Maven Resolver) |
| `henosis-gen-bridge` | IR → Kotlin bridge classes shared by every Android target (KotlinPoet) |
| `henosis-gen-flutter` | IR → Flutter plugin package with example app |
| `henosis-gen-react-native` | IR → React Native TurboModule package with example app |
| `henosis-verify` | Builds generated example apps |
| `henosis-cli` | Clikt commands, wiring only |

See `docs/architecture.md` for how data flows between them.

## Tests and golden files

Every generator has golden-file snapshot tests under `golden/`: the expected output for the sample
SDK, compared byte for byte. When you change a parser or generator on purpose, regenerate the
goldens **in the same commit** and review the diff:

```bash
./gradlew updateGolden
```

```bash
git diff --stat golden
```

Unit tests use JUnit 5 with Kotest assertions. They never touch the network: Maven tests use a
file-based repository fixture (see `MavenResolverTest`).

Generated output is validated against the real toolchains in the e2e workflow (`dart analyze
--fatal-infos`, `flutter test`, `tsc --noEmit`, debug APK builds through `heno verify`). Run the
same locally with:

```bash
heno generate -c fixtures/henosis.yaml --out henosis-out && heno verify -c fixtures/henosis.yaml --out henosis-out
```

## Code style

- Kotlin (JVM 17), ktlint official style, 140 columns. `./gradlew ktlintFormat` fixes most things.
- Public functions in `henosis-core` get KDoc.
- The IR is target-agnostic: no Flutter or React Native concepts inside `henosis-core`.
- Generated Kotlin uses a `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` owned by
  the plugin/module and cancelled on detach; results are delivered on the main thread.

## Commits and pull requests

- Small, focused commits with conventional messages: `feat(parser): ...`, `fix(gen-flutter): ...`,
  `docs: ...`, `chore(build): ...`.
- A pull request should say what changed, why, and how it was verified. Generator changes include
  their golden updates; parser changes usually need a new member in `fixtures/sample-sdk`.
- CI must be green: `ci.yml` (unit + golden) and `e2e.yml` (generated packages build).

## Adding a target generator

1. Create `henosis-gen-<target>` applying `id("henosis.kotlin-module")` with `api(project(":henosis-core"))`
   and, for Android targets, `implementation(project(":henosis-gen-bridge"))`.
2. Implement `generate(ir: Ir, context: GeneratorContext): GeneratedPackage`, reading only the IR.
   Use `CodeWriter` for non-Kotlin files and the bridge generator for Kotlin.
3. Mirror the framework's own scaffolding tool for templates (`flutter create`,
   `create-react-native-library`) and record the versions in a `<Target>Toolchain` object.
4. Add a golden test that generates from `golden/ir/sample-sdk.json` and compares the tree.
5. Wire it into `GenerateCommand` and `VerifyCommand`, and add an e2e job that builds the output.

## Reporting SDK compatibility

The most useful report is a public SDK that does not bind. Run `heno inspect` and `heno doctor`
and paste their output into the "SDK compatibility" issue template; the results table in
`docs/compatibility.md` grows from these.
