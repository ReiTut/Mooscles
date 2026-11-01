package com.mooscles.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdsTest {
    private val versionFour = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    @Test
    fun newIdIsALowercaseVersionFourUuid() {
        repeat(1_000) {
            val id = newId()
            assertTrue(versionFour.matches(id), "Not a lowercase version 4 UUID: $id")
        }
    }

    @Test
    fun newIdsAreDistinct() {
        val ids = List(1_000) { newId() }
        assertEquals(ids.size, ids.toSet().size)
    }
}
