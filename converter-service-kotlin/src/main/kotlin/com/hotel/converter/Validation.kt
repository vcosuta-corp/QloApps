package com.hotel.converter

private val UUID_V4_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

data class ValidationError(
    val field: String,
    val errorCode: String,
    val message: String,
)

sealed class ValidationResult<out T> {
    data class Success<T>(val value: T) : ValidationResult<T>()

    data class Failure(val errors: List<ValidationError>) : ValidationResult<Nothing>() {
        constructor(field: String, errorCode: String, message: String) : this(
            listOf(ValidationError(field, errorCode, message)),
        )
    }
}

fun validateCorrelationId(headerValue: String?): ValidationResult<String> {
    val trimmed = headerValue?.trim()
    val error =
        when {
            headerValue == null ->
                ValidationError(
                    "X-Correlation-ID",
                    "HEADER_REQUIRED",
                    "Header obrigatorio 'X-Correlation-ID' ausente.",
                )
            trimmed.isNullOrEmpty() ->
                ValidationError(
                    "X-Correlation-ID",
                    "INVALID_HEADER",
                    "Header 'X-Correlation-ID' nao pode ser vazio.",
                )
            !UUID_V4_REGEX.matches(trimmed) ->
                ValidationError(
                    "X-Correlation-ID",
                    "INVALID_HEADER",
                    "Header 'X-Correlation-ID' deve ser um UUID v4 valido.",
                )
            else -> null
        }
    return if (error != null) {
        ValidationResult.Failure(listOf(error))
    } else {
        ValidationResult.Success(trimmed ?: "")
    }
}
