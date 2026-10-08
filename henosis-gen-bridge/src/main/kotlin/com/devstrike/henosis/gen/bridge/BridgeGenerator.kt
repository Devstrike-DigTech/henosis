package com.devstrike.henosis.gen.bridge

import com.devstrike.henosis.core.codegen.GeneratedFile
import com.devstrike.henosis.core.codegen.GeneratedHeader
import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrConstructor
import com.devstrike.henosis.core.ir.IrFacade
import com.devstrike.henosis.core.ir.IrType
import com.devstrike.henosis.core.ir.IrTypeKind
import com.devstrike.henosis.core.ir.IrTypeRef
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec

/**
 * Generates the framework-neutral Kotlin bridge that every Android target shares:
 *
 * - `HenosisCodecs`: `encode<Type>` / `decode<Type>` for each data class (maps with string keys).
 * - `<Facade>Bridge`: owns the SDK singleton and exposes `suspend fun invoke(method, args)` and
 *   `fun event(name): Flow<Any?>`, with all argument decoding and result encoding done inside.
 *
 * The framework glue (Flutter plugin, React Native module) only moves `Any?` values across its
 * channel and maps exceptions; it never touches SDK types.
 */
object BridgeGenerator {
    const val CODECS_CLASS = "HenosisCodecs"

    private val FLOW = ClassName("kotlinx.coroutines.flow", "Flow")
    private val FLOW_MAP = ClassName("kotlinx.coroutines.flow", "map")
    private val CONTEXT = ClassName("android.content", "Context")
    private val ARGS = MAP.parameterizedBy(STRING, ANY.copy(nullable = true))

    /** Generates the bridge sources for [packageName]; file paths are relative to that package's source directory. */
    fun generate(
        ir: Ir,
        packageName: String,
    ): List<GeneratedFile> {
        val codecs = ClassName(packageName, CODECS_CLASS)
        val files = mutableListOf<GeneratedFile>()
        files += codecsFile(ir, packageName, codecs)
        ir.facades.forEach { facade -> files += facadeFile(ir, packageName, codecs, facade) }
        return files
    }

    /** `<Facade>Bridge`, the class name used by framework glue. */
    fun bridgeClassName(facade: IrFacade): String = "${facade.name}Bridge"

    private fun codecsFile(
        ir: Ir,
        packageName: String,
        codecs: ClassName,
    ): GeneratedFile {
        val type = TypeSpec.objectBuilder(codecs).addKdoc("Converts the SDK's values to and from their channel representation.\n")
        type.addFunction(decodeBytesFunction())
        ir.types.filter { it.kind == IrTypeKind.DATA_CLASS }.forEach { dataClass ->
            type.addFunction(encodeFunction(ir, dataClass, codecs))
            type.addFunction(decodeFunction(ir, dataClass, codecs))
        }
        return file(packageName, CODECS_CLASS, type.build(), ir)
    }

    /** `decodeBytes`: accepts a `ByteArray` (Flutter) or a base64 `String` (React Native). */
    private fun decodeBytesFunction(): FunSpec {
        val base64 = ClassName("android.util", "Base64")
        return FunSpec
            .builder("decodeBytes")
            .addKdoc("Accepts a raw `ByteArray` or a base64 `String`, depending on what the framework channel can carry.\n")
            .addParameter("value", ANY.copy(nullable = true))
            .returns(com.squareup.kotlinpoet.BYTE_ARRAY)
            .beginControlFlow("return when (value)")
            .addStatement("is ByteArray -> value")
            .addStatement("is String -> %T.decode(value, %T.DEFAULT)", base64, base64)
            .addStatement("else -> throw %T(%S + value?.javaClass?.name)", IllegalArgumentException::class, "expected bytes, got ")
            .endControlFlow()
            .build()
    }

    private fun encodeFunction(
        ir: Ir,
        dataClass: IrType,
        codecs: ClassName,
    ): FunSpec {
        val codec = KotlinCodec(ir, codecs)
        val native = ClassName.bestGuess(dataClass.nativeClass)
        val body = CodeBlock.builder().add("return mapOf(\n").indent()
        dataClass.fields.forEach { field ->
            body.add("%S to %L,\n", field.name, codec.encode(field.type, CodeBlock.of("value.%N", field.name)))
        }
        body.unindent().add(")\n")
        return FunSpec
            .builder("encode${dataClass.name}")
            .addParameter("value", native)
            .returns(ARGS)
            .addCode(body.build())
            .build()
    }

    private fun decodeFunction(
        ir: Ir,
        dataClass: IrType,
        codecs: ClassName,
    ): FunSpec {
        val codec = KotlinCodec(ir, codecs)
        val native = ClassName.bestGuess(dataClass.nativeClass)
        val body = CodeBlock.builder().add("return %T(\n", native).indent()
        dataClass.fields.forEach { field ->
            body.add("%L,\n", codec.decode(field.type, CodeBlock.of("value[%S]", field.name)))
        }
        body.unindent().add(")\n")
        return FunSpec
            .builder("decode${dataClass.name}")
            .addParameter("value", MAP.parameterizedBy(STAR, STAR))
            .returns(native)
            .addCode(body.build())
            .build()
    }

