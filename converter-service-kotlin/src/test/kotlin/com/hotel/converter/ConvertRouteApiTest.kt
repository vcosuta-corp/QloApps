package com.hotel.converter

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val VALID_CORRELATION_ID = "c3d4e5f6-a7b8-4c0d-9e2f-3a4b5c6d7e8f"

@Suppress("LargeClass")
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
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
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
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
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
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
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
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
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
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(VALID_CORRELATION_ID, response.headers["X-Correlation-ID"])

            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals(VALID_CORRELATION_ID, json["correlation_id"]?.jsonPrimitive?.content)
        }

    @Test
    fun `7 - missing nights in PROVIDER_A returns 400 with FIELD_REQUIRED`() =
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
                    "arrival": "2026-09-10"
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("FAILED", json["status"]?.jsonPrimitive?.content)
            val firstError = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("nights", firstError?.get("field")?.jsonPrimitive?.content)
            assertEquals("FIELD_REQUIRED", firstError?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `8 - missing or empty guest_full_name in PROVIDER_A returns 400`() =
        testApplication {
            application {
                module()
            }

            val missingPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "arrival": "2026-09-10",
                    "nights": 2
                  }
                }
                """.trimIndent()

            val resMissing =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(missingPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, resMissing.status)
            val jsonMissing = Json.parseToJsonElement(resMissing.bodyAsText()).jsonObject
            val errMissing = jsonMissing["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("guest_full_name", errMissing?.get("field")?.jsonPrimitive?.content)
            assertEquals("FIELD_REQUIRED", errMissing?.get("error_code")?.jsonPrimitive?.content)

            val emptyPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "   ",
                    "arrival": "2026-09-10",
                    "nights": 2
                  }
                }
                """.trimIndent()

            val resEmpty =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(emptyPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, resEmpty.status)
            val jsonEmpty = Json.parseToJsonElement(resEmpty.bodyAsText()).jsonObject
            val errEmpty = jsonEmpty["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("guest_full_name", errEmpty?.get("field")?.jsonPrimitive?.content)
            assertEquals("INVALID_SCHEMA", errEmpty?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `9 - nights less than or equal to 0 in PROVIDER_A returns 400 with CHECKOUT_BEFORE_CHECKIN`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Carlos Silva",
                    "arrival": "2026-09-10",
                    "nights": 0
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val firstError = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("nights", firstError?.get("field")?.jsonPrimitive?.content)
            assertEquals("CHECKOUT_BEFORE_CHECKIN", firstError?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `10 - room_count less than or equal to 0 in PROVIDER_A returns 400 with INVALID_SCHEMA`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Carlos Silva",
                    "arrival": "2026-09-10",
                    "nights": 2,
                    "room_count": 0
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val firstError = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("room_count", firstError?.get("field")?.jsonPrimitive?.content)
            assertEquals("INVALID_SCHEMA", firstError?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `11 - checkout before or equal to checkin in PROVIDER_B returns 400 with CHECKOUT_BEFORE_CHECKIN`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_B",
                  "payload": {
                    "customer": { "first_name": "Ana", "last_name": "Souza" },
                    "checkin_date": "2026-10-10",
                    "checkout_date": "2026-10-05"
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("FAILED", json["status"]?.jsonPrimitive?.content)
            val firstError = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("CHECKOUT_BEFORE_CHECKIN", firstError?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `12 - missing or incomplete customer in PROVIDER_B returns 400`() =
        testApplication {
            application {
                module()
            }

            val payloadNoCustomer =
                """
                {
                  "provider": "PROVIDER_B",
                  "payload": {
                    "checkin_date": "2026-10-10",
                    "checkout_date": "2026-10-15"
                  }
                }
                """.trimIndent()

            val resNoCust =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(payloadNoCustomer)
                }

            assertEquals(HttpStatusCode.BadRequest, resNoCust.status)
            val jsonNoCust = Json.parseToJsonElement(resNoCust.bodyAsText()).jsonObject
            val firstErrNoCust = jsonNoCust["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("customer", firstErrNoCust?.get("field")?.jsonPrimitive?.content)

            val payloadNoFirstName =
                """
                {
                  "provider": "PROVIDER_B",
                  "payload": {
                    "customer": { "first_name": "   ", "last_name": "Souza" },
                    "checkin_date": "2026-10-10",
                    "checkout_date": "2026-10-15"
                  }
                }
                """.trimIndent()

            val resNoFirst =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(payloadNoFirstName)
                }

            assertEquals(HttpStatusCode.BadRequest, resNoFirst.status)
            val jsonNoFirst = Json.parseToJsonElement(resNoFirst.bodyAsText()).jsonObject
            val errNoFirst = jsonNoFirst["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("customer.first_name", errNoFirst?.get("field")?.jsonPrimitive?.content)
            assertEquals("FIELD_REQUIRED", errNoFirst?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `13 - duplicate spaces and control characters in guest name are normalized (RN-004)`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "   Maria \t \n  \u0007  Oliveira   ",
                    "arrival": "2026-09-10",
                    "nights": 2,
                    "room_count": 1
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val draft = json["draft"]?.jsonObject
            assertEquals("Maria Oliveira", draft?.get("guest_name")?.jsonPrimitive?.content)
        }

    @Test
    fun `14 - special characters and quotes in channel reference are safely serialized`() =
        testApplication {
            application {
                module()
            }

            val complexRef = """BOOK"123\456/789"""
            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Marcos Lima",
                    "arrival": "2026-09-10",
                    "nights": 1,
                    "channel_reference": "BOOK\"123\\456/789"
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val draft = json["draft"]?.jsonObject
            assertEquals(complexRef, draft?.get("channel_reference")?.jsonPrimitive?.content)
        }

    @Test
    fun `15 - missing X-Correlation-ID header returns 400 with HEADER_REQUIRED`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Marcos Lima",
                    "arrival": "2026-09-10",
                    "nights": 1
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    // Intentionally omitting X-Correlation-ID
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val firstErr = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("X-Correlation-ID", firstErr?.get("field")?.jsonPrimitive?.content)
            assertEquals("HEADER_REQUIRED", firstErr?.get("error_code")?.jsonPrimitive?.content)
            assertTrue(response.headers["X-Correlation-ID"]?.isNotBlank() == true)
        }

    @Test
    fun `16 - empty or blank X-Correlation-ID header returns 400 with INVALID_HEADER`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Marcos Lima",
                    "arrival": "2026-09-10",
                    "nights": 1
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", "   ")
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val firstErr = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("X-Correlation-ID", firstErr?.get("field")?.jsonPrimitive?.content)
            assertEquals("INVALID_HEADER", firstErr?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `17 - non-UUID-v4 X-Correlation-ID header returns 400 with INVALID_HEADER`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Marcos Lima",
                    "arrival": "2026-09-10",
                    "nights": 1
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", "not-a-valid-uuid-v4")
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val firstErr = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("X-Correlation-ID", firstErr?.get("field")?.jsonPrimitive?.content)
            assertEquals("INVALID_HEADER", firstErr?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `18 - missing Content-Type header returns 400 with HEADER_REQUIRED`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Marcos Lima",
                    "arrival": "2026-09-10",
                    "nights": 1
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(ByteArrayContent(requestPayload.toByteArray(), contentType = null))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val firstErr = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("Content-Type", firstErr?.get("field")?.jsonPrimitive?.content)
            assertEquals("HEADER_REQUIRED", firstErr?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `19 - unsupported Content-Type header returns 400 with UNSUPPORTED_MEDIA_TYPE`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Marcos Lima",
                    "arrival": "2026-09-10",
                    "nights": 1
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, "text/plain")
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val firstErr = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("Content-Type", firstErr?.get("field")?.jsonPrimitive?.content)
            assertEquals("UNSUPPORTED_MEDIA_TYPE", firstErr?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `20 - Content-Type application-json with charset parameter returns 200`() =
        testApplication {
            application {
                module()
            }

            val requestPayload =
                """
                {
                  "provider": "PROVIDER_A",
                  "payload": {
                    "guest_full_name": "Carlos Silva",
                    "arrival": "2026-10-15",
                    "nights": 2,
                    "room_count": 1,
                    "channel_reference": "REF-CHARSET"
                  }
                }
                """.trimIndent()

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, "application/json; charset=utf-8")
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(requestPayload)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("SUCCESS", json["status"]?.jsonPrimitive?.content)
            val draft = json["draft"]?.jsonObject
            assertEquals("Carlos Silva", draft?.get("guest_name")?.jsonPrimitive?.content)
        }

    @Test
    fun `21 - JSON array body returns 400 with INVALID_SCHEMA`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody("""[{"provider": "PROVIDER_A"}]""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("FAILED", json["status"]?.jsonPrimitive?.content)
            val firstErr = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("payload", firstErr?.get("field")?.jsonPrimitive?.content)
            assertEquals("INVALID_SCHEMA", firstErr?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `22 - missing provider in body returns 400 with FIELD_REQUIRED`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody("""{"payload": {}}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("FAILED", json["status"]?.jsonPrimitive?.content)
            val firstErr = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("provider", firstErr?.get("field")?.jsonPrimitive?.content)
            assertEquals("FIELD_REQUIRED", firstErr?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `23 - non-string provider in body returns 400 with INVALID_SCHEMA`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody("""{"provider": 123, "payload": {}}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("FAILED", json["status"]?.jsonPrimitive?.content)
            val firstErr = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("provider", firstErr?.get("field")?.jsonPrimitive?.content)
            assertEquals("INVALID_SCHEMA", firstErr?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `24 - missing payload in body returns 400 with FIELD_REQUIRED`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody("""{"provider": "PROVIDER_A"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("FAILED", json["status"]?.jsonPrimitive?.content)
            val firstErr = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("payload", firstErr?.get("field")?.jsonPrimitive?.content)
            assertEquals("FIELD_REQUIRED", firstErr?.get("error_code")?.jsonPrimitive?.content)
        }

    @Test
    fun `25 - non-object payload in body returns 400 with INVALID_SCHEMA`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/v1/external-reservation-requests/convert") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody("""{"provider": "PROVIDER_A", "payload": "not-an-object"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("FAILED", json["status"]?.jsonPrimitive?.content)
            val firstErr = json["errors"]?.jsonArray?.get(0)?.jsonObject
            assertEquals("payload", firstErr?.get("field")?.jsonPrimitive?.content)
            assertEquals("INVALID_SCHEMA", firstErr?.get("error_code")?.jsonPrimitive?.content)
        }
}
