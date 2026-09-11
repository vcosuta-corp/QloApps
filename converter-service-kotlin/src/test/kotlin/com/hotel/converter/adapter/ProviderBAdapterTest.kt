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

class ProviderBAdapterTest {
    private val adapter = ProviderBAdapter()

    @Test
    fun `valid conversion calculates nights from checkin_date and checkout_date`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Ricardo")
                        put("last_name", "Menezes")
                    },
                )
                put("checkin_date", "2026-12-20")
                put("checkout_date", "2026-12-25")
                put("reference_id", "EXP-889")
                put("room_count", 2)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("Ricardo Menezes", draft.guestName)
        assertEquals("2026-12-20", draft.checkIn)
        assertEquals("2026-12-25", draft.checkOut)
        assertEquals(5, draft.nights)
        assertEquals(2, draft.rooms)
        assertEquals("EXP-889", draft.channelReference)
        assertEquals("PROVIDER_B", draft.sourceProvider)
    }

    @Test
    fun `month rollover correctly calculates nights across months`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Beatriz")
                        put("last_name", "Lima")
                    },
                )
                put("checkin_date", "2026-10-30")
                put("checkout_date", "2026-11-03")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("2026-10-30", draft.checkIn)
        assertEquals("2026-11-03", draft.checkOut)
        assertEquals(4, draft.nights)
    }

    @Test
    fun `year rollover correctly calculates nights across years`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Beatriz")
                        put("last_name", "Lima")
                    },
                )
                put("checkin_date", "2026-12-29")
                put("checkout_date", "2027-01-03")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("2026-12-29", draft.checkIn)
        assertEquals("2027-01-03", draft.checkOut)
        assertEquals(5, draft.nights)
    }

    @Test
    fun `leap year rollover correctly computes nights including Feb 29`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Beatriz")
                        put("last_name", "Lima")
                    },
                )
                put("checkin_date", "2028-02-27")
                put("checkout_date", "2028-03-02")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("2028-02-27", draft.checkIn)
        assertEquals("2028-03-02", draft.checkOut)
        assertEquals(4, draft.nights)
    }

    @Test
    fun `customer first_name and last_name are sanitized according to RN-004`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "  Ana \t ")
                        put("last_name", " Costa \r\n ")
                    },
                )
                put("checkin_date", "2026-12-20")
                put("checkout_date", "2026-12-22")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("Ana Costa", draft.guestName)
    }

    @Test
    fun `missing room_count defaults to 1 according to RN-005`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Lucas")
                        put("last_name", "Alves")
                    },
                )
                put("checkin_date", "2026-12-20")
                put("checkout_date", "2026-12-22")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals(1, draft.rooms)
    }

    @Test
    fun `rooms key is supported as alternative to room_count`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Lucas")
                        put("last_name", "Alves")
                    },
                )
                put("checkin_date", "2026-12-20")
                put("checkout_date", "2026-12-22")
                put("rooms", 3)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals(3, draft.rooms)
    }

    @Test
    fun `missing reference_id defaults to NA`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Lucas")
                        put("last_name", "Alves")
                    },
                )
                put("checkin_date", "2026-12-20")
                put("checkout_date", "2026-12-22")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("N/A", draft.channelReference)
    }

    @Test
    fun `missing customer returns FIELD_REQUIRED`() {
        val payload =
            buildJsonObject {
                put("checkin_date", "2026-12-20")
                put("checkout_date", "2026-12-22")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "customer" }
        assertEquals("FIELD_REQUIRED", err?.errorCode)
    }

    @Test
    fun `non-object customer returns INVALID_SCHEMA`() {
        val json =
            """
            {
              "customer": "invalid-string",
              "checkin_date": "2026-12-20",
              "checkout_date": "2026-12-22"
            }
            """.trimIndent()
        val payload = Json.parseToJsonElement(json).jsonObject

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "customer" }
        assertEquals("INVALID_SCHEMA", err?.errorCode)
    }

    @Test
    fun `missing or blank customer first_name returns FIELD_REQUIRED`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "   ")
                        put("last_name", "Alves")
                    },
                )
                put("checkin_date", "2026-12-20")
                put("checkout_date", "2026-12-22")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "customer.first_name" }
        assertEquals("FIELD_REQUIRED", err?.errorCode)
    }

    @Test
    fun `missing or blank customer last_name returns FIELD_REQUIRED`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Lucas")
                    },
                )
                put("checkin_date", "2026-12-20")
                put("checkout_date", "2026-12-22")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "customer.last_name" }
        assertEquals("FIELD_REQUIRED", err?.errorCode)
    }

    @Test
    fun `missing checkin_date returns FIELD_REQUIRED`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Lucas")
                        put("last_name", "Alves")
                    },
                )
                put("checkout_date", "2026-12-22")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "checkin_date" }
        assertEquals("FIELD_REQUIRED", err?.errorCode)
    }

    @Test
    fun `missing checkout_date returns FIELD_REQUIRED`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Lucas")
                        put("last_name", "Alves")
                    },
                )
                put("checkin_date", "2026-12-20")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "checkout_date" }
        assertEquals("FIELD_REQUIRED", err?.errorCode)
    }

    @Test
    fun `invalid date format in checkin_date returns INVALID_SCHEMA`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Lucas")
                        put("last_name", "Alves")
                    },
                )
                put("checkin_date", "2026/12/20")
                put("checkout_date", "2026-12-22")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "payload" }
        assertEquals("INVALID_SCHEMA", err?.errorCode)
    }

    @Test
    fun `checkout_date before checkin_date returns CHECKOUT_BEFORE_CHECKIN`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Lucas")
                        put("last_name", "Alves")
                    },
                )
                put("checkin_date", "2026-12-10")
                put("checkout_date", "2026-12-08")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "checkout_date" }
        assertEquals("CHECKOUT_BEFORE_CHECKIN", err?.errorCode)
    }

    @Test
    fun `checkout_date equal to checkin_date returns CHECKOUT_BEFORE_CHECKIN`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Lucas")
                        put("last_name", "Alves")
                    },
                )
                put("checkin_date", "2026-12-10")
                put("checkout_date", "2026-12-10")
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        val err = errors.firstOrNull { it.field == "checkout_date" }
        assertEquals("CHECKOUT_BEFORE_CHECKIN", err?.errorCode)
    }

    @Test
    fun `room_count less than or equal to 0 returns INVALID_SCHEMA`() {
        val payload =
            buildJsonObject {
                put(
                    "customer",
                    buildJsonObject {
                        put("first_name", "Lucas")
                        put("last_name", "Alves")
                    },
                )
                put("checkin_date", "2026-12-10")
                put("checkout_date", "2026-12-12")
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
                put("rooms", 0)
            }

        val result = adapter.convert(payload)

        assertTrue(result is ValidationResult.Failure)
        val errors = (result as ValidationResult.Failure).errors
        assertFalse(errors.isEmpty())
        assertTrue(errors.any { it.field == "customer" && it.errorCode == "FIELD_REQUIRED" })
        assertTrue(errors.any { it.field == "checkin_date" && it.errorCode == "FIELD_REQUIRED" })
        assertTrue(errors.any { it.field == "checkout_date" && it.errorCode == "FIELD_REQUIRED" })
        assertTrue(errors.any { it.field == "room_count" && it.errorCode == "INVALID_SCHEMA" })
    }
}
