package com.mooscles.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

object BackupCodec {
    const val FORMAT = "mooscles-backup"
    const val FORMAT_VERSION = 1

    // Compact output: the contract is JSON, and a large archive is noticeably smaller without indentation.
    // Internal so the tests read a new file exactly as an older app's codec, with this same configuration, would.
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Only the two fields that decide how to read the rest; every other key is skipped without building a tree. */
    @Serializable
    private class Header(val format: JsonElement? = null, val formatVersion: JsonElement? = null)

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val body = text.removePrefix("\uFEFF")
        val header = try {
            json.decodeFromString(Header.serializer(), body)
        } catch (e: SerializationException) {
            throw notAnObject(body, e)
        } catch (e: IllegalArgumentException) {
            throw notAnObject(body, e)
        }
        val format = (header.format as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != FORMAT) throw BackupException.NotABackup
        val version = (header.formatVersion as? JsonPrimitive)?.intOrNull
            ?: throw BackupException.Corrupted("missing or invalid formatVersion")
        if (version > FORMAT_VERSION) throw BackupException.NewerVersion(version)
        if (version < 1) throw BackupException.Corrupted("invalid formatVersion $version")
        return try {
            json.decodeFromString(BackupFile.serializer(), body)
        } catch (e: SerializationException) {
            throw BackupException.Corrupted(e.message ?: "unreadable backup")
        } catch (e: IllegalArgumentException) {
            throw BackupException.Corrupted(e.message ?: "unreadable backup")
        }
    }

    /**
     * The header reads any well-formed JSON object, so it failed on malformed JSON or on JSON that is not an
     * object. Only this failure path parses the whole text, to tell the two apart.
     */
    private fun notAnObject(body: String, cause: Exception): BackupException {
        val wellFormed = try {
            json.parseToJsonElement(body)
            true
        } catch (e: SerializationException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
        return if (wellFormed) BackupException.NotABackup else BackupException.Corrupted(cause.message ?: "unreadable JSON")
    }
}
