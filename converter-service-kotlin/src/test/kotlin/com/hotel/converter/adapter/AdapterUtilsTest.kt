package com.hotel.converter.adapter

import com.hotel.converter.domain.ValidationError
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdapterUtilsTest {
    @Test
    fun `resolveRoomCount defaults to 1 when room keys are absent according to RN-005`() {
        val payload = buildJsonObject { put("some_field", "value") }
        val errors = mutableListOf<ValidationError>()

        val rooms = resolveRoomCount(payload, errors)

        assertEquals(1, rooms)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `resolveRoomCount defaults to 1 when room_count is JsonNull`() {
        val payload = buildJsonObject { put("room_count", null as String?) }
        val errors = mutableListOf<ValidationError>()

        val rooms = resolveRoomCount(payload, errors)

        assertEquals(1, rooms)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `resolveRoomCount extracts valid integer from room_count`() {
        val payload = buildJsonObject { put("room_count", 3) }
        val errors = mutableListOf<ValidationError>()

        val rooms = resolveRoomCount(payload, errors)

        assertEquals(3, rooms)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `resolveRoomCount extracts valid integer from alternative rooms key`() {
        val payload = buildJsonObject { put("rooms", 4) }
        val errors = mutableListOf<ValidationError>()

        val rooms = resolveRoomCount(payload, errors)

        assertEquals(4, rooms)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `resolveRoomCount parses string integer value`() {
        val payload = buildJsonObject { put("room_count", " 2 ") }
        val errors = mutableListOf<ValidationError>()

        val rooms = resolveRoomCount(payload, errors)

        assertEquals(2, rooms)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `resolveRoomCount adds INVALID_SCHEMA error when value is 0`() {
        val payload = buildJsonObject { put("room_count", 0) }
        val errors = mutableListOf<ValidationError>()

        val rooms = resolveRoomCount(payload, errors)

        assertEquals(1, rooms)
        assertEquals(1, errors.size)
        assertEquals("room_count", errors[0].field)
        assertEquals("INVALID_SCHEMA", errors[0].errorCode)
    }

    @Test
    fun `resolveRoomCount adds INVALID_SCHEMA error when value is negative`() {
        val payload = buildJsonObject { put("room_count", -2) }
        val errors = mutableListOf<ValidationError>()

        val rooms = resolveRoomCount(payload, errors)

        assertEquals(1, rooms)
        assertEquals(1, errors.size)
        assertEquals("room_count", errors[0].field)
        assertEquals("INVALID_SCHEMA", errors[0].errorCode)
    }

    @Test
    fun `resolveRoomCount adds INVALID_SCHEMA error when value is non-integer string`() {
        val payload = buildJsonObject { put("room_count", "not-a-number") }
        val errors = mutableListOf<ValidationError>()

        val rooms = resolveRoomCount(payload, errors)

        assertEquals(1, rooms)
        assertEquals(1, errors.size)
        assertEquals("room_count", errors[0].field)
        assertEquals("INVALID_SCHEMA", errors[0].errorCode)
    }

    @Test
    fun `resolveRoomCount adds INVALID_SCHEMA error when value is json object without throwing`() {
        val payload =
            buildJsonObject {
                put("room_count", buildJsonObject { put("nested", 1) })
            }
        val errors = mutableListOf<ValidationError>()

        val rooms = resolveRoomCount(payload, errors)

        assertEquals(1, rooms)
        assertEquals(1, errors.size)
        assertEquals("room_count", errors[0].field)
        assertEquals("INVALID_SCHEMA", errors[0].errorCode)
    }

    @Test
    fun `resolveRoomCount adds INVALID_SCHEMA error when value is json array without throwing`() {
        val payload =
            buildJsonObject {
                put("room_count", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(1)) })
            }
        val errors = mutableListOf<ValidationError>()

        val rooms = resolveRoomCount(payload, errors)

        assertEquals(1, rooms)
        assertEquals(1, errors.size)
        assertEquals("room_count", errors[0].field)
        assertEquals("INVALID_SCHEMA", errors[0].errorCode)
    }

    @Test
    fun `getStringField extracts string primitive or returns null`() {
        val payload =
            buildJsonObject {
                put("valid_str", "hello")
                put("int_field", 123)
            }

        assertEquals("hello", getStringField(payload, "valid_str"))
        assertNull(getStringField(payload, "int_field"))
        assertNull(getStringField(payload, "non_existent"))
    }
}
