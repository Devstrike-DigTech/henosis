# `henosis.yaml` reference

The config **selects** members of the SDK; every signature is read from the SDK itself.
Unknown keys are errors, and every error names the exact key path (`facades[0].methods[3]`).

```yaml
name: sample_sdk                  # base name; drives package names
version: 0.1.0
source:
  aar: ./fixtures/sample-sdk/build/outputs/aar/sample-sdk-release.aar
  # or: maven: com.squareup.okio:okio-jvm:3.18.2
  # repositories: [https://example.com/maven]   # extra repositories besides Google and Maven Central
facades:
  - class: com.devstrike.sample.Greeter
    as: Greeter                   # name exposed to Dart/TS (optional; default: simple class name)
    constructor: context          # none | context
    methods:
      - greet
      - greetMany
      - fetchProfile
      - divide(Int, Int)          # overload selection
      - divide(Double, Double) as divideDouble   # expose a second overload under another name
    events:
      - ticks                     # Flow<T> property or zero-argument fun returning Flow<T>
types:                            # data classes / enums reachable from facades are auto-included;
  include: []                     # list extras here
targets:
  flutter:
    package: sample_sdk
    org: com.devstrike
  react_native:
    package: "@devstrike/sample-sdk"
    module: SampleSdk
```

## Keys

| Key | Required | Meaning |
| --- | --- | --- |
| `name` | yes | Lowercase letters, digits and underscores, starting with a letter. Drives package names. |
| `version` | yes | Version of the generated packages. `heno doctor` warns if it is not `MAJOR.MINOR.PATCH`. |
| `source.aar` | one of | Path to a local `.aar`, relative to the config file. |
| `source.maven` | one of | `group:artifact:version`, resolved from Google, Maven Central and `source.repositories` into `~/.cache/henosis/m2` (override with `HENOSIS_CACHE`; `HENOSIS_OFFLINE` skips the public repositories). `.aar` is tried first, then `.jar`. A Kotlin Multiplatform umbrella artifact with no classes (`okio`) is parsed through its `-jvm`/`-android` sibling while the generated packages keep the coordinates you wrote. |
| `source.repositories` | no | Extra Maven repositories besides Google and Maven Central. Written into the generated build files too, so consumers resolve the SDK from the same place. |
| `facades[]` | yes | At least one class to expose. Each facade becomes a singleton in the generated plugin. |
| `facades[].class` | yes | Fully qualified name of a plain, non-generic public class. |
| `facades[].as` | no | Exposed name. Must be unique across facades and types. |
| `facades[].constructor` | no | `none` (public no-arg constructor, default) or `context` (one `android.content.Context` parameter; `applicationContext` is injected). |
| `facades[].methods[]` | no | Method selectors, see below. |
| `facades[].events[]` | no | Names of `Flow<T>` properties or zero-argument functions returning `Flow<T>`. |
| `types.include[]` | no | Extra data classes or enums to generate. Types used by selected members are included automatically, recursively through data-class fields. |
| `targets.flutter.package` | if flutter | Dart package name (`[a-z][a-z0-9_]*`). |
| `targets.flutter.org` | if flutter | Reverse-domain organisation for the Android plugin (`com.devstrike`). |
| `targets.react_native.package` | if rn | npm package name (`@scope/name` or `name`). |
| `targets.react_native.module` | if rn | TurboModule name in PascalCase. |

At least one target must be configured.

## Method selectors

| Selector | Meaning |
| --- | --- |
| `greet` | The only method named `greet`. An error if it is overloaded. |
| `divide(Int, Int)` | The overload whose parameter types render exactly like this (simple or fully qualified Kotlin names, `?` for nullable, generics allowed: `greetMany(List<String>)`). |
| `divide(Double, Double) as divideDouble` | The same, exposed under another name. Exposed names must be unique within a facade; events share that namespace. |

## What `heno doctor` checks

Errors stop generation; warnings do not.

- **source**: exactly one of `aar`/`maven`; the `.aar` exists, or the coordinates are well-formed and resolve.
- **name / targets**: valid identifiers and package names as above.
- **facades**: class exists, is a plain non-generic class, has the constructor the config asks for; selectors parse, resolve to exactly one method, and exposed names do not collide.
- **types**: every parameter, return, event-element and data-class field type is in the [v0.1 type table](type-mapping.md). Unsupported types are errors; `Long` and Java platform types are warnings. Data classes must keep a public primary constructor with one parameter per property.
- **nothing selected**: a facade with no methods and no events is a warning.

Each finding is one line: `error    facades[0].methods[3]: <message>  fix: <hint>`.
