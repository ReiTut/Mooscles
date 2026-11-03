package com.mooscles.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The iOS migration test cannot read `shared/schemas`; its copy of version 5 must stay the exported one. */
@RunWith(RobolectricTestRunner::class) // org.json is Android's
@Config(sdk = [35])
class SchemaV5FixtureTest {
    @Test fun matchesTheExportedJson() {
        assertEquals(exportedSchemaStatements(SchemaV5.VERSION), SchemaV5.statements)
        assertEquals(exportedIdentityHash(SchemaV5.VERSION), SchemaV5.IDENTITY_HASH)
    }
}
