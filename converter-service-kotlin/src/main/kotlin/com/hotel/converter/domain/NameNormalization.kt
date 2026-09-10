package com.hotel.converter.domain

/**
 * Normalizes guest name according to business rule RN-004:
 * Strips control characters and collapses duplicate whitespace.
 */
fun normalizeGuestName(rawName: String): String {
    val withoutControls = rawName.replace(Regex("\\p{Cntrl}"), " ")
    return withoutControls.replace(Regex("\\s+"), " ").trim()
}
