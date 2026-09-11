package com.hotel.converter

import com.hotel.converter.domain.CanonicalDraft
import com.hotel.converter.domain.ValidationError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ConversionRequest(
    val provider: String,
    val payload: JsonObject? = null,
)

@Serializable
data class ConversionResponse(
    @SerialName("correlation_id") val correlationId: String,
    val status: String,
    val draft: CanonicalDraftPayload? = null,
    val errors: List<ValidationErrorPayload> = emptyList(),
) {
    companion object {
        fun success(
            correlationId: String,
            draft: CanonicalDraft,
        ): ConversionResponse =
            ConversionResponse(
                correlationId = correlationId,
                status = "SUCCESS",
                draft = draft.toPayload(),
                errors = emptyList(),
            )

        fun failure(
            correlationId: String,
            errors: List<ValidationError>,
        ): ConversionResponse =
            ConversionResponse(
                correlationId = correlationId,
                status = "FAILED",
                draft = null,
                errors = errors.map { it.toPayload() },
            )

        fun failure(
            correlationId: String,
            error: ValidationError,
        ): ConversionResponse = failure(correlationId, listOf(error))
    }
}

typealias ConversionApiResponse = ConversionResponse

@Serializable
data class CanonicalDraftPayload(
    @SerialName("guest_name") val guestName: String,
    @SerialName("check_in") val checkIn: String,
    @SerialName("check_out") val checkOut: String,
    val nights: Int,
    @SerialName("rooms_requested") val roomsRequested: Int,
    @SerialName("channel_reference") val channelReference: String,
    @SerialName("source_provider") val sourceProvider: String,
)

@Serializable
data class ValidationErrorPayload(
    val field: String,
    @SerialName("error_code") val errorCode: String,
    val message: String,
)

fun CanonicalDraft.toPayload(): CanonicalDraftPayload =
    CanonicalDraftPayload(
        guestName = guestName,
        checkIn = checkIn,
        checkOut = checkOut,
        nights = nights,
        roomsRequested = rooms,
        channelReference = channelReference,
        sourceProvider = sourceProvider,
    )

fun ValidationError.toPayload(): ValidationErrorPayload =
    ValidationErrorPayload(
        field = field,
        errorCode = errorCode,
        message = message,
    )
