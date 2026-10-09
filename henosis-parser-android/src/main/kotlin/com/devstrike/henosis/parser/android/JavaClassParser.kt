package com.devstrike.henosis.parser.android

import com.devstrike.henosis.core.api.ApiClass
import com.devstrike.henosis.core.api.ApiClassKind
import com.devstrike.henosis.core.api.ApiConstructor
import com.devstrike.henosis.core.api.ApiFunction
import com.devstrike.henosis.core.api.ApiOrigin
import com.devstrike.henosis.core.api.ApiParameter
import com.devstrike.henosis.core.api.ApiProperty
import com.devstrike.henosis.core.types.TypeRef
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

/**
 * Builds an [ApiClass] from a plain Java class (no Kotlin metadata).
 *
 * Java gives no nullability, so every reference type becomes a nullable platform type and the
 * type rules warn about it. Generic signatures are read when present; otherwise the erased
 * descriptor is used.
 */
internal object JavaClassParser {
    private const val ASM_API = Opcodes.ASM9

    fun parse(node: ClassNode): ApiClass? {
        if (!node.isPublic() || node.isSynthetic() || node.name.endsWith("package-info") || node.name == "module-info") return null
        if (node.outerClass != null && node.innerClasses.any { it.name == node.name && it.innerName == null }) return null // anonymous
        val kind =
            when {
                node.access has Opcodes.ACC_ANNOTATION -> ApiClassKind.ANNOTATION_CLASS
                node.access has Opcodes.ACC_ENUM -> ApiClassKind.ENUM_CLASS
                node.access has Opcodes.ACC_INTERFACE -> ApiClassKind.INTERFACE
                else -> ApiClassKind.CLASS
            }
        val classSignature = node.signature?.let { ClassSignature.parse(it) }
        val deprecations = Deprecations(node)
        val constructors =
            node.methods
                .filter { it.name == "<init>" && it.isPublic() && !it.isSynthetic() }
                .map { m ->
                    val sig = MethodSignature.of(m)
                    ApiConstructor(parameters = parameters(m, sig), jvmDescriptor = m.desc)
                }.sortedBy { it.jvmDescriptor }
        val functions =
            node.methods
                .filter { it.name != "<init>" && it.name != "<clinit>" && it.isPublic() && !it.isSynthetic() && !it.isStatic() }
                .filter { !(it.access has Opcodes.ACC_BRIDGE) }
                .filter { it.name !in setOf("equals", "hashCode", "toString") }
                .map { m ->
                    val sig = MethodSignature.of(m)
                    ApiFunction(
                        name = m.name,
                        parameters = parameters(m, sig),
                        returnType = sig.returnType,
                        typeParameters = sig.typeParameters,
                        jvmName = m.name,
                        jvmDescriptor = m.desc,
                        deprecation = deprecations.of(m.name, m.desc),
                    )
                }.sortedWith(compareBy({ it.name }, { it.jvmDescriptor }))
        val properties =
            node.fields
                .filter { it.isPublic() && !it.isStatic() && !it.isSynthetic() }
                .map { f ->
                    ApiProperty(
                        name = f.name,
                        type = f.signature?.let { TypeSignature.parse(it) } ?: JvmTypeNames.fromAsmType(Type.getType(f.desc)),
                        isMutable = !(f.access has Opcodes.ACC_FINAL),
                    )
                }.sortedBy { it.name }
        val enumEntries =
            if (kind == ApiClassKind.ENUM_CLASS) node.fields.filter { it.access has Opcodes.ACC_ENUM }.map { it.name } else emptyList()
        return ApiClass(
            name = JvmTypeNames.fromInternalName(node.name),
            kind = kind,
            origin = ApiOrigin.JAVA,
            typeParameters = classSignature?.typeParameters ?: emptyList(),
            constructors = constructors,
            functions = functions,
            properties = properties,
            enumEntries = enumEntries,
        )
    }

    private fun parameters(
        m: MethodNode,
        sig: MethodSignature,
    ): List<ApiParameter> {
        val names = parameterNames(m, sig.parameterTypes.size)
        return sig.parameterTypes.mapIndexed { i, type -> ApiParameter(name = names[i], type = type) }
    }

    /** Parameter names from `-parameters` metadata, else the local variable table, else `p0, p1, ...`. */
    private fun parameterNames(
        m: MethodNode,
        count: Int,
    ): List<String> {
        m.parameters?.takeIf { it.size == count }?.let { return it.map { p -> p.name } }
        val locals = m.localVariables ?: emptyList()
        val argTypes = Type.getArgumentTypes(m.desc)
        var slot = if (m.isStatic()) 0 else 1
        return (0 until count).map { i ->
            val name = locals.firstOrNull { it.index == slot }?.name ?: "p$i"
            slot += argTypes.getOrNull(i)?.size ?: 1
            name
        }
    }

    private fun ClassNode.isPublic() = access has Opcodes.ACC_PUBLIC

