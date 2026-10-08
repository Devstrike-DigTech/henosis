package com.devstrike.henosis.parser.android

import com.devstrike.henosis.core.api.ApiClassKind
import com.devstrike.henosis.core.api.ApiOrigin
import com.devstrike.henosis.core.types.KotlinTypes
import com.devstrike.henosis.core.types.TypeRef
import com.devstrike.henosis.core.types.TypeRefKind
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeSorted
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AndroidSdkParserTest {
    private val model = SampleSdk.model
    private val greeter = model.findClass("com.devstrike.sample.Greeter").shouldNotBeNull()

    @Test
    fun `classes are sorted and only public API is kept`() {
        model.classes.map { it.name }.shouldBeSorted()
        model.classes.map { it.name } shouldContainExactly
            listOf(
                "com.devstrike.sample.Counter",
                "com.devstrike.sample.Greeter",
                "com.devstrike.sample.LegacyApi",
                "com.devstrike.sample.Level",
                "com.devstrike.sample.Profile",
            )
    }

    @Test
    fun `Greeter takes a Context in its constructor`() {
        greeter.kind shouldBe ApiClassKind.CLASS
        greeter.origin shouldBe ApiOrigin.KOTLIN
        val ctor = greeter.constructors.single()
        ctor.parameters.single().type shouldBe TypeRef(KotlinTypes.ANDROID_CONTEXT)
        ctor.jvmDescriptor shouldBe "(Landroid/content/Context;)V"
    }

    @Test
    fun `suspend and nullability come from Kotlin metadata`() {
        val fetch = greeter.functions.single { it.name == "fetchProfile" }
        fetch.isSuspend.shouldBeTrue()
        fetch.parameters.single().type shouldBe TypeRef(KotlinTypes.LONG)
        fetch.returnType shouldBe TypeRef("com.devstrike.sample.Profile", nullable = true)
        fetch.jvmDescriptor shouldBe "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;"

        val greetOptional = greeter.functions.single { it.name == "greetOptional" }
        greetOptional.parameters.single().type shouldBe TypeRef(KotlinTypes.STRING, nullable = true)
        greetOptional.isSuspend.shouldBeFalse()
    }

    @Test
    fun `overloads are both listed with distinct descriptors`() {
        val divides = greeter.functions.filter { it.name == "divide" }
        divides shouldHaveSize 2
        divides.map { it.jvmDescriptor } shouldContainExactly listOf("(DD)D", "(II)D")
    }

    @Test
    fun `generic collections keep their type arguments`() {
        val greetMany = greeter.functions.single { it.name == "greetMany" }
        greetMany.parameters.single().type shouldBe TypeRef(KotlinTypes.LIST, listOf(TypeRef(KotlinTypes.STRING)))
        greetMany.returnType shouldBe TypeRef(KotlinTypes.MAP, listOf(TypeRef(KotlinTypes.STRING), TypeRef(KotlinTypes.STRING)))
    }

    @Test
    fun `Flow property is read as a property`() {
        val ticks = greeter.properties.single { it.name == "ticks" }
        ticks.type shouldBe TypeRef(KotlinTypes.FLOW, listOf(TypeRef(KotlinTypes.INT)))
        ticks.isMutable.shouldBeFalse()
        ticks.getterJvmName shouldBe "getTicks"
    }

    @Test
    fun `generic function exposes its type parameter`() {
        val generic = greeter.functions.single { it.name == "generic" }
        generic.typeParameters shouldContainExactly listOf("T")
        generic.parameters
            .single()
            .type.kind shouldBe TypeRefKind.TYPE_PARAMETER
        generic.returnType shouldBe TypeRef.typeParameter("T")
    }

    @Test
    fun `data class and enum are detected and their generated members hidden`() {
        val profile = model.findClass("com.devstrike.sample.Profile").shouldNotBeNull()
        profile.kind shouldBe ApiClassKind.DATA_CLASS
        profile.functions shouldHaveSize 0
        profile.properties.map { it.name } shouldContainExactly listOf("avatar", "id", "level", "name", "tags")
        profile.properties.single { it.name == "avatar" }.type shouldBe TypeRef(KotlinTypes.BYTE_ARRAY, nullable = true)

        val level = model.findClass("com.devstrike.sample.Level").shouldNotBeNull()
        level.kind shouldBe ApiClassKind.ENUM_CLASS
        level.enumEntries shouldContainExactly listOf("BRONZE", "SILVER", "GOLD")
        level.functions shouldHaveSize 0
        level.constructors shouldHaveSize 0
    }

    @Test
    fun `default arguments are flagged`() {
        val counter = model.findClass("com.devstrike.sample.Counter").shouldNotBeNull()
        counter.constructors.single().parameters shouldHaveSize 0
        val increment = counter.functions.single { it.name == "increment" }
        increment.parameters
            .single()
            .hasDefault
            .shouldBeTrue()
        counter.functions.single { it.name == "reset" }.returnType shouldBe TypeRef(KotlinTypes.UNIT)
        counter.properties
            .single { it.name == "count" }
            .isMutable
            .shouldBeFalse()
    }

    @Test
    fun `Java classes yield platform types`() {
        val legacy = model.findClass("com.devstrike.sample.LegacyApi").shouldNotBeNull()
        legacy.origin shouldBe ApiOrigin.JAVA
        val ping = legacy.functions.single { it.name == "ping" }
        ping.parameters.single().type shouldBe TypeRef(KotlinTypes.STRING, nullable = true, platform = true)
        ping.returnType shouldBe TypeRef(KotlinTypes.STRING, nullable = true, platform = true)
        val names = legacy.functions.single { it.name == "names" }
        val platformString = TypeRef(KotlinTypes.STRING, nullable = true, platform = true)
        names.returnType shouldBe TypeRef(KotlinTypes.LIST, listOf(platformString), nullable = true, platform = true)
        legacy.functions.single { it.name == "count" }.returnType shouldBe TypeRef(KotlinTypes.INT)
    }
}
