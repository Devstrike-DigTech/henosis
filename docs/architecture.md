# Architecture

Henosis is a pipeline with one shared representation in the middle. Everything to the left of the
IR knows about Kotlin and the JVM; everything to the right knows about one framework.

```
.aar / Maven ──► Parser ──► Facade selection + doctor ──► IR (JSON)
                                                          │
                     ┌────────────────────┬───────────────┼────────────────────┐
                     ▼                    ▼               ▼                    ▼
              Kotlin bridge        Flutter target   React Native target   (MAUI, v0.2)
                     └────────────────────┴───────────────┴──► heno verify (builds example apps)
```

## Stages

### 1. Parse: archive → API model (`henosis-parser-android`)

`AndroidSdkParser` opens the `.aar` (its `classes.jar`) or `.jar` and reads every public class with
ASM. Kotlin classes are read through their `@kotlin.Metadata` annotation (`kotlin-metadata-jvm`),
which is the only source of nullability, `suspend`, data-class and enum kinds, default arguments
and Kotlin visibility. Java classes fall back to descriptors and generic signatures; their reference
types become platform types (nullable, with a warning). `@Deprecated` levels come from bytecode
because Kotlin metadata does not carry them. Maven coordinates are downloaded by `MavenResolver`
first.

The output is the `ApiModel` (`henosis-core/.../api`): facts only, sorted, no judgement.

### 2. Judge: type rules (`henosis-core/.../types`)

`TypeRules` implements the v0.1 type table once. `Inspector` runs it over every member to produce
`heno inspect`'s `ok`/`warn`/`unsupported` tags; the doctor runs the same rules over the members a
config selects. If a type is not in the table, nothing downstream ever sees it.

### 3. Select and build the IR (`henosis-core/.../doctor`)

`IrBuilder` resolves `henosis.yaml` against the API model in a single pass: it checks names,
targets, constructors, method selectors, events and reachable data classes, collecting a
`DoctorFinding` for every rule that fails, and builds the `Ir` only when there are no errors.
`doctor` and `generate` share this code, so they cannot disagree.

The IR (`henosis-core/.../ir`) is a closed vocabulary: facades with methods and events, data classes
and enums, a type reference with fourteen kinds, the SDK source coordinates and the targets. It is
serialised to `ir.json` and described by `docs/ir.schema.json`. It contains JVM names and
descriptors (so the bridge can call the SDK) but no framework concepts.

### 4. Generate (`henosis-gen-*`)

Each target generator reads only the IR and returns a `GeneratedPackage`: text files, embedded
binaries (the Gradle wrapper jar) and files to copy (the SDK `.aar`). Writing to disk happens in
one place, so generation itself is pure and testable against golden trees.

- `henosis-gen-bridge` (KotlinPoet) emits the Kotlin that both Android targets share: one
  `<Facade>Bridge` per facade owning the SDK singleton and exposing
  `suspend fun invoke(method, args: Map<String, Any?>): Any?` and `fun event(name): Flow<Any?>`,
  plus `HenosisCodecs` for data classes and bytes. All argument decoding and result encoding lives
  here; framework glue only moves `Any?` values and maps exceptions.
- `henosis-gen-flutter` emits the Dart API (singleton facades over `MethodChannel` and
  `EventChannel`, immutable data classes, enums, `HenosisException`), the `FlutterPlugin` glue, an
  example app and a test. Templates mirror `flutter create`.
- `henosis-gen-react-native` emits one Codegen spec and one Kotlin TurboModule per facade, a
  `BaseReactPackage`, typed TypeScript facades, `HenosisError`, events through
  `NativeEventEmitter`, and an example app. Templates mirror `create-react-native-library` and the
  React Native app template.

Framework templates are copied from the frameworks' own scaffolding tools and pinned in a
`<Target>Toolchain` object, so a generated package builds exactly like a hand-created one.

### 5. Verify (`henosis-verify`)

`Verifier` runs the real toolchains (`flutter build apk --debug`, `yarn typecheck`,
`./gradlew assembleDebug`) on the generated example apps and reports pass/fail per target.

## Runtime shape of a generated package

```
Dart / TypeScript facade  ──channel──►  framework glue  ──Any?──►  <Facade>Bridge  ──typed──►  SDK
   decodes results                      maps exceptions              decodes args
   encodes arguments                    runs coroutines              encodes results
```

- Each facade is a singleton created lazily on first use (`constructor: none`) or with the
  application context (`constructor: context`).
- Calls run in a `SupervisorJob + Dispatchers.Main.immediate` scope owned by the plugin/module and
  cancelled when the engine detaches; `suspend` functions suspend there.
- A native `Throwable` becomes `HenosisException(code, message)` / `HenosisError`, where `code` is
  the exception's simple class name.
- `Flow<T>` events are collected only while a listener exists.
- Values cross as channel-friendly primitives: numbers, strings, booleans, lists, string-keyed maps,
  data classes as maps, enums as names, bytes as `ByteArray` (Flutter) or base64 (React Native).

## Determinism

Classes, members, files and imports are sorted; no file carries a timestamp; version numbers come
from one place (`gradle.properties` → `HenosisVersion`). The golden tests under `golden/` compare
every generated file byte for byte, and a dedicated test generates twice and compares the results.

## Adding a target

A new framework is a new `henosis-gen-<target>` module with a generator that reads the IR, a
`<Target>Toolchain` with verified versions, a golden test, and hooks in `generate` and `verify`.
See `CONTRIBUTING.md` for the steps.
