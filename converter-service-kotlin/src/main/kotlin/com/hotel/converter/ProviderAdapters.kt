package com.hotel.converter

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

fun normalizeGuestName(rawName: String): String {
    val withoutControls = rawName.replace(Regex("\\p{Cntrl}"), " ")
    return withoutControls.replace(Regex("\\s+"), " ").trim()
}

private fun getStringField(
    payload: JsonObject,
    fieldName: String,
): String? {
    val element = payload[fieldName]
    return if (element is JsonPrimitive && element.isString) element.content else null
}

private fun validateArrival(
    checkInStr: String?,
    errors: MutableList<ValidationError>,
): LocalDate? {
    if (checkInStr == null) {
        errors.add(
            ValidationError(
                "arrival",
                "FIELD_REQUIRED",
                "Campo obrigatorio 'arrival' nao encontrado no payload do PROVIDER_A.",
            ),
        )
        return null
    }
    return try {
        LocalDate.parse(checkInStr)
    } catch (e: DateTimeParseException) {
        errors.add(
            ValidationError(
                "payload",
                "INVALID_SCHEMA",
                "Formato de data invalido para 'arrival': ${e.message}",
            ),
        )
        null
    }
}

private fun validateNights(
    hasNights: Boolean,
    nightsRaw: Int?,
    errors: MutableList<ValidationError>,
): Int {
    var result = 1
    if (!hasNights) {
        errors.add(
            ValidationError(
                "nights",
                "FIELD_REQUIRED",
                "Campo obrigatorio 'nights' nao encontrado no payload do PROVIDER_A.",
            ),
        )
    } else if (nightsRaw == null) {
        errors.add(
            ValidationError(
                "nights",
                "INVALID_SCHEMA",
                "Campo 'nights' deve ser um numero inteiro.",
            ),
        )
    } else if (nightsRaw <= 0) {
        errors.add(
            ValidationError(
                "nights",
                "CHECKOUT_BEFORE_CHECKIN",
                "A quantidade de noites deve ser maior ou igual a 1.",
            ),
        )
    } else {
        result = nightsRaw
    }
    return result
}

private fun validateRooms(
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

fun parseProviderA(payload: JsonObject): ValidationResult<ReservationDraft> {
    val rawGuestName = getStringField(payload, "guest_full_name")
    val guestName = rawGuestName?.let { normalizeGuestName(it) }.orEmpty()
    val checkInStr = getStringField(payload, "arrival")
    val nightsRaw = payload["nights"]?.jsonPrimitive?.content?.toIntOrNull()
    val roomsRaw = payload["room_count"]?.jsonPrimitive?.content?.toIntOrNull()

    val errors = mutableListOf<ValidationError>()
    if (rawGuestName == null) {
        errors.add(
            ValidationError(
                "guest_full_name",
                "FIELD_REQUIRED",
                "Campo obrigatorio 'guest_full_name' nao encontrado no payload do PROVIDER_A.",
            ),
        )
    } else if (guestName.isEmpty()) {
        errors.add(
            ValidationError(
                "guest_full_name",
                "INVALID_SCHEMA",
                "Nome do hospede nao pode ser vazio.",
            ),
        )
    }

    val inDate = validateArrival(checkInStr, errors)
    val nights = validateNights(payload["nights"] != null, nightsRaw, errors)
    val rooms = validateRooms(payload["room_count"] != null, roomsRaw, errors)

    return if (errors.isNotEmpty() || inDate == null) {
        ValidationResult.Failure(errors)
    } else {
        val checkOutStr = inDate.plusDays(nights.toLong()).toString()
        val channelRef = payload["channel_reference"]?.jsonPrimitive?.content ?: "N/A"
        ValidationResult.Success(
            ReservationDraft(
                guestName = guestName,
                checkIn = checkInStr ?: "",
                checkOut = checkOutStr,
                nights = nights,
                rooms = rooms,
                channelReference = channelRef,
                sourceProvider = "PROVIDER_A",
            ),
        )
    }
}

private fun validateCustomer(
    payload: JsonObject,
    errors: MutableList<ValidationError>,
): String {
    val customerElement = payload["customer"]
    if (customerElement !is JsonObject) {
        val errCode = if (customerElement == null) "FIELD_REQUIRED" else "INVALID_SCHEMA"
        errors.add(
            ValidationError(
                "customer",
                errCode,
                "Objeto obrigatorio 'customer' nao encontrado no payload do PROVIDER_B.",
            ),
        )
        return ""
    }

    val firstName = getStringField(customerElement, "first_name")
    val lastName = getStringField(customerElement, "last_name")
    if (firstName.isNullOrBlank()) {
        errors.add(
            ValidationError(
                "customer.first_name",
                "FIELD_REQUIRED",
                "Campo obrigatorio 'first_name' nao encontrado ou vazio.",
            ),
        )
    }
    if (lastName.isNullOrBlank()) {
        errors.add(
            ValidationError(
                "customer.last_name",
                "FIELD_REQUIRED",
                "Campo obrigatorio 'last_name' nao encontrado ou vazio.",
            ),
        )
    }
    return normalizeGuestName("${firstName.orEmpty()} ${lastName.orEmpty()}")
}

private data class DateInterval(
    val checkInStr: String,
    val checkOutStr: String,
    val nights: Int,
)

private fun parseDate(
    field: String,
    value: String,
    errors: MutableList<ValidationError>,
): LocalDate? {
    return try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        errors.add(
            ValidationError(
                "payload",
                "INVALID_SCHEMA",
                "Formato de data invalido para '$field': ${e.message}",
            ),
        )
        null
    }
}

