package com.hotel.converter.adapter

import com.hotel.converter.domain.ValidationError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun getStringField(
    payload: JsonObject,
    fieldName: String,
): String? {
    val element = payload[fieldName]
    return if (element is JsonPrimitive && element.isString) element.content else null
}

internal fun validateRooms(
    hasRooms: Boolean,
    roomsRaw: Int?,
    errors: MutableList<ValidationError>,
): Int {
    var result = 1
    if (hasRooms) {
        if (roomsRaw == null || roomsRaw <= 0) {
            errors.add(
                ValidationError(
                    "room_count",
                    "INVALID_SCHEMA",
                    "room_count deve ser maior ou igual a 1.",
                ),
            )
        } else {
            result = roomsRaw
        }
    }
    return result
}
