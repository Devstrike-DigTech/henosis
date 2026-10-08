package com.devstrike.henosis.gen.reactnative

import com.devstrike.henosis.core.codegen.CodeWriter
import com.devstrike.henosis.core.codegen.GeneratedFile
import com.devstrike.henosis.core.codegen.GeneratedHeader
import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrFacade
import com.devstrike.henosis.core.ir.IrType
import com.devstrike.henosis.core.ir.IrTypeKind

/** Generates `src/`: the Codegen specs, the public TypeScript API, types, codecs and runtime helpers. */
internal class TsLibrary(
    private val ir: Ir,
    private val moduleName: String,
) {
    private val types = TsTypes(ir)
    private val header = GeneratedHeader.slash(ir.henosisVersion)

    fun generate(): List<GeneratedFile> {
        val files = mutableListOf<GeneratedFile>()
        files += GeneratedFile("src/index.ts", index())
        files += GeneratedFile("src/henosisError.ts", errorFile())
        files += GeneratedFile("src/henosisRuntime.ts", runtimeFile())
        files += GeneratedFile("src/henosisBase64.ts", base64File())
        if (ir.types.isNotEmpty()) {
            files += GeneratedFile("src/types.ts", typesFile())
            files += GeneratedFile("src/henosisCodecs.ts", codecsFile())
        }
        ir.facades.forEach { facade ->
            files += GeneratedFile("src/${specName(facade)}.ts", specFile(facade))
            files += GeneratedFile("src/${TsNames.fileName(facade.name)}.ts", facadeFile(facade))
        }
        return files
    }

    private fun isDataClass(name: String) = ir.types.any { it.name == name && it.kind == IrTypeKind.DATA_CLASS }

    /** `NativeSampleSdkGreeter`: the spec file and TurboModule name share it. */
    fun specName(facade: IrFacade) = "Native${turboModuleName(facade)}"

    fun turboModuleName(facade: IrFacade) = "$moduleName${facade.name}"

    /** The device-event name an event is emitted under; prefixed so packages never collide. */
    fun eventName(
        facade: IrFacade,
        event: String,
    ) = "henosis.${ir.name}.${facade.name}.$event"

    private fun index(): String {
        val w = CodeWriter()
        w.line(header)
        w.line("export { HenosisError } from './henosisError';")
        w.line("export { HenosisEvent, type Unsubscribe } from './henosisRuntime';")
        if (ir.types.isNotEmpty()) w.line("export type { ${ir.types.joinToString(", ") { it.name }} } from './types';")
        ir.facades.forEach { w.line("export { ${it.name} } from './${TsNames.fileName(it.name)}';") }
        return w.toString()
    }

    private fun errorFile(): String =
        """
        |$header
        |/**
        | * An error raised by the native SDK while handling a call or an event.
        | *
        | * `code` is the simple class name of the native exception, for example
        | * `ArithmeticException` or `IllegalStateException`.
        | */
        |export class HenosisError extends Error {
        |  /** The simple class name of the native exception. */
        |  readonly code: string;
        |
        |  constructor(code: string, message?: string) {
        |    super(message ?? code);
        |    this.name = 'HenosisError';
        |    this.code = code;
        |  }
        |
        |  /** Wraps a rejection coming from the native module (which carries a `code`). */
        |  static from(error: unknown): HenosisError {
        |    if (error instanceof HenosisError) {
        |      return error;
        |    }
        |    if (typeof error === 'object' && error !== null) {
        |      const candidate = error as { code?: unknown; message?: unknown };
        |      const code = typeof candidate.code === 'string' ? candidate.code : 'UnknownError';
        |      const message = typeof candidate.message === 'string' ? candidate.message : undefined;
        |      return new HenosisError(code, message);
        |    }
        |    return new HenosisError('UnknownError', String(error));
        |  }
        |}
        |
        """.trimMargin()

    private fun runtimeFile(): String =
        """
        |$header
        |import { NativeEventEmitter } from 'react-native';
        |
        |import { HenosisError } from './henosisError';
        |
        |/** Returned by `addListener`; call it to stop listening. */
        |export type Unsubscribe = () => void;
        |
        |/** What `NativeEventEmitter` needs from a module that emits events (React Native does not export this type). */
        |export interface EventModule {
        |  addListener(eventType: string): void;
        |  removeListeners(count: number): void;
        |}
        |
        |/** Awaits a native call, turning rejections into [HenosisError]. */
        |export async function henosisInvoke<T>(call: () => Promise<T>): Promise<T> {
        |  try {
        |    return await call();
        |  } catch (error) {
        |    throw HenosisError.from(error);
        |  }
        |}
        |
        |/** A stream of native events. The native side only collects while at least one listener exists. */
        |export class HenosisEvent<T> {
        |  private readonly emitter: NativeEventEmitter;
        |
        |  constructor(
        |    module: EventModule,
        |    private readonly eventName: string,
        |    private readonly decode: (event: unknown) => T
        |  ) {
        |    this.emitter = new NativeEventEmitter(module);
        |  }
        |
        |  /** Starts listening; the returned function unsubscribes. */
        |  addListener(
        |    listener: (event: T) => void,
        |    onError?: (error: HenosisError) => void
        |  ): Unsubscribe {
        |    const subscription = this.emitter.addListener(this.eventName, (event: unknown) =>
        |      listener(this.decode(event))
        |    );
        |    const errorSubscription =
        |      onError === undefined
        |        ? undefined
        |        : this.emitter.addListener(`${'$'}{this.eventName}.error`, (error: unknown) =>
        |            onError(HenosisError.from(error))
        |          );
        |    return () => {
        |      subscription.remove();
        |      errorSubscription?.remove();
        |    };
        |  }
        |}
        |
        """.trimMargin()

    private fun base64File(): String =
        """
        |$header
        |// Byte arrays cross the React Native bridge as base64 strings (Codegen cannot type binary data).
        |
        |const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
        |const LOOKUP: Record<string, number> = {};
        |for (let i = 0; i < ALPHABET.length; i++) {
        |  LOOKUP[ALPHABET.charAt(i)] = i;
        |}
        |
        |/** Encodes [buffer] as standard base64 with padding. */
        |export function arrayBufferToBase64(buffer: ArrayBuffer): string {
        |  const bytes = new Uint8Array(buffer);
        |  let out = '';
        |  for (let i = 0; i < bytes.length; i += 3) {
        |    const b0 = bytes[i] ?? 0;
        |    const b1 = bytes[i + 1];
        |    const b2 = bytes[i + 2];
        |    const triple = (b0 << 16) | ((b1 ?? 0) << 8) | (b2 ?? 0);
        |    out += ALPHABET.charAt((triple >> 18) & 63);
        |    out += ALPHABET.charAt((triple >> 12) & 63);
        |    out += b1 === undefined ? '=' : ALPHABET.charAt((triple >> 6) & 63);
        |    out += b2 === undefined ? '=' : ALPHABET.charAt(triple & 63);
        |  }
        |  return out;
        |}
        |
        |/** Decodes standard base64 (padding optional, whitespace ignored). */
        |export function base64ToArrayBuffer(text: string): ArrayBuffer {
        |  const clean = text.replace(/[^A-Za-z0-9+/]/g, '');
        |  const length = Math.floor((clean.length * 3) / 4);
        |  const bytes = new Uint8Array(length);
        |  let value = 0;
        |  let bits = 0;
        |  let index = 0;
        |  for (let i = 0; i < clean.length; i++) {
        |    value = (value << 6) | (LOOKUP[clean.charAt(i)] ?? 0);
        |    bits += 6;
        |    if (bits >= 8) {
        |      bits -= 8;
        |      bytes[index++] = (value >> bits) & 255;
        |    }
        |  }
        |  return bytes.buffer;
        |}
        |
        """.trimMargin()

    private fun typesFile(): String {
        val w = CodeWriter()
        w.line(header)
        ir.types.forEach { type ->
            w.line()
            w.line("/** Mirrors `${type.nativeClass}`. */")
            when (type.kind) {
                IrTypeKind.ENUM -> {
                    w.line("export type ${type.name} = ${type.entries.joinToString(" | ") { "'$it'" }};")
                }

                else -> {
                    w.block("export interface ${type.name} {") {
                        type.fields.forEach { f -> line("readonly ${TsNames.identifier(f.name)}: ${types.name(f.type, readonly = true)};") }
                    }
                }
            }
        }
        return w.toString()
    }

    private fun codecsFile(): String {
        val w = CodeWriter()
        w.line(header)
        val dataClasses = ir.types.filter { it.kind == IrTypeKind.DATA_CLASS }
        val usesBytes = dataClasses.any { t -> t.fields.any { types.usesBytes(it.type) } }
        if (usesBytes) w.line("import { arrayBufferToBase64, base64ToArrayBuffer } from './henosisBase64';")
        w.line("import type { ${ir.types.joinToString(", ") { it.name }} } from './types';")
        dataClasses.forEach { type -> dataClassCodecs(w, type) }
        if (dataClasses.isEmpty()) {
            w.line()
            w.line("export {};")
        }
        return w.toString()
    }

    private fun dataClassCodecs(
        w: CodeWriter,
        type: IrType,
    ) {
        val fields = type.fields.map { it.copy(name = TsNames.identifier(it.name)) to it.name }
        w.line()
        w.line("/** Rebuilds a ${type.name} from the map the native side sends. */")
        w.block("export function decode${type.name}(value: unknown): ${type.name} {") {
            line("const map = value as Record<string, unknown>;")
            fields.forEach { (f, wire) -> line("const ${f.name} = map['$wire'];") }
            line("return {")
            indent { fields.forEach { (f, _) -> line("${f.name}: ${types.decode(f.type, f.name)},") } }
            line("};")
        }
        w.line()
        w.line("/** Encodes a ${type.name} for the native side. */")
        w.block("export function encode${type.name}(value: ${type.name}): Record<string, unknown> {") {
            line("return {")
            indent { fields.forEach { (f, wire) -> line("$wire: ${types.encode(f.type, "value.${f.name}")},") } }
            line("};")
        }
    }

    private fun specFile(facade: IrFacade): String {
        val w = CodeWriter()
        w.line(header)
        w.line("import { TurboModuleRegistry, type TurboModule } from 'react-native';")
        w.line()
        w.line("/** Codegen spec for `${facade.nativeClass}`; see the `${facade.name}` class for the typed API. */")
        w.block("export interface Spec extends TurboModule {") {
            facade.methods.forEach { method ->
                val params = method.parameters.joinToString(", ") { "${TsNames.identifier(it.name)}: ${types.specName(it.type)}" }
                line("${method.name}($params): Promise<${types.specName(method.returnType)}>;")
            }
            if (facade.events.isNotEmpty()) {
                line("addListener(eventName: string): void;")
                line("removeListeners(count: number): void;")
            }
        }
        w.line()
        w.line("export default TurboModuleRegistry.getEnforcing<Spec>('${turboModuleName(facade)}');")
        return w.toString()
    }

    private fun facadeFile(facade: IrFacade): String {
        val w = CodeWriter()
        w.line(header)
        val allTypes = facade.methods.flatMap { m -> m.parameters.map { it.type } + m.returnType } + facade.events.map { it.elementType }
        val spec = specName(facade)
        w.line("import $spec from './$spec';")
        val incoming = facade.methods.map { it.returnType } + facade.events.map { it.elementType }
        val outgoing = facade.methods.flatMap { m -> m.parameters.map { it.type } }
        val base64Imports =
            listOfNotNull(
                "arrayBufferToBase64".takeIf { outgoing.any { types.usesBytes(it) } },
                "base64ToArrayBuffer".takeIf { incoming.any { types.usesBytes(it) } },
            )
        if (base64Imports.isNotEmpty()) w.line("import { ${base64Imports.joinToString(", ")} } from './henosisBase64';")
        val decoders = incoming.flatMap { types.referencedTypes(it) }.filter { isDataClass(it) }.map { "decode$it" }
        val encoders = outgoing.flatMap { types.referencedTypes(it) }.filter { isDataClass(it) }.map { "encode$it" }
        val codecImports = (decoders + encoders).toSortedSet()
        if (codecImports.isNotEmpty()) w.line("import { ${codecImports.joinToString(", ")} } from './henosisCodecs';")
        val runtime = mutableListOf<String>()
        if (facade.events.isNotEmpty()) runtime += "HenosisEvent"
        if (facade.methods.isNotEmpty()) runtime += "henosisInvoke"
        if (runtime.isNotEmpty()) w.line("import { ${runtime.joinToString(", ")} } from './henosisRuntime';")
        val referenced = allTypes.flatMap { types.referencedTypes(it) }.toSortedSet()
        if (referenced.isNotEmpty()) w.line("import type { ${referenced.joinToString(", ")} } from './types';")
        w.line()
        w.line("/**")
        w.line(" * Bindings for `${facade.nativeClass}`.")
        w.line(" *")
        w.line(" * The native object is a singleton owned by the module; use `${facade.name}.instance`.")
        w.line(" */")
        w.block("export class ${facade.name} {") {
            line("private constructor() {}")
            line()
            line("/** The shared instance. */")
            line("static readonly instance: ${facade.name} = new ${facade.name}();")
            facade.events.forEach { event ->
                line()
                line("/** Events from `${facade.nativeClass}.${event.nativeName}`. */")
                line("readonly ${event.name}: HenosisEvent<${types.name(event.elementType)}> = new HenosisEvent(")
                indent {
                    line("$spec,")
                    line("'${eventName(facade, event.name)}',")
                    line("(event: unknown) => ${types.decode(event.elementType, "event")}")
                }
                line(");")
            }
            facade.methods.forEach { method ->
                line()
                line("/** Calls `${facade.nativeClass}.${method.nativeName}`. */")
                val params = method.parameters.joinToString(", ") { "${TsNames.identifier(it.name)}: ${types.name(it.type)}" }
                val returns =
                    if (method.returnType.kind ==
                        IrTypeKind.UNIT
                    ) {
                        "Promise<void>"
                    } else {
                        "Promise<${types.name(method.returnType)}>"
                    }
                block("async ${method.name}($params): $returns {") {
                    val args = method.parameters.joinToString(", ") { types.encode(it.type, TsNames.identifier(it.name)) }
                    if (method.returnType.kind == IrTypeKind.UNIT) {
                        line("await henosisInvoke(() => $spec.${method.name}($args));")
                    } else {
                        line("const result: unknown = await henosisInvoke(() => $spec.${method.name}($args));")
                        line("return ${types.decode(method.returnType, "result")};")
                    }
                }
            }
        }
        return w.toString()
    }
}
