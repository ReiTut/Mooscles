package com.mooscles.domain

import kotlin.time.Clock

private val systemClock: Clock = Clock.System

/** Milliseconds since the Unix epoch from the system clock. */
fun currentTimeMillis(): Long = systemClock.now().toEpochMilliseconds()

/** The current instant as ISO-8601 UTC text, in the same form the JVM's `Instant.toString()` writes. */
fun currentIsoInstant(): String = systemClock.now().toString()
