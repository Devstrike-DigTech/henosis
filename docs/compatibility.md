# Compatibility

Three public, open-source Android SDKs run through the full loop with **zero hand edits**:
`heno init <coordinates>` → edit the facade list → `heno doctor` → `heno generate` → `heno verify`.
The configs are checked in under `fixtures/compat/` so every row can be reproduced:

```bash
henosis-cli/build/install/heno/bin/heno generate -c fixtures/compat/okio/henosis.yaml --out henosis-out/okio
henosis-cli/build/install/heno/bin/heno verify   -c fixtures/compat/okio/henosis.yaml --out henosis-out/okio
```

Run on 2026-10-09 with Henosis 0.1.0, Flutter 3.47.6, React Native 0.87.1, Node 24, JDK 17.

| SDK | Coordinates | Packaging | Public classes | Facade | Methods bound | doctor | Flutter (`flutter build apk --debug`) | React Native (`tsc`, `assembleDebug`) |
| --- | --- | --- | --: | --- | --: | --- | --- | --- |
| Okio | `com.squareup.okio:okio:3.18.2` | Kotlin, `.jar` (classes in `okio-jvm`) | 44 | `okio.Buffer` | 11 | 0 errors, 2 warnings (`Long`) | PASS | PASS |
| osmdroid | `org.osmdroid:osmdroid-android:6.1.20` | Java, `.aar` | 265 | `org.osmdroid.util.BoundingBox` | 15 | 0 errors, 0 warnings | PASS | PASS |
| ZXing core | `com.google.zxing:core:3.5.4` | Java, `.jar` | 172 | `com.google.zxing.common.BitArray` | 14 | 0 errors, 0 warnings | PASS | PASS |

## What each SDK taught us

### Okio (`okio.Buffer`)

- The artifact `com.squareup.okio:okio` is a Kotlin Multiplatform umbrella whose JVM classes live
  in `okio-jvm`, selected by Gradle module metadata that Maven Resolver does not read. Henosis
  notices the empty archive, parses the `-jvm` (or `-android`) sibling, and keeps the umbrella
  coordinates in the generated build files. That last part matters: React Native itself depends
  on `com.squareup.okio:okio:2.9.0`, and only the umbrella coordinate lets Gradle align the two
  versions; declaring `okio-jvm:3.18.2` directly produced duplicate `okio.*` classes in the example
  app. This was the second defect the compatibility runs found and fixed.
- `Buffer.size()` is `@Deprecated(level = ERROR)` ("moved to val"). Kotlin metadata still lists it
  as a function, and the first generated bridge called it, which the Kotlin compiler refused. Henosis
  now reads `@Deprecated` levels from bytecode: `doctor` rejects ERROR/HIDDEN members and flags
  WARNING ones. This was the first defect the compatibility runs found and fixed.
- Methods returning `okio.Buffer`, `ByteString`, `InputStream` or taking `Charset` are reported as
  unsupported types, so the bindable surface is the primitive reading API (`readUtf8()`,
  `readInt`, `readByteArray()`, `exhausted`, `skip`, ...). Writing (`writeUtf8` returns `Buffer`
  for chaining) is not bindable in v0.1.

### osmdroid (`org.osmdroid.util.BoundingBox`)

- A plain Java AAR with a no-argument constructor and `double` getters/setters binds cleanly with no
  warnings: primitives carry no nullability ambiguity. Overloads such as `contains(double, double)`
  versus `contains(IGeoPoint)` are selected as `contains(Double, Double)`.
- Everything taking or returning osmdroid's own classes (`GeoPoint`, `IGeoPoint`, `MapView`) is out
  of scope until object handles arrive (roadmap).

### ZXing core (`com.google.zxing.common.BitArray`)

- `BitArray` (bits in, bits out) binds fully. The entry points most people want do not, and
  `doctor` says exactly why (`fixtures/compat/zxing/henosis-unsupported.yaml`):

```
error    facades[0].methods[0]: com.google.zxing.qrcode.QRCodeWriter.encode return type: type 'com.google.zxing.common.BitMatrix' is not supported in v0.1; see docs/type-mapping.md for the supported set
error    facades[1].methods[0]: com.google.zxing.MultiFormatReader.decode parameter 'image': type 'com.google.zxing.BinaryBitmap' is not supported in v0.1; see docs/type-mapping.md for the supported set
error    facades[1].methods[0]: com.google.zxing.MultiFormatReader.decode return type: type 'com.google.zxing.Result' is not supported in v0.1; see docs/type-mapping.md for the supported set
doctor: 3 error(s), 5 warning(s)
```

- Java platform types (`String!`, `BarcodeFormat!`) produce one warning each; they are treated as
  nullable, which is the safe choice for a Java API.

## Patterns that block binding in v0.1 (and where they are tracked)

| Pattern | Seen in | What Henosis says | Roadmap |
| --- | --- | --- | --- |
| Methods returning the SDK's own classes (builders, chaining) | Okio, ZXing, osmdroid | unsupported type, with the type named | Object handles |
| Static entry points (`companion object`, Java `static`) | Timber, Glide, Gson | not listed as facade members | Static facades |
| Constructors with arguments | ZXing `BitMatrix`, osmdroid `GeoPoint` | "no public no-argument or Context constructor" | Constructor arguments |
| `@Deprecated(level = ERROR)` members | Okio | rejected by `doctor`, flagged by `inspect` | — |
| Kotlin Multiplatform umbrella artifacts | Okio | parses the `-jvm`/`-android` sibling, keeps the umbrella coordinates | Read Gradle module metadata for other suffixes |
