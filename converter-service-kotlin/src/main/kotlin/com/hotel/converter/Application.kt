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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val SERVER_PORT = 8106
private const val SERVER_HOST = "127.0.0.1"

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
        try {
            handleConversion(call)
        } catch (e: SerializationException) {
            respondValidationErrors(
                call,
                listOf(ValidationError("payload", "INVALID_SCHEMA", "JSON malformatado: ${e.message}")),
            )
        } catch (e: IllegalArgumentException) {
            respondValidationErrors(
                call,
                listOf(ValidationError("payload", "INVALID_SCHEMA", e.message ?: "Argumento invalido")),
            )
        }
    }
}

private suspend fun handleConversion(call: ApplicationCall) {
    val rawText = call.receiveText()
    val rootObj = Json.parseToJsonElement(rawText).jsonObject
    val provider = rootObj["provider"]?.jsonPrimitive?.content.orEmpty()
    val payload = rootObj["payload"]?.jsonObject

    if (payload == null) {
        respondValidationErrors(
            call,
            listOf(ValidationError("payload", "INVALID_SCHEMA", "payload obrigatorio")),
        )
        return
    }

    val parseResult =
        when (provider) {
            "PROVIDER_A" -> parseProviderA(payload)
            "PROVIDER_B" -> parseProviderB(payload)
            else -> {
                respondUnsupportedProvider(call, provider)
                return
            }
        }

    when (parseResult) {
        is ValidationResult.Failure -> respondValidationErrors(call, parseResult.errors)
        is ValidationResult.Success -> {
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "corr-demo"
            call.response.header("X-Correlation-ID", correlationId)
            val responseJson = buildSuccessResponse(correlationId, parseResult.value)
            call.respondText(responseJson, ContentType.Application.Json, HttpStatusCode.OK)
        }
    }
}

private suspend fun respondUnsupportedProvider(
    call: ApplicationCall,
    provider: String,
) {
    val correlationId = call.request.headers["X-Correlation-ID"] ?: "corr-demo"
    call.response.header("X-Correlation-ID", correlationId)
    val json =
        """
        {
          "correlation_id": "$correlationId",
          "status": "FAILED",
          "draft": null,
          "errors": [
            {
              "field": "provider",
              "error_code": "UNSUPPORTED_PROVIDER",
              "message": "Provedor nao suportado: $provider"
            }
          ]
        }
        """.trimIndent()
    call.respondText(json, ContentType.Application.Json, HttpStatusCode.BadRequest)
}

private suspend fun respondValidationErrors(
    call: ApplicationCall,
    errors: List<ValidationError>,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
) {
    val correlationId = call.request.headers["X-Correlation-ID"] ?: "corr-demo"
    call.response.header("X-Correlation-ID", correlationId)
    val errorsJson =
        errors.joinToString(",") { err ->
            """
            {
              "field": "${err.field}",
              "error_code": "${err.errorCode}",
              "message": "${err.message.replace("\"", "\\\"")}"
            }
            """.trimIndent()
        }
    val json =
        """
        {
          "correlation_id": "$correlationId",
          "status": "FAILED",
          "draft": null,
          "errors": [
            $errorsJson
          ]
        }
        """.trimIndent()
    call.respondText(json, ContentType.Application.Json, status)
}

private fun buildSuccessResponse(
    correlationId: String,
    draft: ReservationDraft,
): String {
    return """
        {
          "correlation_id": "$correlationId",
          "status": "SUCCESS",
          "draft": {
            "guest_name": "${draft.guestName}",
            "check_in": "${draft.checkIn}",
            "check_out": "${draft.checkOut}",
            "nights": ${draft.nights},
            "rooms_requested": ${draft.rooms},
            "channel_reference": "${draft.channelReference}",
            "source_provider": "${draft.sourceProvider}"
          },
          "errors": []
        }
        """.trimIndent()
}
