package com.devstrike.henosis.core

import com.devstrike.henosis.core.types.KotlinTypes
import com.devstrike.henosis.core.types.SupportLevel
import com.devstrike.henosis.core.types.TypePosition
import com.devstrike.henosis.core.types.TypeRef
import com.devstrike.henosis.core.types.TypeRules
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class TypeRulesTest {
    private val rules = TypeRules(dataClasses = setOf("com.x.Profile"), enums = setOf("com.x.Level"))

    private fun t(
        name: String,
        vararg args: TypeRef,
        nullable: Boolean = false,
    ) = TypeRef(name, args.toList(), nullable)

    @Test
    fun `scalars and declared data classes and enums are ok`() {
        listOf(KotlinTypes.INT, KotlinTypes.STRING, KotlinTypes.BYTE_ARRAY, KotlinTypes.UNIT, "com.x.Profile", "com.x.Level")
            .forEach { rules.check(t(it), TypePosition.RETURN).shouldBeEmpty() }
    }

    @Test
    fun `Long warns about JavaScript precision`() {
        val findings = rules.check(t(KotlinTypes.LONG), TypePosition.PARAMETER)
        findings.single().level shouldBe SupportLevel.WARN
        findings.single().message shouldContain "2^53"
    }

    @Test
    fun `List and Map recurse into their arguments`() {
        rules.check(t(KotlinTypes.LIST, t(KotlinTypes.STRING)), TypePosition.RETURN).shouldBeEmpty()
        rules.levelOf(t(KotlinTypes.LIST, t(KotlinTypes.LONG)), TypePosition.RETURN) shouldBe SupportLevel.WARN
        rules.check(t(KotlinTypes.MAP, t(KotlinTypes.STRING), t("com.x.Profile")), TypePosition.RETURN).shouldBeEmpty()
    }

    @Test
    fun `Map with non-String key is unsupported`() {
        val findings = rules.check(t(KotlinTypes.MAP, t(KotlinTypes.INT), t(KotlinTypes.STRING)), TypePosition.RETURN)
        findings.first().level shouldBe SupportLevel.UNSUPPORTED
        findings.first().message shouldContain "Map keys must be non-null String"
    }

    @Test
    fun `type parameters and star projections are unsupported`() {
        rules.levelOf(TypeRef.typeParameter("T"), TypePosition.RETURN) shouldBe SupportLevel.UNSUPPORTED
        rules.levelOf(t(KotlinTypes.LIST, TypeRef.STAR), TypePosition.RETURN) shouldBe SupportLevel.UNSUPPORTED
    }

    @Test
    fun `Flow is an event type only`() {
        val flow = t(KotlinTypes.FLOW, t(KotlinTypes.INT))
        rules.check(flow, TypePosition.PROPERTY).shouldBeEmpty()
        rules.check(flow, TypePosition.RETURN).shouldBeEmpty()
        rules.levelOf(flow, TypePosition.PARAMETER) shouldBe SupportLevel.UNSUPPORTED
    }

    @Test
    fun `Context is allowed only as a constructor parameter`() {
        val context = t(KotlinTypes.ANDROID_CONTEXT)
        rules.check(context, TypePosition.CONSTRUCTOR_PARAMETER).shouldBeEmpty()
        rules.levelOf(context, TypePosition.PARAMETER) shouldBe SupportLevel.UNSUPPORTED
    }

    @Test
    fun `platform types warn and unknown classes are unsupported`() {
        val platform = TypeRef(KotlinTypes.STRING, nullable = true, platform = true)
        rules.levelOf(platform, TypePosition.RETURN) shouldBe SupportLevel.WARN
        rules.levelOf(t("java.io.File"), TypePosition.RETURN) shouldBe SupportLevel.UNSUPPORTED
    }

    @Test
    fun `render follows Kotlin syntax`() {
        t(KotlinTypes.MAP, t(KotlinTypes.STRING), t(KotlinTypes.LIST, t(KotlinTypes.INT)), nullable = true).render() shouldBe
            "Map<String, List<Int>>?"
        TypeRef(KotlinTypes.STRING, nullable = true, platform = true).render() shouldBe "String!"
        TypeRef.typeParameter("T").render() shouldBe "T"
    }
}
