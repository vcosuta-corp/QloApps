package com.hotel.converter.adapter

import com.hotel.converter.domain.CanonicalDraft
import com.hotel.converter.domain.ValidationError
import com.hotel.converter.domain.ValidationResult
import com.hotel.converter.domain.normalizeGuestName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException

class ProviderAAdapter : ChannelAdapter {
    override val providerName: String = "PROVIDER_A"

    override fun convert(payload: JsonObject): ValidationResult<CanonicalDraft> {
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
                    "Campo obrigatório 'guest_full_name' não encontrado no payload do PROVIDER_A.",
                ),
            )
        } else if (guestName.isEmpty()) {
            errors.add(
                ValidationError(
                    "guest_full_name",
                    "INVALID_SCHEMA",
                    "Nome do hóspede não pode ser vazio.",
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
                CanonicalDraft(
                    guestName = guestName,
                    checkIn = checkInStr ?: "",
                    checkOut = checkOutStr,
                    nights = nights,
                    rooms = rooms,
                    channelReference = channelRef,
                    sourceProvider = providerName,
                ),
            )
        }
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
                    "Campo obrigatório 'arrival' não encontrado no payload do PROVIDER_A.",
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
                    "Formato de data inválido para 'arrival': ${e.message}",
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
                    "Campo obrigatório 'nights' não encontrado no payload do PROVIDER_A.",
                ),
            )
        } else if (nightsRaw == null) {
            errors.add(
                ValidationError(
                    "nights",
                    "INVALID_SCHEMA",
                    "Campo 'nights' deve ser um número inteiro.",
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
}
