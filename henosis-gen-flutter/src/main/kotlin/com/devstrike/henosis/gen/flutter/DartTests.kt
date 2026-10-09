package com.devstrike.henosis.gen.flutter

import com.devstrike.henosis.core.codegen.CodeWriter
import com.devstrike.henosis.core.codegen.GeneratedHeader
import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrTypeKind

/**
 * Generates `test/<package>_test.dart`: unit tests that drive the real Dart bindings through a
 * mocked platform channel, proving the error mapping end to end on the Dart side: a native
 * `PlatformException` with code `ArithmeticException` surfaces as `HenosisException` with that
 * code, and a successful reply is decoded into the declared return type.
 */
internal class DartTests(
    private val ir: Ir,
    private val packageName: String,
) {
    private val types = DartTypes(ir)

    fun generate(): String {
        val w = CodeWriter()
        w.line(GeneratedHeader.slash(ir.henosisVersion))
        w.line("import 'package:flutter/services.dart';")
        w.line("import 'package:flutter_test/flutter_test.dart';")
        w.line("import 'package:$packageName/$packageName.dart';")
        w.line()
        w.block("void main() {") {
            line("TestWidgetsFlutterBinding.ensureInitialized();")
            line("final TestDefaultBinaryMessenger messenger =")
            indent { line("TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;") }
            ir.facades.forEach { facade ->
                val channel = "henosis/${ir.name}/${facade.name}"
                val method =
                    facade.methods.firstOrNull { it.returnType.kind != IrTypeKind.UNIT } ?: facade.methods.firstOrNull() ?: return@forEach
                val args = method.parameters.joinToString(", ") { types.sample(it.type) }
                line()
                block("group('${facade.name}', () {", footer = "});") {
                    line("const MethodChannel channel = MethodChannel('$channel');")
                    line()
                    block("tearDown(() {", footer = "});") { line("messenger.setMockMethodCallHandler(channel, null);") }
                    line()
                    block("test('native exceptions surface as HenosisException with the native class name', () async {", footer = "});") {
                        block("messenger.setMockMethodCallHandler(channel, (MethodCall call) async {", footer = "});") {
                            line("throw PlatformException(code: 'ArithmeticException', message: 'Division by zero');")
                        }
                        line("await expectLater(")
                        indent {
                            line("() => ${facade.name}.instance.${method.name}($args),")
                            line("throwsA(")
                            indent {
                                line("isA<HenosisException>()")
                                indent {
                                    line(".having((HenosisException e) => e.code, 'code', 'ArithmeticException')")
                                    line(".having((HenosisException e) => e.message, 'message', 'Division by zero'),")
                                }
                            }
                            line("),")
                        }
                        line(");")
                    }
                    line()
                    block("test('${method.name} sends its arguments and decodes the reply', () async {", footer = "});") {
                        line("MethodCall? received;")
                        block("messenger.setMockMethodCallHandler(channel, (MethodCall call) async {", footer = "});") {
                            line("received = call;")
                            line("return ${types.sampleWire(method.returnType)};")
                        }
                        if (method.returnType.kind == IrTypeKind.UNIT) {
                            line("await ${facade.name}.instance.${method.name}($args);")
                        } else {
                            line("final ${types.name(method.returnType)} result =")
                            indent { line("await ${facade.name}.instance.${method.name}($args);") }
                            line("expect(result, ${types.sample(method.returnType)});")
                        }
                        line("expect(received?.method, '${method.name}');")
                        if (method.parameters.isNotEmpty()) {
                            line("expect(received?.arguments, isA<Map<Object?, Object?>>());")
                            line(
                                "expect((received?.arguments as Map<Object?, Object?>).keys, ${method.parameters.joinToString(
                                    ", ",
                                    "<String>[",
                                    "]",
                                ) { "'${it.name}'" }});",
                            )
                        }
                    }
                }
            }
        }
        return w.toString()
    }
}
