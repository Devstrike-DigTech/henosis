package com.devstrike.sample

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A facade that takes an Android [Context] (lifecycle option `constructor: context`).
 *
 * Every method here maps to a row of the v0.1 type table, plus one deliberately unsupported
 * member ([generic]) so `heno doctor` has something to reject.
 */
class Greeter(
    private val context: Context,
) {
    /** `String -> String`. */
    fun greet(name: String): String = "Hello, $name! Greetings from ${context.packageName}."

    /** Nullable parameter (`String?`). */
    fun greetOptional(name: String?): String = greet(name ?: "stranger")

    /** `List<String> -> Map<String, String>`. */
    fun greetMany(names: List<String>): Map<String, String> = names.associateWith { greet(it) }

    /** `suspend`, `Long` parameter (precision warning) and a nullable data-class result. */
    suspend fun fetchProfile(id: Long): Profile? {
        delay(10)
        if (id <= 0L) return null
        return Profile(
            id = id,
            name = "user-$id",
            tags = listOf("sample", "generated"),
            level = Level.entries[(id % Level.entries.size).toInt()],
            avatar = null,
        )
    }

    /** Throws [ArithmeticException] on a zero divisor, to exercise error mapping. */
    fun divide(
        a: Int,
        b: Int,
    ): Double {
        if (b == 0) throw ArithmeticException("Division by zero")
        return a.toDouble() / b
    }

    /** Overload of [divide]; selected in config as `divide(Double, Double)`. */
    fun divide(
        a: Double,
        b: Double,
    ): Double {
        if (b == 0.0) throw ArithmeticException("Division by zero")
        return a / b
    }

    /** `ByteArray -> ByteArray`. */
    fun echoBytes(data: ByteArray): ByteArray = data.copyOf()

    /** An event source: `Flow<Int>` property, one tick per second. */
    val ticks: Flow<Int> =
        flow {
            var tick = 0
            while (true) {
                emit(tick++)
                delay(1_000)
            }
        }

    /** Deliberately unsupported in v0.1: a generic type parameter. `heno doctor` must reject it. */
    fun <T> generic(x: T): T = x

    /** Deprecated with a warning: still callable, so Henosis only flags it. */
    @Deprecated("use greet", ReplaceWith("greet(name)"))
    fun oldGreet(name: String): String = greet(name)

    /** Deprecated at ERROR level: the compiler refuses calls, so Henosis must refuse it too. */
    @Deprecated("moved to greet", level = DeprecationLevel.ERROR)
    fun legacyGreet(name: String): String = greet(name)
}
