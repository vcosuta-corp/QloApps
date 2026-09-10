package com.hotel.converter.adapter

import com.hotel.converter.domain.CanonicalDraft
import com.hotel.converter.domain.ValidationResult
import kotlinx.serialization.json.JsonObject

/**
 * Port interface for channel reservation request adapters.
 * Each adapter is responsible for parsing and normalizing payloads from a specific external provider.
 */
interface ChannelAdapter {
    val providerName: String

    fun convert(payload: JsonObject): ValidationResult<CanonicalDraft>
}
