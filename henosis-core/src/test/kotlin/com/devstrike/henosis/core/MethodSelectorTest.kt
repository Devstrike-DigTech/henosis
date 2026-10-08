package com.devstrike.henosis.core

import com.devstrike.henosis.core.api.ApiFunction
import com.devstrike.henosis.core.api.ApiParameter
import com.devstrike.henosis.core.config.MethodSelector
import com.devstrike.henosis.core.types.KotlinTypes
import com.devstrike.henosis.core.types.TypeRef
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MethodSelectorTest {
    private fun fn(
        name: String,
        vararg params: TypeRef,
    ) = ApiFunction(
        name = name,
        parameters = params.mapIndexed { i, t -> ApiParameter("p$i", t) },
        returnType = TypeRef(KotlinTypes.UNIT),
        jvmName = name,
        jvmDescriptor = "",
    )

    @Test
    fun `a bare name matches any overload`() {
        val s = MethodSelector.parse("greet")
        s.name shouldBe "greet"
        s.parameterTypes shouldBe null
        s.exposedName shouldBe "greet"
        s.matches(fn("greet", TypeRef(KotlinTypes.STRING))).shouldBeTrue()
        s.matches(fn("other")).shouldBeFalse()
    }

    @Test
    fun `parameter types select one overload by simple or qualified name`() {
        val s = MethodSelector.parse("divide(Int, Int)")
        s.parameterTypes shouldContainExactly listOf("Int", "Int")
        s.matches(fn("divide", TypeRef(KotlinTypes.INT), TypeRef(KotlinTypes.INT))).shouldBeTrue()
        s.matches(fn("divide", TypeRef(KotlinTypes.DOUBLE), TypeRef(KotlinTypes.DOUBLE))).shouldBeFalse()
        MethodSelector
            .parse(
                "divide(kotlin.Int, kotlin.Int)",
            ).matches(fn("divide", TypeRef(KotlinTypes.INT), TypeRef(KotlinTypes.INT)))
            .shouldBeTrue()
        MethodSelector.parse("f(String?)").matches(fn("f", TypeRef(KotlinTypes.STRING, nullable = true))).shouldBeTrue()
        MethodSelector.parse("f(String)").matches(fn("f", TypeRef(KotlinTypes.STRING, nullable = true))).shouldBeFalse()
    }

    @Test
    fun `generic parameter types split on top-level commas only`() {
        val s = MethodSelector.parse("f(Map<String, Int>, List<String>) as g")
        s.parameterTypes shouldContainExactly listOf("Map<String, Int>", "List<String>")
        s.alias shouldBe "g"
        s.exposedName shouldBe "g"
        val map = TypeRef(KotlinTypes.MAP, listOf(TypeRef(KotlinTypes.STRING), TypeRef(KotlinTypes.INT)))
        val list = TypeRef(KotlinTypes.LIST, listOf(TypeRef(KotlinTypes.STRING)))
        s.matches(fn("f", map, list)).shouldBeTrue()
        s.render() shouldBe "f(Map<String, Int>, List<String>) as g"
    }

    @Test
    fun `empty parentheses select the zero-argument overload`() {
        val s = MethodSelector.parse("reset()")
        s.parameterTypes shouldBe emptyList()
        s.matches(fn("reset")).shouldBeTrue()
        s.matches(fn("reset", TypeRef(KotlinTypes.INT))).shouldBeFalse()
    }

    @Test
    fun `bad syntax is rejected with a reason`() {
        shouldThrow<IllegalArgumentException> { MethodSelector.parse("divide(Int,") }
        shouldThrow<IllegalArgumentException> { MethodSelector.parse("divide(Int,, Int)") }
        shouldThrow<IllegalArgumentException> { MethodSelector.parse("1bad") }
        shouldThrow<IllegalArgumentException> { MethodSelector.parse("greet as") }
    }
}
