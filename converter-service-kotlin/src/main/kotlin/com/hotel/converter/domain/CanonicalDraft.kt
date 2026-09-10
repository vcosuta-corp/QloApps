package com.hotel.converter.domain

/**
 * Pure domain model representing the canonical reservation draft.
 * Agnostic of any external provider or transport format.
 */
data class CanonicalDraft(
    val guestName: String,
    val checkIn: String,
    val checkOut: String,
    val nights: Int,
    val rooms: Int,
    val channelReference: String,
    val sourceProvider: String,
)

typealias ReservationDraft = CanonicalDraft
