package com.hotel.converter.adapter

import com.hotel.converter.domain.ValidationResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderAAdapterTest {
    private val adapter = ProviderAAdapter()

    @Test
    fun `valid conversion computes check_out by adding nights to arrival`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "Maria Oliveira")
                put("arrival", "2026-09-10")
                put("nights", 3)
                put("room_count", 2)
                put("channel_reference", "EXT-BOOKING-994")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("Maria Oliveira", draft.guestName)
        assertEquals("2026-09-10", draft.checkIn)
        assertEquals("2026-09-13", draft.checkOut)
        assertEquals(3, draft.nights)
        assertEquals(2, draft.rooms)
        assertEquals("EXT-BOOKING-994", draft.channelReference)
        assertEquals("PROVIDER_A", draft.sourceProvider)
    }

    @Test
    fun `month and year rollover are handled accurately by LocalDate`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "Joao Pereira")
                put("arrival", "2026-12-30")
                put("nights", 3)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("2026-12-30", draft.checkIn)
        assertEquals("2027-01-02", draft.checkOut)
        assertEquals(3, draft.nights)
    }

    @Test
    fun `leap year rollover is handled correctly`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "Roberta Dias")
                put("arrival", "2028-02-28")
                put("nights", 2)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("2028-02-28", draft.checkIn)
        assertEquals("2028-03-01", draft.checkOut)
        assertEquals(2, draft.nights)
    }

    @Test
    fun `guest_full_name is sanitized according to RN-004`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "  Juliana \t\r\n Silveira  ")
                put("arrival", "2026-11-01")
                put("nights", 4)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("Juliana Silveira", draft.guestName)
    }

    @Test
    fun `missing room_count defaults to 1 according to RN-005`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "Lucas Mendes")
                put("arrival", "2026-10-15")
                put("nights", 2)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals(1, draft.rooms)
    }

    @Test
    fun `missing channel_reference defaults to NA`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "Lucas Mendes")
                put("arrival", "2026-10-15")
                put("nights", 2)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("N/A", draft.channelReference)
    }

    @Test
    fun `missing guest_full_name returns FIELD_REQUIRED`() {
        val payload =
            buildJsonObject {
                put("arrival", "2026-09-10")
                put("nights", 3)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "guest_full_name" }
        assertEquals("FIELD_REQUIRED", err?.errorCode)
    }

    @Test
    fun `empty or whitespace-only guest_full_name returns INVALID_SCHEMA`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "    ")
                put("arrival", "2026-09-10")
                put("nights", 3)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "guest_full_name" }
        assertEquals("INVALID_SCHEMA", err?.errorCode)
    }

    @Test
    fun `missing arrival returns FIELD_REQUIRED`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "Lucas Mendes")
                put("nights", 3)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "arrival" }
        assertEquals("FIELD_REQUIRED", err?.errorCode)
    }

    @Test
    fun `invalid arrival date format returns INVALID_SCHEMA`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "Lucas Mendes")
                put("arrival", "2026/09/10")
                put("nights", 3)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "payload" }
        assertEquals("INVALID_SCHEMA", err?.errorCode)
    }

    @Test
    fun `missing nights returns FIELD_REQUIRED`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "Lucas Mendes")
                put("arrival", "2026-09-10")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "nights" }
        assertEquals("FIELD_REQUIRED", err?.errorCode)
    }

    @Test
    fun `non-numeric nights returns INVALID_SCHEMA`() {
        val json =
            """
            {
              "guest_full_name": "Lucas Mendes",
              "arrival": "2026-09-10",
              "nights": "duas"
            }
            """.trimIndent()
        val payload = Json.parseToJsonElement(json).jsonObject

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "nights" }
        assertEquals("INVALID_SCHEMA", err?.errorCode)
    }

    @Test
    fun `nights less than or equal to 0 returns CHECKOUT_BEFORE_CHECKIN`() {
        val payloadZero =
            buildJsonObject {
                put("guest_full_name", "Lucas Mendes")
                put("arrival", "2026-09-10")
                put("nights", 0)
            }
        val resultZero = adapter.convert(payloadZero)
        assertTrue(resultZero is ValidationResult.Failure)
        val errorsZero = (resultZero as ValidationResult.Failure).errors
        val errZero = errorsZero.firstOrNull { it.field == "nights" }
        assertEquals("CHECKOUT_BEFORE_CHECKIN", errZero?.errorCode)

        val payloadNeg =
            buildJsonObject {
                put("guest_full_name", "Lucas Mendes")
                put("arrival", "2026-09-10")
                put("nights", -2)
            }
        val resultNeg = adapter.convert(payloadNeg)
        assertTrue(resultNeg is ValidationResult.Failure)
        val errorsNeg = (resultNeg as ValidationResult.Failure).errors
        val errNeg = errorsNeg.firstOrNull { it.field == "nights" }
        assertEquals("CHECKOUT_BEFORE_CHECKIN", errNeg?.errorCode)
    }

    @Test
    fun `room_count less than or equal to 0 returns INVALID_SCHEMA`() {
        val payload =
            buildJsonObject {
                put("guest_full_name", "Lucas Mendes")
                put("arrival", "2026-09-10")
                put("nights", 2)
                put("room_count", 0)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "room_count" }
        assertEquals("INVALID_SCHEMA", err?.errorCode)
    }

    @Test
    fun `multiple schema errors are collected and reported together`() {
        val payload =
            buildJsonObject {
                put("room_count", -1)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        assertFalse(errors.isEmpty())
        assertTrue(errors.any { it.field == "guest_full_name" && it.errorCode == "FIELD_REQUIRED" })
        assertTrue(errors.any { it.field == "arrival" && it.errorCode == "FIELD_REQUIRED" })
        assertTrue(errors.any { it.field == "nights" && it.errorCode == "FIELD_REQUIRED" })
        assertTrue(errors.any { it.field == "room_count" && it.errorCode == "INVALID_SCHEMA" })
    }
}
