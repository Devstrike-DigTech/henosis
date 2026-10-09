package com.devstrike.henosis.core.inspect

import com.devstrike.henosis.core.api.ApiClass
import com.devstrike.henosis.core.api.ApiClassKind
import com.devstrike.henosis.core.api.ApiConstructor
import com.devstrike.henosis.core.api.ApiDeprecation
import com.devstrike.henosis.core.api.ApiDeprecationLevel
import com.devstrike.henosis.core.api.ApiFunction
import com.devstrike.henosis.core.api.ApiModel
import com.devstrike.henosis.core.api.ApiParameter
import com.devstrike.henosis.core.api.ApiProperty
import com.devstrike.henosis.core.types.Finding
import com.devstrike.henosis.core.types.SupportLevel
import com.devstrike.henosis.core.types.TypePosition
import com.devstrike.henosis.core.types.TypeRules
import kotlinx.serialization.Serializable

/** The result of `heno inspect`: every public class and member, tagged with how well it can be bridged. */
@Serializable
data class InspectReport(
    /** Schema version of this report; bump [FORMAT] when the JSON shape changes. */
    val format: Int,
    val classes: List<InspectedClass>,
) {
    companion object {
        /** The current report schema version. */
        const val FORMAT: Int = 1
    }
}

@Serializable
data class InspectedClass(
    val name: String,
    val kind: ApiClassKind,
    val status: SupportLevel,
    val typeParameters: List<String> = emptyList(),
    val enumEntries: List<String> = emptyList(),
    val constructors: List<InspectedMember> = emptyList(),
    val functions: List<InspectedMember> = emptyList(),
    val properties: List<InspectedMember> = emptyList(),
)

/** One member with its Kotlin-style signature and the findings that decide its [status]. */
@Serializable
data class InspectedMember(
    val name: String,
    val signature: String,
    val status: SupportLevel,
    val findings: List<Finding> = emptyList(),
)

/** Builds an [InspectReport] from an [ApiModel] by running [TypeRules] over every member. */
object Inspector {
    fun inspect(model: ApiModel): InspectReport {
        val rules = TypeRules.forModel(model)
        return InspectReport(format = InspectReport.FORMAT, classes = model.classes.map { inspectClass(it, rules) })
    }

    private fun inspectClass(
        cls: ApiClass,
        rules: TypeRules,
    ): InspectedClass {
        val constructors = cls.constructors.map { inspectConstructor(it, rules) }
        val functions = cls.functions.map { inspectFunction(it, rules) }
        val properties = cls.properties.map { inspectProperty(it, rules) }
        val memberLevels = (constructors + functions + properties).map { it.status }
        val classLevel =
            if (cls.typeParameters.isNotEmpty()) SupportLevel.UNSUPPORTED else SupportLevel.worst(memberLevels)
        return InspectedClass(
            name = cls.name,
            kind = cls.kind,
            status = classLevel,
            typeParameters = cls.typeParameters,
            enumEntries = cls.enumEntries,
            constructors = constructors,
            functions = functions,
            properties = properties,
        )
    }

    private fun inspectConstructor(
        ctor: ApiConstructor,
        rules: TypeRules,
    ): InspectedMember {
        val findings = ctor.parameters.flatMap { p -> parameterFindings(p, TypePosition.CONSTRUCTOR_PARAMETER, rules) }
        return member("<init>", "constructor(${renderParameters(ctor.parameters)})", findings)
    }

    private fun inspectFunction(
        fn: ApiFunction,
        rules: TypeRules,
    ): InspectedMember {
        val findings =
            listOfNotNull(deprecationFinding(fn.deprecation)) +
                fn.parameters.flatMap { p -> parameterFindings(p, TypePosition.PARAMETER, rules) } +
                rules.check(fn.returnType, TypePosition.RETURN).map { it.at("return type") }
        val signature =
            buildString {
                if (fn.isSuspend) append("suspend ")
                append("fun ")
                if (fn.typeParameters.isNotEmpty()) append(fn.typeParameters.joinToString(", ", "<", "> "))
                append(fn.name).append('(').append(renderParameters(fn.parameters)).append("): ")
                append(fn.returnType.render())
            }
        return member(fn.name, signature, findings)
    }

    private fun inspectProperty(
        prop: ApiProperty,
        rules: TypeRules,
    ): InspectedMember {
        val findings =
            listOfNotNull(deprecationFinding(prop.deprecation)) + rules.check(prop.type, TypePosition.PROPERTY).map { it.at("type") }
        val keyword = if (prop.isMutable) "var" else "val"
        return member(prop.name, "$keyword ${prop.name}: ${prop.type.render()}", findings)
    }

    /** ERROR/HIDDEN deprecations cannot be compiled against; WARNING ones are flagged so the user knows. */
    fun deprecationFinding(deprecation: ApiDeprecation?): Finding? {
        deprecation ?: return null
        val reason = deprecation.message?.let { ": $it" } ?: ""
        return when (deprecation.level) {
            ApiDeprecationLevel.WARNING -> {
                Finding(SupportLevel.WARN, "deprecated$reason")
            }

            ApiDeprecationLevel.ERROR, ApiDeprecationLevel.HIDDEN -> {
                Finding(
                    SupportLevel.UNSUPPORTED,
                    "deprecated with level ${deprecation.level.name}$reason; generated code could not call it",
                )
            }
        }
    }

    private fun parameterFindings(
        p: ApiParameter,
        position: TypePosition,
        rules: TypeRules,
    ): List<Finding> = rules.check(p.type, position).map { it.at("parameter '${p.name}'") }

    private fun renderParameters(parameters: List<ApiParameter>): String =
        parameters.joinToString(", ") { p ->
            "${p.name}: ${p.type.render()}" + if (p.hasDefault) " = ..." else ""
        }

    private fun member(
        name: String,
        signature: String,
        findings: List<Finding>,
    ): InspectedMember =
        InspectedMember(
            name = name,
            signature = signature,
            status = SupportLevel.worst(findings.map { it.level }),
            findings = findings,
        )
}
