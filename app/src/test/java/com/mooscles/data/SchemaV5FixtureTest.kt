package com.mooscles.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The version 5 migration fixture must match the schema exported in `shared/schemas`. */
@RunWith(RobolectricTestRunner::class) // org.json is Android's
@Config(sdk = [35])
class SchemaV5FixtureTest {
    @Test fun matchesTheExportedJson() {
        assertEquals(exportedSchemaStatements(SchemaV5.VERSION), SchemaV5.statements)
        assertEquals(exportedIdentityHash(SchemaV5.VERSION), SchemaV5.IDENTITY_HASH)
    }
}