private fun validateDatesProviderB(
    payload: JsonObject,
    errors: MutableList<ValidationError>,
): DateInterval? {
    val checkInStr = getStringField(payload, "checkin_date")
    val checkOutStr = getStringField(payload, "checkout_date")
    if (checkInStr == null) {
        errors.add(
            ValidationError(
                "checkin_date",
                "FIELD_REQUIRED",
                "Campo obrigatorio 'checkin_date' nao encontrado no payload do PROVIDER_B.",
            ),
        )
    }
    if (checkOutStr == null) {
        errors.add(
            ValidationError(
                "checkout_date",
                "FIELD_REQUIRED",
                "Campo obrigatorio 'checkout_date' nao encontrado no payload do PROVIDER_B.",
            ),
        )
    }

    var interval: DateInterval? = null
    if (checkInStr != null && checkOutStr != null) {
        val inDate = parseDate("checkin_date", checkInStr, errors)
        val outDate = parseDate("checkout_date", checkOutStr, errors)
        if (inDate != null && outDate != null) {
            if (!outDate.isAfter(inDate)) {
                errors.add(
                    ValidationError(
                        "checkout_date",
                        "CHECKOUT_BEFORE_CHECKIN",
                        "Data de check-out ($checkOutStr) deve ser posterior a data de check-in ($checkInStr).",
                    ),
                )
            } else {
                val nights = ChronoUnit.DAYS.between(inDate, outDate).toInt()
                interval = DateInterval(checkInStr, checkOutStr, nights)
            }
        }
    }
    return interval
}

fun parseProviderB(payload: JsonObject): ValidationResult<ReservationDraft> {
    val errors = mutableListOf<ValidationError>()
    val guestName = validateCustomer(payload, errors)
    val dates = validateDatesProviderB(payload, errors)

    val roomCountRaw =
        (payload["room_count"] ?: payload["rooms"])?.jsonPrimitive?.content?.toIntOrNull()
    val hasRoomCount = payload["room_count"] != null || payload["rooms"] != null
    val rooms = validateRooms(hasRoomCount, roomCountRaw, errors)
    val channelRef = payload["reference_id"]?.jsonPrimitive?.content ?: "N/A"

    return if (errors.isNotEmpty() || dates == null) {
        ValidationResult.Failure(errors)
    } else {
        ValidationResult.Success(
            ReservationDraft(
                guestName = guestName,
                checkIn = dates.checkInStr,
                checkOut = dates.checkOutStr,
                nights = dates.nights,
                rooms = rooms,
                channelReference = channelRef,
                sourceProvider = "PROVIDER_B",
            ),
        )
    }
}
