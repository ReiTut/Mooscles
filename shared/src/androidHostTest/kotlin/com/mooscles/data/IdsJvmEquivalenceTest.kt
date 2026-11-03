package com.mooscles.data

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ids stored in the database must keep the exact text `UUID.randomUUID().toString()` gave. */
class IdsJvmEquivalenceTest {
    @Test
    fun newIdParsesAsJavaUuidWithSameText() {
        repeat(1_000) {
            val id = newId()
            val parsed = UUID.fromString(id)
            assertEquals(id, parsed.toString())
            assertEquals(4, parsed.version(), id)
            assertEquals(2, parsed.variant(), id)
        }
    }
}
