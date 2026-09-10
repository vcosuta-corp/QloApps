package com.hotel.converter

import com.hotel.converter.adapter.ProviderAAdapter
import com.hotel.converter.adapter.ProviderBAdapter
import com.hotel.converter.domain.CanonicalDraft
import com.hotel.converter.domain.ValidationResult
import kotlinx.serialization.json.JsonObject

fun parseProviderA(payload: JsonObject): ValidationResult<CanonicalDraft> = ProviderAAdapter().convert(payload)

fun parseProviderB(payload: JsonObject): ValidationResult<CanonicalDraft> = ProviderBAdapter().convert(payload)

fun normalizeGuestName(rawName: String): String = com.hotel.converter.domain.normalizeGuestName(rawName)
