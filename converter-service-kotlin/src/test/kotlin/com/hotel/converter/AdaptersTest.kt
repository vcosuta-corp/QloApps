package com.hotel.converter

import com.hotel.converter.adapter.AdapterRegistry
import com.hotel.converter.adapter.ProviderAAdapter
import com.hotel.converter.adapter.ProviderBAdapter
import com.hotel.converter.domain.ValidationResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdaptersTest {
    @Test
    fun `adapter registry resolves registered adapters and rejects unknown`() {
        assertNotNull(AdapterRegistry.getAdapter("PROVIDER_A"))
        assertNotNull(AdapterRegistry.getAdapter("PROVIDER_B"))
        assertNull(AdapterRegistry.getAdapter("UNKNOWN_PROVIDER"))
    }

    @Test
    fun `ProviderAAdapter converts valid payload to CanonicalDraft`() {
        val json =
            """
            {
              "guest_full_name": "Ana Souza",
              "arrival": "2026-11-10",
              "nights": 3,
              "room_count": 2,
              "channel_reference": "REF-A-01"
            }
            """.trimIndent()
        val payload = Json.parseToJsonElement(json).jsonObject
        val result = ProviderAAdapter().convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("Ana Souza", draft.guestName)
        assertEquals("2026-11-10", draft.checkIn)
        assertEquals("2026-11-13", draft.checkOut)
        assertEquals(3, draft.nights)
        assertEquals(2, draft.rooms)
        assertEquals("REF-A-01", draft.channelReference)
        assertEquals("PROVIDER_A", draft.sourceProvider)
    }

    @Test
    fun `ProviderBAdapter converts valid payload and computes nights`() {
        val json =
            """
            {
              "customer": {
                "first_name": "Pedro",
                "last_name": "Santos"
              },
              "checkin_date": "2026-12-01",
              "checkout_date": "2026-12-05",
              "reference_id": "REF-B-01"
            }
            """.trimIndent()
        val payload = Json.parseToJsonElement(json).jsonObject
        val result = ProviderBAdapter().convert(payload)

        assertTrue(result is ValidationResult.Success)
        val draft = (result as ValidationResult.Success).value
        assertEquals("Pedro Santos", draft.guestName)
        assertEquals("2026-12-01", draft.checkIn)
        assertEquals("2026-12-05", draft.checkOut)
        assertEquals(4, draft.nights)
        assertEquals(1, draft.rooms)
        assertEquals("REF-B-01", draft.channelReference)
        assertEquals("PROVIDER_B", draft.sourceProvider)
    }
}
