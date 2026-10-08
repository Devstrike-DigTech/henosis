package com.devstrike.henosis.core

import com.devstrike.henosis.core.api.ApiClass
import com.devstrike.henosis.core.api.ApiClassKind
import com.devstrike.henosis.core.api.ApiConstructor
import com.devstrike.henosis.core.api.ApiFunction
import com.devstrike.henosis.core.api.ApiModel
import com.devstrike.henosis.core.api.ApiOrigin
import com.devstrike.henosis.core.api.ApiParameter
import com.devstrike.henosis.core.api.ApiProperty
import com.devstrike.henosis.core.types.KotlinTypes
import com.devstrike.henosis.core.types.TypeRef

/** A hand-built [ApiModel] with one example of everything the doctor rules look at. */
object TestSdk {
    const val PKG = "com.x"
    const val GREETER = "$PKG.Greeter"
    const val COUNTER = "$PKG.Counter"
    const val PROFILE = "$PKG.Profile"
    const val LEVEL = "$PKG.Level"
    const val BROKEN = "$PKG.Broken"
    const val BOX = "$PKG.Box"
    const val UTIL = "$PKG.Util"
    const val LEGACY = "$PKG.LegacyApi"

    val int = TypeRef(KotlinTypes.INT)
    val long = TypeRef(KotlinTypes.LONG)
    val double = TypeRef(KotlinTypes.DOUBLE)
    val string = TypeRef(KotlinTypes.STRING)
    val unit = TypeRef(KotlinTypes.UNIT)
    val context = TypeRef(KotlinTypes.ANDROID_CONTEXT)
    val profile = TypeRef(PROFILE)
    val level = TypeRef(LEVEL)
    val file = TypeRef("java.io.File")

    fun list(t: TypeRef) = TypeRef(KotlinTypes.LIST, listOf(t))

    fun map(
        k: TypeRef,
        v: TypeRef,
    ) = TypeRef(KotlinTypes.MAP, listOf(k, v))

    fun flow(t: TypeRef) = TypeRef(KotlinTypes.FLOW, listOf(t))

    fun fn(
        name: String,
        returnType: TypeRef = unit,
        vararg params: Pair<String, TypeRef>,
        suspend: Boolean = false,
        typeParameters: List<String> = emptyList(),
    ) = ApiFunction(
        name = name,
        parameters = params.map { (n, t) -> ApiParameter(n, t) },
        returnType = returnType,
        isSuspend = suspend,
        typeParameters = typeParameters,
        jvmName = name,
        jvmDescriptor = "(${params.joinToString("") { "X" }})X",
    )

    fun prop(
        name: String,
        type: TypeRef,
        mutable: Boolean = false,
    ) = ApiProperty(name, type, mutable, "get${name.replaceFirstChar { it.uppercase() }}")

    fun ctor(vararg params: Pair<String, TypeRef>) = ApiConstructor(params.map { (n, t) -> ApiParameter(n, t) }, "(${params.size})V")

    val model =
        ApiModel(
            listOf(
                ApiClass(
                    name = BOX,
                    kind = ApiClassKind.CLASS,
                    origin = ApiOrigin.KOTLIN,
                    typeParameters = listOf("T"),
                    constructors = listOf(ctor()),
                ),
                ApiClass(
                    name = BROKEN,
                    kind = ApiClassKind.DATA_CLASS,
                    origin = ApiOrigin.KOTLIN,
                    constructors = listOf(ctor("path" to file)),
                    properties = listOf(prop("path", file)),
                ),
                ApiClass(
                    name = COUNTER,
                    kind = ApiClassKind.CLASS,
                    origin = ApiOrigin.KOTLIN,
                    constructors = listOf(ctor()),
                    functions = listOf(fn("increment", int, "by" to int), fn("reset")),
                    properties = listOf(prop("count", int)),
                ),
                ApiClass(
                    name = GREETER,
                    kind = ApiClassKind.CLASS,
                    origin = ApiOrigin.KOTLIN,
                    constructors = listOf(ctor("context" to context)),
                    functions =
                        listOf(
                            fn("badFlowParam", unit, "events" to flow(int)),
                            fn("badMap", map(int, string)),
                            fn("broken", TypeRef(BROKEN)),
                            fn("divide", double, "a" to double, "b" to double),
                            fn("divide", double, "a" to int, "b" to int),
                            fn("fetchProfile", profile.copy(nullable = true), "id" to long, suspend = true),
                            fn("generic", TypeRef.typeParameter("T"), "x" to TypeRef.typeParameter("T"), typeParameters = listOf("T")),
                            fn("greet", string, "name" to string),
                            fn("greetMany", map(string, string), "names" to list(string)),
                            fn("ticksFn", flow(int)),
                            fn("useFile", unit, "f" to file),
                        ),
                    properties = listOf(prop("count", int), prop("ticks", flow(int))),
                ),
                ApiClass(
                    name = LEGACY,
                    kind = ApiClassKind.CLASS,
                    origin = ApiOrigin.JAVA,
                    constructors = listOf(ctor()),
                    functions =
                        listOf(
                            fn(
                                "ping",
                                string.copy(nullable = true, platform = true),
                                "m" to string.copy(nullable = true, platform = true),
                            ),
                        ),
                ),
                ApiClass(
                    name = LEVEL,
                    kind = ApiClassKind.ENUM_CLASS,
                    origin = ApiOrigin.KOTLIN,
                    enumEntries = listOf("BRONZE", "SILVER", "GOLD"),
                ),
                ApiClass(
                    name = PROFILE,
                    kind = ApiClassKind.DATA_CLASS,
                    origin = ApiOrigin.KOTLIN,
                    constructors = listOf(ctor("id" to long, "name" to string, "tags" to list(string), "level" to level)),
                    properties = listOf(prop("id", long), prop("level", level), prop("name", string), prop("tags", list(string))),
                ),
                ApiClass(
                    name = UTIL,
                    kind = ApiClassKind.OBJECT,
                    origin = ApiOrigin.KOTLIN,
                    functions = listOf(fn("now", long)),
                ),
            ),
        )
}
