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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

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
        } catch (e: IllegalArgumentException) {
            respondInvalidSchema(call, e.message)
        } catch (e: SerializationException) {
            respondInvalidSchema(call, e.message)
        } catch (e: IllegalStateException) {
            respondInvalidSchema(call, e.message)
        } catch (e: DateTimeParseException) {
            respondInvalidSchema(call, e.message)
        }
    }
}

private suspend fun handleConversion(call: ApplicationCall) {
    val rawText = call.receiveText()
    val rootObj = Json.parseToJsonElement(rawText).jsonObject
    val provider = rootObj["provider"]?.jsonPrimitive?.content.orEmpty()
    val payload =
        rootObj["payload"]?.jsonObject
            ?: throw IllegalArgumentException("payload obrigatorio")

    val draft =
        when (provider) {
            "PROVIDER_A" -> parseProviderA(payload)
            "PROVIDER_B" -> parseProviderB(payload)
            else -> {
                respondUnsupportedProvider(call, provider)
                return
            }
        }

    val correlationId = call.request.headers["X-Correlation-ID"] ?: "corr-demo"
    call.response.header("X-Correlation-ID", correlationId)
    val responseJson = buildSuccessResponse(correlationId, draft)
    call.respondText(responseJson, ContentType.Application.Json, HttpStatusCode.OK)
}

private fun parseProviderA(payload: JsonObject): ReservationDraft {
    val guestName = payload["guest_full_name"]?.jsonPrimitive?.content ?: "Hospede Nao Informado"
    val checkInStr =
        payload["arrival"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("arrival obrigatorio")
    val nights = payload["nights"]?.jsonPrimitive?.int ?: 1
    val rooms = payload["room_count"]?.jsonPrimitive?.int ?: 1
    val channelRef = payload["channel_reference"]?.jsonPrimitive?.content ?: "N/A"

    val inDate = LocalDate.parse(checkInStr)
    val checkOutStr = inDate.plusDays(nights.toLong()).toString()

    return ReservationDraft(
        guestName = guestName,
        checkIn = checkInStr,
        checkOut = checkOutStr,
        nights = nights,
        rooms = rooms,
        channelReference = channelRef,
        sourceProvider = "PROVIDER_A",
    )
}

private fun parseProviderB(payload: JsonObject): ReservationDraft {
    val customerObj = payload["customer"]?.jsonObject
    val firstName = customerObj?.get("first_name")?.jsonPrimitive?.content.orEmpty()
    val lastName = customerObj?.get("last_name")?.jsonPrimitive?.content.orEmpty()
    val guestName = "$firstName $lastName".trim()

    val checkInStr =
        payload["checkin_date"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("checkin_date obrigatorio")
    val checkOutStr =
        payload["checkout_date"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("checkout_date obrigatorio")
    val channelRef = payload["reference_id"]?.jsonPrimitive?.content ?: "N/A"

    val inDate = LocalDate.parse(checkInStr)
    val outDate = LocalDate.parse(checkOutStr)
    val nights = ChronoUnit.DAYS.between(inDate, outDate).toInt()

    return ReservationDraft(
        guestName = guestName,
        checkIn = checkInStr,
        checkOut = checkOutStr,
        nights = nights,
        rooms = 1,
        channelReference = channelRef,
        sourceProvider = "PROVIDER_B",
    )
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
          "status": "FAILED",
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

private suspend fun respondInvalidSchema(
    call: ApplicationCall,
    message: String?,
) {
    val correlationId = call.request.headers["X-Correlation-ID"] ?: "corr-demo"
    call.response.header("X-Correlation-ID", correlationId)
    val json =
        """
        {
          "status": "FAILED",
          "errors": [
            {
              "field": "payload",
              "error_code": "INVALID_SCHEMA",
              "message": "$message"
            }
          ]
        }
        """.trimIndent()
    call.respondText(json, ContentType.Application.Json, HttpStatusCode.BadRequest)
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
