# Type mapping (v0.1)

Every type that crosses the bridge maps to exactly one row below. Anything else is reported by
`heno doctor` (and tagged `unsupported` by `heno inspect`) with the member path and a fix hint.

| Kotlin / Java | Dart | TypeScript | Notes |
| --- | --- | --- | --- |
| `Int`, `Short`, `Byte` | `int` | `number` | |
| `Long` | `int` | `number` | **warn**: values above 2^53 lose precision in JavaScript |
| `Double`, `Float` | `double` | `number` | |
| `Boolean` | `bool` | `boolean` | |
| `String` | `String` | `string` | |
| `T?` | `T?` | `T \| null` | from Kotlin metadata; Java reference types are platform types, treated as nullable with a **warn** |
| `List<T>` | `List<T>` | `T[]` | |
| `Map<String, T>` | `Map<String, T>` | `Record<string, T>` | non-`String` keys are an **error** |
| `ByteArray` | `Uint8List` | `ArrayBuffer`* | *base64 `string` if TurboModule codegen cannot type it; decided in M4 |
| `data class` | immutable class + `copyWith`, `==`, `hashCode`, `toString` | `interface` (readonly fields) | crosses as a map |
| `enum class` | `enum` | string union | crosses as its name |
| `suspend fun` | `Future<T>` | `Promise<T>` | |
| `Flow<T>` | `Stream<T>` | `addListener(cb): Unsubscribe` | only as a property or a function return type (an event) |
| `Unit` | `void` / `Future<void>` | `void` / `Promise<void>` | |
| `android.content.Context` | — | — | only as a facade constructor parameter; Henosis injects `applicationContext` |
| anything else | — | — | **error** with the member path |

Sync (non-`suspend`) methods still return `Future` / `Promise` across the bridge in v0.1.

The rules are implemented once, in `henosis-core`
(`com.devstrike.henosis.core.types.TypeRules`), and every command and generator reads them from there.
