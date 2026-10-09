# Roadmap

Everything here is **out of scope for v0.1** and lives in this file, not in the code
(CLAUDE.md, hard rule 5). Items move out of this list only when a milestone picks them up.

## Deferred from v0.1

| Area | Deferred item | Notes |
| --- | --- | --- |
| Input | iOS input (XCFramework / Swift) | v0.3 |
| Target | .NET MAUI target | v0.2, built on the Native Library Interop pattern |
| UI | Native UI views (platform views, camera previews) | v0.5 |
| Lifecycle | Multiple instances / object handles | v0.1 is singleton-only per facade class |
| Callbacks | Callbacks from Dart/TS into native beyond events | v0.5 |
| Types | Generics beyond `List`, `Map<String, *>`, `Flow` | `heno doctor` reports them as errors |
| Async | Cancellation of in-flight `suspend` calls | v0.2 |
| Distribution | Publishing to pub.dev / npm | v0.5 |
| Automation | GitHub Action that regenerates and opens a PR on each native release | v0.4 |
| Facades | Static facades (`companion object` / Java `static` entry points) | found in Timber, Gson, Glide (docs/compatibility.md) |
| Facades | Constructor arguments beyond `Context` | found in ZXing `BitMatrix`, osmdroid `GeoPoint` |
| Input | Read Gradle module metadata (`.module`) to follow Kotlin Multiplatform umbrella artifacts | found in Okio |
| Verify | Run the generated example apps on an emulator (instrumented end-to-end) | v0.2 |

## Generator notes (observed while building M3)

- A local `.aar` is shipped inside the generated plugin as a one-artifact Maven repository
  (`android/libs/maven`), because Android library modules cannot depend on a bare `.aar` file.
  Its transitive dependencies are unknown from the archive alone; the generated plugin adds
  `kotlinx-coroutines-android` (which the bridge needs anyway) and nothing else. With
  `source.maven` (M5) the generated build files depend on the coordinates directly and Gradle
  resolves the SDK's own dependencies from its POM.
- Generated Dart data classes compare `List` and `Map` fields one level deep (`listEquals` /
  `mapEquals`); nested collections compare by identity.
- Synchronous SDK methods run on the Android main thread inside the plugin's coroutine scope.
  Dispatching them to a background dispatcher is a v0.2 option.
- The generated plugin talks to the engine through `MethodChannel`/`EventChannel` directly; the
  `plugin_platform_interface` pattern (needed for federated plugins) is not generated.

## React Native notes (observed while building M4)

- Codegen cannot type binary data, so `ByteArray` crosses the bridge as a base64 string and the
  generated TypeScript wrapper converts to and from `ArrayBuffer` (the choice CLAUDE.md allows).
  The shared Kotlin codec accepts both raw bytes (Flutter) and base64 (React Native).
- Events use `NativeEventEmitter` with the spec's `addListener`/`removeListeners`; the native side
  collects every event flow while at least one listener exists. Codegen's newer typed
  `EventEmitter<T>` spec members are a v0.2 option.
- The generated package pins TypeScript 6 because React Native 0.87's tooling is validated on it,
  not on TypeScript 7.
- One TurboModule is registered per facade (`<Module><Facade>`), which keeps Codegen specs typed
  per facade at the cost of one native module per class.

## Hardening notes (observed while building M5)

- Maven resolution uses the POM only. Kotlin Multiplatform "umbrella" artifacts (`okio`) carry
  their JVM classes in a sibling (`okio-jvm`) selected through Gradle module metadata, which
  Resolver does not read; `heno` falls back to the `-jvm` and `-android` siblings for parsing and
  keeps the umbrella coordinates in the generated build files. Reading `.module` metadata for
  other variant names is a v0.2 option.
- `heno verify` builds debug variants only and needs the toolchains on the PATH (or `--flutter`
  / `--yarn`); it does not run the apps. Instrumented end-to-end runs on an emulator are v0.2.
- Static members (Kotlin `companion object`, Java `static`) and classes without a no-argument or
  `Context` constructor are reported by `heno inspect`/`doctor` and cannot be facades in v0.1;
  this is what blocks most of ZXing's and Timber's entry points (see docs/compatibility.md).

## Parser notes (observed while building M1)

- Top-level Kotlin functions (file facades such as `FooKt`) are not listed by `heno inspect`;
  v0.1 facades are classes.
- Static members (`companion object` with `@JvmStatic`, Java `static`) are skipped; a facade is an
  instance.
- Java inner-class and enum constructors fall back to erased descriptors when the generic
  signature omits synthetic parameters.
