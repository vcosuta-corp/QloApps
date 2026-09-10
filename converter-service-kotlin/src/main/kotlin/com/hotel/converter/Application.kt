package com.hotel.converter

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

private const val SERVER_PORT = 8106
private const val SERVER_HOST = "127.0.0.1"

private val jsonEncoder =
    Json {
        encodeDefaults = true
        prettyPrint = false
    }

data class ReservationDraft(
    val guestName: String,
    val checkIn: String,
    val checkOut: String,
    val nights: Int,
    val rooms: Int,
    val channelReference: String,
    val sourceProvider: String,
)

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = SERVER_HOST) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    routing {
        registerHealthRoute()
        registerConvertRoute()
    }
}

private fun Route.registerHealthRoute() {
    get("/healthz") {
        call.respondText("{\"status\":\"UP\"}", ContentType.Application.Json)
    }
}

private fun Route.registerConvertRoute() {
    post("/v1/external-reservation-requests/convert") {
        val rawHeader = call.request.headers["X-Correlation-ID"]
        val correlationResult = validateCorrelationId(rawHeader)
        val correlationId =
            when (correlationResult) {
                is ValidationResult.Failure -> {
                    val fallbackId = UUID.randomUUID().toString()
                    respondValidationErrors(
                        call = call,
                        errors = correlationResult.errors,
                        correlationId = fallbackId,
                    )
                    return@post
                }
                is ValidationResult.Success -> correlationResult.value
            }

        try {
            handleConversion(call, correlationId)
        } catch (e: SerializationException) {
            respondValidationErrors(
                call = call,
                errors = listOf(ValidationError("payload", "INVALID_SCHEMA", "JSON malformatado: ${e.message}")),
                correlationId = correlationId,
            )
        } catch (e: IllegalArgumentException) {
            respondValidationErrors(
                call = call,
                errors = listOf(ValidationError("payload", "INVALID_SCHEMA", e.message ?: "Argumento invalido")),
                correlationId = correlationId,
            )
        }
    }
}

private suspend fun handleConversion(
    call: ApplicationCall,
    correlationId: String,
) {
    val rawText = call.receiveText()
    val rootObj = Json.parseToJsonElement(rawText).jsonObject
    val provider = rootObj["provider"]?.jsonPrimitive?.content.orEmpty()
    val payload = rootObj["payload"]?.jsonObject

    if (payload == null) {
        respondValidationErrors(
            call = call,
            errors = listOf(ValidationError("payload", "INVALID_SCHEMA", "payload obrigatorio")),
            correlationId = correlationId,
        )
        return
    }

    val parseResult =
        when (provider) {
            "PROVIDER_A" -> parseProviderA(payload)
            "PROVIDER_B" -> parseProviderB(payload)
            else -> {
                respondUnsupportedProvider(call, provider, correlationId)
                return
            }
        }

    when (parseResult) {
        is ValidationResult.Failure -> respondValidationErrors(call, parseResult.errors, correlationId)
        is ValidationResult.Success -> {
            call.response.header("X-Correlation-ID", correlationId)
            val responseJson = buildSuccessResponse(correlationId, parseResult.value)
            call.respondText(responseJson, ContentType.Application.Json, HttpStatusCode.OK)
        }
    }
}

private suspend fun respondUnsupportedProvider(
    call: ApplicationCall,
    provider: String,
    correlationId: String,
) {
    call.response.header("X-Correlation-ID", correlationId)
    val response =
        ConversionApiResponse(
            correlationId = correlationId,
            status = "FAILED",
            draft = null,
            errors =
                listOf(
                    ValidationErrorPayload(
                        field = "provider",
                        errorCode = "UNSUPPORTED_PROVIDER",
                        message = "Provedor nao suportado: $provider",
                    ),
                ),
        )
    call.respondText(jsonEncoder.encodeToString(response), ContentType.Application.Json, HttpStatusCode.BadRequest)
}

private suspend fun respondValidationErrors(
    call: ApplicationCall,
    errors: List<ValidationError>,
    correlationId: String,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
) {
    call.response.header("X-Correlation-ID", correlationId)
    val response =
        ConversionApiResponse(
            correlationId = correlationId,
            status = "FAILED",
            draft = null,
            errors = errors.map { ValidationErrorPayload(it.field, it.errorCode, it.message) },
        )
    call.respondText(jsonEncoder.encodeToString(response), ContentType.Application.Json, status)
}

private fun buildSuccessResponse(
    correlationId: String,
    draft: ReservationDraft,
): String {
    val response =
        ConversionApiResponse(
            correlationId = correlationId,
            status = "SUCCESS",
            draft =
                CanonicalDraftPayload(
                    guestName = draft.guestName,
                    checkIn = draft.checkIn,
                    checkOut = draft.checkOut,
                    nights = draft.nights,
                    roomsRequested = draft.rooms,
                    channelReference = draft.channelReference,
                    sourceProvider = draft.sourceProvider,
                ),
            errors = emptyList(),
        )
    return jsonEncoder.encodeToString(response)
}
