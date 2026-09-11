package com.hotel.converter

import com.hotel.converter.domain.CanonicalDraft
import com.hotel.converter.domain.ValidationError
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanonicalModelTest {
    private val json =
        Json {
            encodeDefaults = true
            prettyPrint = false
        }

    @Test
    fun `CanonicalDraft holds valid reservation draft data`() {
        val draft =
            CanonicalDraft(
                guestName = "Carlos Drumond",
                checkIn = "2026-10-01",
                checkOut = "2026-10-05",
                nights = 4,
                rooms = 2,
                channelReference = "REF-DRUMOND-01",
                sourceProvider = "PROVIDER_A",
            )

        assertEquals("Carlos Drumond", draft.guestName)
        assertEquals("2026-10-01", draft.checkIn)
        assertEquals("2026-10-05", draft.checkOut)
        assertEquals(4, draft.nights)
        assertEquals(2, draft.rooms)
        assertEquals("REF-DRUMOND-01", draft.channelReference)
        assertEquals("PROVIDER_A", draft.sourceProvider)
    }

    @Test
    fun `CanonicalDraft toPayload maps all fields accurately`() {
        val draft =
            CanonicalDraft(
                guestName = "Clarice Lispector",
                checkIn = "2026-11-15",
                checkOut = "2026-11-20",
                nights = 5,
                rooms = 1,
                channelReference = "REF-CLARICE-02",
                sourceProvider = "PROVIDER_B",
            )

        val payload = draft.toPayload()
        assertEquals(draft.guestName, payload.guestName)
        assertEquals(draft.checkIn, payload.checkIn)
        assertEquals(draft.checkOut, payload.checkOut)
        assertEquals(draft.nights, payload.nights)
        assertEquals(draft.rooms, payload.roomsRequested)
        assertEquals(draft.channelReference, payload.channelReference)
        assertEquals(draft.sourceProvider, payload.sourceProvider)
    }

    @Test
    fun `ConversionResponse success factory creates correct structure and serializes with snake_case keys`() {
        val draft =
            CanonicalDraft(
                guestName = "Machado de Assis",
                checkIn = "2026-09-10",
                checkOut = "2026-09-13",
                nights = 3,
                rooms = 1,
                channelReference = "REF-MACHADO",
                sourceProvider = "PROVIDER_A",
            )
        val correlationId = "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e"

        val response = ConversionResponse.success(correlationId, draft)
        assertEquals("SUCCESS", response.status)
        assertEquals(correlationId, response.correlationId)
        assertNotNull(response.draft)
        assertTrue(response.errors.isEmpty())

        val encoded = json.encodeToString(response)
        val jsonElement = Json.parseToJsonElement(encoded).jsonObject

        assertEquals(correlationId, jsonElement["correlation_id"]?.jsonPrimitive?.content)
        assertEquals("SUCCESS", jsonElement["status"]?.jsonPrimitive?.content)

        val draftJson = jsonElement["draft"]?.jsonObject
        assertNotNull(draftJson)
        assertEquals("Machado de Assis", draftJson?.get("guest_name")?.jsonPrimitive?.content)
        assertEquals("2026-09-10", draftJson?.get("check_in")?.jsonPrimitive?.content)
        assertEquals("2026-09-13", draftJson?.get("check_out")?.jsonPrimitive?.content)
        assertEquals(3, draftJson?.get("nights")?.jsonPrimitive?.content?.toInt())
        assertEquals(1, draftJson?.get("rooms_requested")?.jsonPrimitive?.content?.toInt())
        assertEquals("REF-MACHADO", draftJson?.get("channel_reference")?.jsonPrimitive?.content)
        assertEquals("PROVIDER_A", draftJson?.get("source_provider")?.jsonPrimitive?.content)
    }

    @Test
    fun `ConversionResponse failure factory creates correct structure with error details`() {
        val correlationId = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        val errors =
            listOf(
                ValidationError("nights", "FIELD_REQUIRED", "Campo obrigatorio ausente."),
                ValidationError("checkout_date", "CHECKOUT_BEFORE_CHECKIN", "Data invalida."),
            )

        val response = ConversionResponse.failure(correlationId, errors)
        assertEquals("FAILED", response.status)
        assertEquals(correlationId, response.correlationId)
        assertNull(response.draft)
        assertEquals(2, response.errors.size)

        val encoded = json.encodeToString(response)
        val jsonElement = Json.parseToJsonElement(encoded).jsonObject

        assertEquals(correlationId, jsonElement["correlation_id"]?.jsonPrimitive?.content)
        assertEquals("FAILED", jsonElement["status"]?.jsonPrimitive?.content)
        assertTrue(jsonElement["draft"] == null || jsonElement["draft"]?.toString() == "null")
    }

    @Test
    fun `ConversionRequest serializes and deserializes provider and payload`() {
        val payloadObj =
            buildJsonObject {
                put("arrival", "2026-12-01")
                put("nights", 2)
            }
        val request = ConversionRequest(provider = "PROVIDER_A", payload = payloadObj)

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<ConversionRequest>(encoded)

        assertEquals("PROVIDER_A", decoded.provider)
        assertNotNull(decoded.payload)
        assertEquals("2026-12-01", decoded.payload?.get("arrival")?.jsonPrimitive?.content)
    }
}
