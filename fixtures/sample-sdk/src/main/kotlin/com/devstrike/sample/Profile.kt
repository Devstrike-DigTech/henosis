package com.devstrike.sample

/** Exercises the `data class` row, with nested `List`, enum, `Long` and a nullable `ByteArray`. */
@Suppress("ArrayInDataClass")
data class Profile(
    val id: Long,
    val name: String,
    val tags: List<String>,
    val level: Level,
    val avatar: ByteArray?,
)
