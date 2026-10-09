# `heno` command reference

`heno` is the Henosis CLI. Every command prints human-readable lines; errors go to stderr with the
prefix `error    `. Exit codes are the same everywhere:

| Code | Meaning |
| --: | --- |
| 0 | ok |
| 1 | validation error (config, doctor findings, missing files) |
| 2 | toolchain missing (`verify` could not find `flutter` or `yarn`) |
| 3 | build failed (`verify`) |

Global: `--version` / `-V`, `--help` / `-h` (also on every subcommand).

## `heno init <sdk> [options]`

Parses the SDK and writes a starter `henosis.yaml` with every bindable class listed and commented
out, overloads already spelled as selectors, and `warn`/`unsupported` notes per member.

| Argument / option | Meaning |
| --- | --- |
| `<sdk>` | Path to an `.aar`/`.jar`, or Maven coordinates `group:artifact:version` |
| `--repo <url>` | Extra Maven repository, repeatable (coordinates only) |
| `-o, --out <file>` | Where to write (default `henosis.yaml`) |
| `--name <name>` | Base name (default: derived from the archive or artifact name) |
| `--force` | Overwrite an existing file |

## `heno inspect [sdk] [options]`

Prints the SDK's public API tree. Each class and member is tagged `[ok]`, `[warn]` or
`[unsupported]` with the reasons indented below it.

| Argument / option | Meaning |
| --- | --- |
| `[sdk]` | Archive to inspect; defaults to the `source` of the config |
| `-c, --config <file>` | Config to take the source from (default `henosis.yaml`) |
| `--json` | Machine-readable report |

## `heno doctor [options]`

Validates the config against the SDK: one line per finding, `error` or `warning`, with the config
key path and a fix hint. Exit code 1 if there is any error. See `docs/config.md` for the rules.

| Option | Meaning |
| --- | --- |
| `-c, --config <file>` | Config file (default `henosis.yaml`) |

```
error    facades[0].methods[1]: 'divide' has 2 overloads; selector is ambiguous: divide(Double, Double) | divide(Int, Int)  fix: add the parameter types, e.g. divide(Double, Double)
warning  facades[0].methods[3]: com.devstrike.sample.Greeter.fetchProfile parameter 'id': Long values above 2^53 lose precision in JavaScript (React Native maps Long to number)
doctor: 1 error(s), 1 warning(s)
```

## `heno generate [options]`

Runs doctor, then writes the packages. Nothing is written when doctor reports an error.

| Option | Meaning |
| --- | --- |
| `-c, --config <file>` | Config file (default `henosis.yaml`) |
| `-t, --target <list>` | `flutter`, `rn`, or both comma-separated (default: every target in the config) |
| `-o, --out <dir>` | Output directory (default `henosis-out`) |
| `--emit-ir` | Also write `<out>/ir.json` |

Output layout:

```
henosis-out/
├── ir.json                          (with --emit-ir)
├── flutter/<package>/               pubspec.yaml, lib/, android/, example/, test/
└── react-native/<package>/          package.json, src/, android/, example/
```

## `heno verify [options]`

Builds the generated example apps and reports `PASS`, `FAIL` or `SKIP` per target. For Flutter:
`flutter pub get` then `flutter build apk --debug`. For React Native: `yarn install` (if
`node_modules` is missing), `yarn typecheck` (`tsc --noEmit`), then `./gradlew assembleDebug`.

| Option | Meaning |
| --- | --- |
| `-c, --config <file>` | Config file (default `henosis.yaml`) |
| `-t, --target <list>` | Targets to verify (default: every target in the config) |
| `-o, --out <dir>` | Directory `generate` wrote to (default `henosis-out`) |
| `--flutter <bin>` | Flutter executable (default `flutter` on the PATH) |
| `--yarn <bin>` | Yarn executable (default `yarn` on the PATH) |
| `-v, --verbose` | Stream the build output |

## Environment

| Variable | Effect |
| --- | --- |
| `ANDROID_HOME` | Required by the generated Android builds (and by the sample SDK fixture) |
| `HENOSIS_CACHE` | Cache root for Maven downloads (default `~/.cache/henosis`) |
| `HENOSIS_OFFLINE` | Skip Google and Maven Central; only `source.repositories` are searched |
