package com.mooscles.data

import kotlin.uuid.Uuid

/** A random version 4 UUID as lowercase hyphenated text, in the same form the JVM's `UUID.toString()` writes. */
fun newId(): String = Uuid.random().toString()
