package com.hotel.converter

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
