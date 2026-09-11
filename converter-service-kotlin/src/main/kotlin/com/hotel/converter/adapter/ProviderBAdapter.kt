package com.hotel.converter.adapter

import com.hotel.converter.domain.CanonicalDraft
import com.hotel.converter.domain.ValidationError
import com.hotel.converter.domain.ValidationResult
import com.hotel.converter.domain.normalizeGuestName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Adapter for external reservation requests from PROVIDER_B.
 *
 * Implements business rules defined in RFC-006:
 * - RN-002: Maps checkin_date and checkout_date into check_in, check_out,
 *   and calculates nights (ChronoUnit.DAYS.between).
 * - RN-003: Strictly validates that check_out > check_in, rejecting with CHECKOUT_BEFORE_CHECKIN otherwise.
 * - RN-004: Unifies nested customer { first_name, last_name } into canonical guest_name with sanitization.
 * - RN-005: Defaults room count to 1 if omitted, supporting room_count and rooms keys.
 */
class ProviderBAdapter : ChannelAdapter {
    override val providerName: String = "PROVIDER_B"

    override fun convert(payload: JsonObject): ValidationResult<CanonicalDraft> {
        val errors = mutableListOf<ValidationError>()
        val guestName = validateCustomer(payload, errors)
        val dates = validateDates(payload, errors)

        val rooms = resolveRoomCount(payload, errors)
        val channelRef = payload["reference_id"]?.jsonPrimitive?.content ?: "N/A"

        return if (errors.isNotEmpty() || dates == null) {
            ValidationResult.Failure(errors)
        } else {
            ValidationResult.Success(
                CanonicalDraft(
                    guestName = guestName,
                    checkIn = dates.checkInStr,
                    checkOut = dates.checkOutStr,
                    nights = dates.nights,
                    rooms = rooms,
                    channelReference = channelRef,
                    sourceProvider = providerName,
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
                    "Objeto obrigatório 'customer' não encontrado no payload do PROVIDER_B.",
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
                    "Campo obrigatório 'first_name' não encontrado ou vazio.",
                ),
            )
        }
        if (lastName.isNullOrBlank()) {
            errors.add(
                ValidationError(
                    "customer.last_name",
                    "FIELD_REQUIRED",
                    "Campo obrigatório 'last_name' não encontrado ou vazio.",
                ),
            )
        }
        return normalizeGuestName("${firstName.orEmpty()} ${lastName.orEmpty()}")
    }

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
                    field,
                    "INVALID_SCHEMA",
                    "Formato de data inválido para '$field': ${e.message}",
                ),
            )
            null
        }
    }

    private fun validateDates(
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
                    "Campo obrigatório 'checkin_date' não encontrado no payload do PROVIDER_B.",
                ),
            )
        }
        if (checkOutStr == null) {
            errors.add(
                ValidationError(
                    "checkout_date",
                    "FIELD_REQUIRED",
                    "Campo obrigatório 'checkout_date' não encontrado no payload do PROVIDER_B.",
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
                            "Data de check-out ($checkOutStr) deve ser posterior à data de check-in ($checkInStr).",
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

    private data class DateInterval(
        val checkInStr: String,
        val checkOutStr: String,
        val nights: Int,
    )
}
