package com.hotel.converter

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConvertRouteApiTest {
    @Test
    fun `1 - GET healthz returns 200 and status UP`() =
        testApplication {
            application {
                module()
            }

            val response = client.get("/healthz")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("UP", json["status"]?.jsonPrimitive?.content)
        }

    @Test
    fun `2 - valid conversion for PROVIDER_A returns 200 and canonical draft`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Maria Oliveira",
                    "arrival": "2026-09-10",
                    "nights": 3,
                    "room_count": 2,
                    "channel_reference": "BOOK-994"
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject

            assertEquals("SUCCESS", json["status"]?.jsonPrimitive?.content)
            val draft = json["draft"]?.jsonObject
            assertEquals("Maria Oliveira", draft?.get("guest_name")?.jsonPrimitive?.content)
            assertEquals("2026-09-10", draft?.get("check_in")?.jsonPrimitive?.content)
            assertEquals("2026-09-13", draft?.get("check_out")?.jsonPrimitive?.content)
            assertEquals("3", draft?.get("nights")?.jsonPrimitive?.content)
            assertEquals("2", draft?.get("rooms_requested")?.jsonPrimitive?.content)
            assertEquals("BOOK-994", draft?.get("channel_reference")?.jsonPrimitive?.content)
            assertEquals("PROVIDER_A", draft?.get("source_provider")?.jsonPrimitive?.content)
            assertTrue(json["errors"]?.jsonArray?.isEmpty() == true)
        }

    @Test
    fun `3 - valid conversion for PROVIDER_B returns 200 and canonical draft`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_B",
                  "payload": {
                    "customer": {
                      "first_name": "Carlos",
                      "last_name": "Santos"
                    },
                    "checkin_date": "2026-11-10",
                    "checkout_date": "2026-11-15",
                    "reference_id": "OTA-B-552"
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject

            assertEquals("SUCCESS", json["status"]?.jsonPrimitive?.content)
            val draft = json["draft"]?.jsonObject
            assertEquals("Carlos Santos", draft?.get("guest_name")?.jsonPrimitive?.content)
            assertEquals("2026-11-10", draft?.get("check_in")?.jsonPrimitive?.content)
            assertEquals("2026-11-15", draft?.get("check_out")?.jsonPrimitive?.content)
            assertEquals("5", draft?.get("nights")?.jsonPrimitive?.content)
            assertEquals("1", draft?.get("rooms_requested")?.jsonPrimitive?.content)
            assertEquals("OTA-B-552", draft?.get("channel_reference")?.jsonPrimitive?.content)
            assertEquals("PROVIDER_B", draft?.get("source_provider")?.jsonPrimitive?.content)
            assertTrue(json["errors"]?.jsonArray?.isEmpty() == true)
        }

    @Test
    fun `4 - unknown provider returns 400 with UNSUPPORTED_PROVIDER`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "UNKNOWN_PARTNER",
                  "payload": {
                    "some_key": "some_value"
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject

            assertEquals("FAILED", json["status"]?.jsonPrimitive?.content)
            val errors = json["errors"]?.jsonArray
            assertTrue(errors != null && errors.isNotEmpty())
            val firstError = errors?.get(0)?.jsonObject
            assertEquals("provider", firstError?.get("field")?.jsonPrimitive?.content)
            assertEquals("UNSUPPORTED_PROVIDER", firstError?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `5 - invalid payload schema returns 400 with INVALID_SCHEMA`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Invalido",
                    "arrival": "data-invalida",
                    "nights": 2
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject

            assertEquals("FAILED", json["status"]?.jsonPrimitive?.content)
            val errors = json["errors"]?.jsonArray
            assertTrue(errors != null && errors.isNotEmpty())
            val firstError = errors?.get(0)?.jsonObject
            assertEquals("payload", firstError?.get("field")?.jsonPrimitive?.content)
            assertEquals("INVALID_SCHEMA", firstError?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `6 - preservation of header X-Correlation-ID in response header and body`() =
        testApplication {
            application {
                module()
            }

            val expectedCorrelationId = "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f"
            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Joao Teste",
                    "arrival": "2026-12-01",
                    "nights": 1,
                    "room_count": 1,
                    "channel_reference": "REF-CORR"
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", expectedCorrelationId)
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(expectedCorrelationId, response.headers["X-Correlation-ID"])

            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals(expectedCorrelationId, json["correlation_id"]?.jsonPrimitive?.content)
        }
}
