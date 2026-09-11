package com.hotel.converter

import com.hotel.converter.domain.ValidationError
import com.hotel.converter.domain.ValidationResult

private val UUID_V4_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

fun validateCorrelationId(headerValue: String?): ValidationResult<String> {
    val trimmed = headerValue?.trim()
    val error =
        when {
            headerValue == null ->
                ValidationError(
                    "X-Correlation-ID",
                    "HEADER_REQUIRED",
                    "Header obrigatório 'X-Correlation-ID' ausente.",
                )
            trimmed.isNullOrEmpty() ->
                ValidationError(
                    "X-Correlation-ID",
                    "INVALID_HEADER",
                    "Header 'X-Correlation-ID' não pode ser vazio.",
                )
            !UUID_V4_REGEX.matches(trimmed) ->
                ValidationError(
                    "X-Correlation-ID",
                    "INVALID_HEADER",
                    "Header 'X-Correlation-ID' deve ser um UUID v4 válido.",
                )
            else -> null
        }
    return if (error != null) {
        ValidationResult.Failure(listOf(error))
    } else {
        ValidationResult.Success(trimmed ?: "")
    }
}

fun validateContentType(headerValue: String?): ValidationError? {
    val clean = headerValue?.split(";")?.firstOrNull()?.trim()?.lowercase()
    return when {
        headerValue == null ->
            ValidationError(
                "Content-Type",
                "HEADER_REQUIRED",
                "Header obrigatório 'Content-Type' ausente.",
            )
        clean != "application/json" ->
            ValidationError(
                "Content-Type",
                "UNSUPPORTED_MEDIA_TYPE",
                "Header 'Content-Type' deve ser 'application/json'.",
            )
        else -> null
    }
}
