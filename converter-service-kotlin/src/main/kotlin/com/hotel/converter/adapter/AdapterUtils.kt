package com.hotel.converter.adapter

import com.hotel.converter.domain.ValidationError
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DEFAULT_ROOMS_REQUESTED = 1
private val DEFAULT_ROOM_KEYS = listOf("room_count", "rooms")

/**
 * Extracts a string field from a JSON object if present and represented as a string primitive.
 */
internal fun getStringField(
    payload: JsonObject,
    fieldName: String,
): String? {
    val element = payload[fieldName]
    return if (element is JsonPrimitive && element.isString) element.content else null
}

/**
 * Resolves and validates the number of requested rooms according to business rule RN-005 (RFC-006).
 *
 * Behavior:
 * - If the room count field is missing or null, safely defaults to 1 without error.
 * - Supports canonical key "room_count" and alternative key "rooms".
 * - If present, must be a valid integer greater than or equal to 1.
 * - If present but non-integer, <= 0, or malformed, appends an INVALID_SCHEMA error.
 */
internal fun resolveRoomCount(
    payload: JsonObject,
    errors: MutableList<ValidationError>,
    candidateKeys: List<String> = DEFAULT_ROOM_KEYS,
): Int {
    val matchedKey =
        candidateKeys.firstOrNull { payload.containsKey(it) && payload[it] !is JsonNull }
            ?: return DEFAULT_ROOMS_REQUESTED

    val element = payload[matchedKey]
    val intValue =
        when {
            element is JsonPrimitive && !element.isString -> element.content.toIntOrNull()
            element is JsonPrimitive && element.isString -> element.content.trim().toIntOrNull()
            else -> null
        }

    return if (intValue == null || intValue <= 0) {
        errors.add(
            ValidationError(
                field = "room_count",
                errorCode = "INVALID_SCHEMA",
                message = "Campo 'room_count' deve ser um número inteiro maior ou igual a 1.",
            ),
        )
        DEFAULT_ROOMS_REQUESTED
    } else {
        intValue
    }
}
