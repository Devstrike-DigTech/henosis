package com.devstrike.sample

/**
 * A facade with a no-arg constructor (lifecycle option `constructor: none`), covering the
 * remaining scalar rows (`Short`, `Byte`, `Float`, `Boolean`), `Unit` and default arguments.
 */
class Counter {
    /** Current count; a read-only property. */
    var count: Int = 0
        private set

    /** Increments by [by] (default 1) and returns the new count. */
    fun increment(by: Int = 1): Int {
        count += by
        return count
    }

    /** Returns `Unit`. */
    fun reset() {
        count = 0
    }

    /** Mixes the small scalar types into one call. */
    fun mix(
        small: Short,
        tiny: Byte,
        ratio: Float,
        enabled: Boolean,
    ): Double = if (enabled) (small + tiny) * ratio.toDouble() else 0.0
}