    private fun ClassNode.isSynthetic() = access has Opcodes.ACC_SYNTHETIC

    private fun MethodNode.isPublic() = access has Opcodes.ACC_PUBLIC

    private fun MethodNode.isStatic() = access has Opcodes.ACC_STATIC

    private fun MethodNode.isSynthetic() = access has Opcodes.ACC_SYNTHETIC

    private fun FieldNode.isPublic() = access has Opcodes.ACC_PUBLIC

    private fun FieldNode.isStatic() = access has Opcodes.ACC_STATIC

    private fun FieldNode.isSynthetic() = access has Opcodes.ACC_SYNTHETIC

    private infix fun Int.has(flag: Int) = this and flag != 0

    /** Parameter and return types of a method, from its generic signature when present. */
    private class MethodSignature(
        val typeParameters: List<String>,
        val parameterTypes: List<TypeRef>,
        val returnType: TypeRef,
    ) {
        companion object {
            fun of(m: MethodNode): MethodSignature {
                val generic = m.signature
                if (generic != null) {
                    val visitor = MethodSignatureVisitor()
                    SignatureReader(generic).accept(visitor)
                    val parsed = visitor.result()
                    // Inner-class constructors and enum constructors carry synthetic leading parameters that
                    // the generic signature omits; fall back to the descriptor if the counts disagree.
                    if (parsed.parameterTypes.size == Type.getArgumentTypes(m.desc).size) return parsed
                }
                val type = Type.getMethodType(m.desc)
                return MethodSignature(
                    typeParameters = emptyList(),
                    parameterTypes = type.argumentTypes.map { JvmTypeNames.fromAsmType(it) },
                    returnType = JvmTypeNames.fromAsmType(type.returnType),
                )
            }
        }
    }

    private class MethodSignatureVisitor : SignatureVisitor(ASM_API) {
        private val typeParameters = mutableListOf<String>()
        private val parameterTypes = mutableListOf<TypeRef>()
        private var returnType: TypeRef? = null

        override fun visitFormalTypeParameter(name: String) {
            typeParameters += name
        }

        override fun visitClassBound(): SignatureVisitor = TypeRefBuilder {}

        override fun visitInterfaceBound(): SignatureVisitor = TypeRefBuilder {}

        override fun visitParameterType(): SignatureVisitor = TypeRefBuilder { parameterTypes += it }

        override fun visitReturnType(): SignatureVisitor = TypeRefBuilder { returnType = it }

        override fun visitExceptionType(): SignatureVisitor = TypeRefBuilder {}

        fun result() = MethodSignature(typeParameters.toList(), parameterTypes.toList(), returnType ?: TypeRef("kotlin.Unit"))
    }

    /** The type parameters declared by a generic class signature. */
    private class ClassSignature(
        val typeParameters: List<String>,
    ) {
        companion object {
            fun parse(signature: String): ClassSignature {
                val names = mutableListOf<String>()
                SignatureReader(signature).accept(
                    object : SignatureVisitor(ASM_API) {
                        override fun visitFormalTypeParameter(name: String) {
                            names += name
                        }

                        override fun visitClassBound(): SignatureVisitor = TypeRefBuilder {}

                        override fun visitInterfaceBound(): SignatureVisitor = TypeRefBuilder {}

                        override fun visitSuperclass(): SignatureVisitor = TypeRefBuilder {}

                        override fun visitInterface(): SignatureVisitor = TypeRefBuilder {}
                    },
                )
                return ClassSignature(names)
            }
        }
    }

    private object TypeSignature {
        fun parse(signature: String): TypeRef {
            var result: TypeRef? = null
            SignatureReader(signature).acceptType(TypeRefBuilder { result = it })
            return result ?: error("Could not parse type signature '$signature'")
        }
    }

    /** Builds one [TypeRef] from a type signature and hands it to [done] when complete. */
    private class TypeRefBuilder(
        private val done: (TypeRef) -> Unit,
    ) : SignatureVisitor(ASM_API) {
        private var internalName: String? = null
        private val arguments = mutableListOf<TypeRef>()

        override fun visitBaseType(descriptor: Char) = done(JvmTypeNames.primitive(descriptor))

        override fun visitTypeVariable(name: String) = done(TypeRef.typeParameter(name, nullable = true))

        override fun visitArrayType(): SignatureVisitor = TypeRefBuilder { element -> done(JvmTypeNames.array(element)) }

        override fun visitClassType(name: String) {
            internalName = name
        }

        override fun visitInnerClassType(name: String) {
            internalName = "$internalName\$$name"
            arguments.clear()
        }

        override fun visitTypeArgument() {
            arguments += TypeRef.STAR
        }

        override fun visitTypeArgument(wildcard: Char): SignatureVisitor = TypeRefBuilder { arguments += it }

        override fun visitEnd() = done(JvmTypeNames.platformClass(internalName ?: error("class type without a name"), arguments.toList()))
    }
}
