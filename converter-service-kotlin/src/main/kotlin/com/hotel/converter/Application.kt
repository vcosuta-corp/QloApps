package com.hotel.converter

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun main() {
    embeddedServer(Netty, port = 8106, host = "127.0.0.1") {
        routing {
            get("/healthz") {
                call.respondText("{\"status\":\"UP\"}", ContentType.Application.Json)
            }
            post("/v1/external-reservation-requests/convert") {
                try {
                    val rawText = call.receiveText()
                    val rootObj = Json.parseToJsonElement(rawText).jsonObject
                    val provider = rootObj["provider"]?.jsonPrimitive?.content ?: ""
                    val payload = rootObj["payload"]?.jsonObject ?: throw IllegalArgumentException("payload obrigatorio")

                    var guestName = ""
                    var checkInStr = ""
                    var checkOutStr = ""
                    var nights = 1
                    var rooms = 1
                    var channelRef = ""

                    if (provider == "PROVIDER_A") {
                        guestName = payload["guest_full_name"]?.jsonPrimitive?.content ?: "Hospede Nao Informado"
                        checkInStr = payload["arrival"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("arrival obrigatorio")
                        nights = payload["nights"]?.jsonPrimitive?.int ?: 1
                        rooms = payload["room_count"]?.jsonPrimitive?.int ?: 1
                        channelRef = payload["channel_reference"]?.jsonPrimitive?.content ?: "N/A"
                        
                        val inDate = LocalDate.parse(checkInStr)
                        checkOutStr = inDate.plusDays(nights.toLong()).toString()
                    } else if (provider == "PROVIDER_B") {
                        val customerObj = payload["customer"]?.jsonObject
                        val firstName = customerObj?.get("first_name")?.jsonPrimitive?.content ?: ""
                        val lastName = customerObj?.get("last_name")?.jsonPrimitive?.content ?: ""
                        guestName = "$firstName $lastName".trim()
                        
                        checkInStr = payload["checkin_date"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("checkin_date obrigatorio")
                        checkOutStr = payload["checkout_date"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("checkout_date obrigatorio")
                        channelRef = payload["reference_id"]?.jsonPrimitive?.content ?: "N/A"
                        
                        val inDate = LocalDate.parse(checkInStr)
                        val outDate = LocalDate.parse(checkOutStr)
                        nights = ChronoUnit.DAYS.between(inDate, outDate).toInt()
                    } else {
                        call.respondText("{\"status\":\"FAILED\",\"errors\":[{\"field\":\"provider\",\"error_code\":\"UNSUPPORTED_PROVIDER\",\"message\":\"Provedor nao suportado: $provider\"}]}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }

                    val responseJson = """
                    {
                      "correlation_id": "${call.request.headers["X-Correlation-ID"] ?: "corr-demo"}",
                      "status": "SUCCESS",
                      "draft": {
                        "guest_name": "$guestName",
                        "check_in": "$checkInStr",
                        "check_out": "$checkOutStr",
                        "nights": $nights,
                        "rooms_requested": $rooms,
                        "channel_reference": "$channelRef",
                        "source_provider": "$provider"
                      },
                      "errors": []
                    }
                    """.trimIndent()
                    call.respondText(responseJson, ContentType.Application.Json, HttpStatusCode.OK)
                } catch (e: Exception) {
                    call.respondText("{\"status\":\"FAILED\",\"errors\":[{\"field\":\"payload\",\"error_code\":\"INVALID_SCHEMA\",\"message\":\"${e.message}\"}]}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }
        }
    }.start(wait = true)
}
