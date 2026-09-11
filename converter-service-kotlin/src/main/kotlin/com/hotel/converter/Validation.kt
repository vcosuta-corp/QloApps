package com.hotel.converter

import com.hotel.converter.domain.ValidationError
import com.hotel.converter.domain.ValidationResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

fun validateRequestBody(rawText: String): ValidationResult<ConversionRequest> =
    when {
        rawText.isBlank() ->
            ValidationResult.Failure(
                "payload",
                "INVALID_SCHEMA",
                "O corpo da requisição não pode ser vazio.",
            )
        else ->
            try {
                validateParsedJson(Json.parseToJsonElement(rawText))
            } catch (e: SerializationException) {
                ValidationResult.Failure(
                    "payload",
                    "INVALID_SCHEMA",
                    "JSON malformatado: ${e.message}",
                )
            }
    }

private fun validateParsedJson(jsonElement: JsonElement): ValidationResult<ConversionRequest> {
    if (jsonElement !is JsonObject) {
        return ValidationResult.Failure(
            "payload",
            "INVALID_SCHEMA",
            "O corpo da requisição deve ser um objeto JSON.",
        )
    }

    val errors = mutableListOf<ValidationError>()
    val providerString = validateProviderField(jsonElement, errors)
    val payloadObject = validatePayloadField(jsonElement, errors)

    return if (errors.isNotEmpty() || payloadObject == null) {
        ValidationResult.Failure(errors)
    } else {
        ValidationResult.Success(ConversionRequest(providerString, payloadObject))
    }
}

private fun validateProviderField(
    jsonObj: JsonObject,
    errors: MutableList<ValidationError>,
): String {
    val providerElement = jsonObj["provider"]
    return when {
        providerElement == null || providerElement is JsonNull -> {
            errors.add(
                ValidationError(
                    "provider",
                    "FIELD_REQUIRED",
                    "Campo obrigatório 'provider' não encontrado.",
                ),
            )
            ""
        }
        providerElement !is JsonPrimitive || !providerElement.isString -> {
            errors.add(
                ValidationError(
                    "provider",
                    "INVALID_SCHEMA",
                    "Campo 'provider' deve ser uma string.",
                ),
            )
            ""
        }
        providerElement.content.trim().isEmpty() -> {
            errors.add(
                ValidationError(
                    "provider",
                    "INVALID_SCHEMA",
                    "Campo 'provider' não pode ser vazio.",
                ),
            )
            ""
        }
        else -> providerElement.content.trim()
    }
}

private fun validatePayloadField(
    jsonObj: JsonObject,
    errors: MutableList<ValidationError>,
): JsonObject? {
    val payloadElement = jsonObj["payload"]
    return when {
        payloadElement == null || payloadElement is JsonNull -> {
            errors.add(
                ValidationError(
                    "payload",
                    "FIELD_REQUIRED",
                    "Campo obrigatório 'payload' não encontrado.",
                ),
            )
            null
        }
        payloadElement !is JsonObject -> {
            errors.add(
                ValidationError(
                    "payload",
                    "INVALID_SCHEMA",
                    "Campo 'payload' deve ser um objeto JSON.",
                ),
            )
            null
        }
        else -> payloadElement
    }
}
