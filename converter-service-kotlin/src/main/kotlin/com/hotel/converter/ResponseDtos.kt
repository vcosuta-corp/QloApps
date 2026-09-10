package com.hotel.converter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversionApiResponse(
    @SerialName("correlation_id") val correlationId: String,
    val status: String,
    val draft: CanonicalDraftPayload? = null,
    val errors: List<ValidationErrorPayload> = emptyList(),
)

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