    private fun facadeFile(
        ir: Ir,
        packageName: String,
        codecs: ClassName,
        facade: IrFacade,
    ): GeneratedFile {
        val native = ClassName.bestGuess(facade.nativeClass)
        val className = bridgeClassName(facade)
        val type =
            TypeSpec
                .classBuilder(className)
                .addKdoc(
                    "Bridges `%L` for the generated plugin. One instance per engine; the SDK object is a singleton.\n",
                    facade.nativeClass,
                )
        val targetInit =
            when (facade.constructor) {
                IrConstructor.NONE -> {
                    CodeBlock.of("%T()", native)
                }

                IrConstructor.CONTEXT -> {
                    type.primaryConstructor(FunSpec.constructorBuilder().addParameter("context", CONTEXT).build())
                    CodeBlock.of("%T(context)", native)
                }
            }
        type.addProperty(PropertySpec.builder("target", native, KModifier.PRIVATE).initializer(targetInit).build())
        type.addFunction(invokeFunction(ir, codecs, facade))
        type.addFunction(eventFunction(ir, codecs, facade))
        return file(packageName, className, type.build(), ir)
    }

    private fun invokeFunction(
        ir: Ir,
        codecs: ClassName,
        facade: IrFacade,
    ): FunSpec {
        val body = CodeBlock.builder().beginControlFlow("return when (method)")
        facade.methods.forEach { method ->
            val codec = KotlinCodec(ir, codecs)
            val args = method.parameters.map { p -> codec.decode(p.type, CodeBlock.of("args[%S]", p.name)) }
            val call =
                CodeBlock.of(
                    "target.%N(%L)",
                    method.jvmName.takeIf { it == method.nativeName } ?: method.nativeName,
                    args.joinToCode(", "),
                )
            if (method.returnType.kind == IrTypeKind.UNIT) {
                body
                    .add("%S -> {\n", method.name)
                    .indent()
                    .add("%L\n", call)
                    .add("null\n")
                    .unindent()
                    .add("}\n")
            } else {
                body.add("%S -> %L\n", method.name, codec.encode(method.returnType, call))
            }
        }
        body.add("else -> throw %T(method)\n", NoSuchMethodException::class)
        body.endControlFlow()
        return FunSpec
            .builder("invoke")
            .addKdoc("Calls [method] with channel-encoded [args]; the result is channel-encoded too.\n")
            .addModifiers(KModifier.SUSPEND)
            .addParameter("method", STRING)
            .addParameter("args", ARGS)
            .returns(ANY.copy(nullable = true))
            .addCode(body.build())
            .build()
    }

    private fun eventFunction(
        ir: Ir,
        codecs: ClassName,
        facade: IrFacade,
    ): FunSpec {
        val body = CodeBlock.builder().beginControlFlow("return when (name)")
        facade.events.forEach { event ->
            val codec = KotlinCodec(ir, codecs)
            val source =
                when (event.source) {
                    com.devstrike.henosis.core.ir.IrEventSource.PROPERTY -> CodeBlock.of("target.%N", event.nativeName)
                    com.devstrike.henosis.core.ir.IrEventSource.FUNCTION -> CodeBlock.of("target.%N()", event.nativeName)
                }
            val element: IrTypeRef = event.elementType
            if (codec.isIdentity(element)) {
                body.add("%S -> %L\n", event.name, source)
            } else {
                body.add("%S -> %L.%M { value -> %L }\n", event.name, source, FLOW_MAP, codec.encode(element, CodeBlock.of("value")))
            }
        }
        body.add("else -> throw %T(name)\n", NoSuchMethodException::class)
        body.endControlFlow()
        return FunSpec
            .builder("event")
            .addKdoc("The channel-encoded event stream named [name].\n")
            .addParameter("name", STRING)
            .returns(FLOW.parameterizedBy(ANY.copy(nullable = true)))
            .addCode(body.build())
            .build()
    }

    private fun file(
        packageName: String,
        name: String,
        type: TypeSpec,
        ir: Ir,
    ): GeneratedFile {
        val spec =
            FileSpec
                .builder(packageName, name)
                .addFileComment("%L", GeneratedHeader.text(ir.henosisVersion))
                .indent("    ")
                .addType(type)
                .build()
        return GeneratedFile("$name.kt", spec.toString())
    }

    private fun List<CodeBlock>.joinToCode(separator: String): CodeBlock {
        val builder = CodeBlock.builder()
        forEachIndexed { i, block ->
            if (i > 0) builder.add(separator)
            builder.add(block)
        }
        return builder.build()
    }
}
