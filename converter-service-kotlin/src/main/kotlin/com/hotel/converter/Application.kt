package com.hotel.converter

import com.hotel.converter.adapter.AdapterRegistry
import com.hotel.converter.domain.CanonicalDraft
import com.hotel.converter.domain.ValidationError
import com.hotel.converter.domain.ValidationResult
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

private const val SERVER_PORT = 8106
private const val SERVER_HOST = "127.0.0.1"

private val jsonEncoder =
    Json {
        encodeDefaults = true
        prettyPrint = false
    }

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
                    respondFailure(call, fallbackId, correlationResult.errors)
                    return@post
                }
                is ValidationResult.Success -> correlationResult.value
            }

        val rawContentType = call.request.headers[HttpHeaders.ContentType]
        val contentTypeError = validateContentType(rawContentType)
        if (contentTypeError != null) {
            respondFailure(call, correlationId, contentTypeError)
            return@post
        }

        try {
            handleConversion(call, correlationId)
        } catch (e: SerializationException) {
            respondFailure(call, correlationId, e.toValidationError())
        } catch (e: IllegalArgumentException) {
            respondFailure(call, correlationId, e.toValidationError())
        } catch (e: IllegalStateException) {
            respondFailure(call, correlationId, e.toValidationError())
        }
    }
}

private suspend fun handleConversion(
    call: ApplicationCall,
    correlationId: String,
) {
    val rawText = call.receiveText()
    val requestResult = validateRequestBody(rawText)
    val conversionRequest =
        when (requestResult) {
            is ValidationResult.Failure -> {
                respondFailure(call, correlationId, requestResult.errors)
                return
            }
            is ValidationResult.Success -> requestResult.value
        }

    val adapter = AdapterRegistry.getAdapter(conversionRequest.provider)
    if (adapter == null) {
        respondFailure(
            call = call,
            correlationId = correlationId,
            error =
                ValidationError(
                    field = "provider",
                    errorCode = "UNSUPPORTED_PROVIDER",
                    message = "Provedor não suportado: ${conversionRequest.provider}",
                ),
        )
        return
    }

    val payload = conversionRequest.payload ?: buildJsonObject { }
    val parseResult = adapter.convert(payload)

    when (parseResult) {
        is ValidationResult.Failure -> respondFailure(call, correlationId, parseResult.errors)
        is ValidationResult.Success -> respondSuccess(call, correlationId, parseResult.value)
    }
}

private fun Exception.toValidationError(): ValidationError =
    when (this) {
        is SerializationException ->
            ValidationError("payload", "INVALID_SCHEMA", "JSON malformatado: ${message ?: ""}".trim())
        else ->
            ValidationError("payload", "INVALID_SCHEMA", message ?: "Argumento inválido")
    }

private suspend fun respondSuccess(
    call: ApplicationCall,
    correlationId: String,
    draft: CanonicalDraft,
) {
    call.response.header("X-Correlation-ID", correlationId)
    val response = ConversionApiResponse.success(correlationId, draft)
    call.respondText(jsonEncoder.encodeToString(response), ContentType.Application.Json, HttpStatusCode.OK)
}

private suspend fun respondFailure(
    call: ApplicationCall,
    correlationId: String,
    errors: List<ValidationError>,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
) {
    call.response.header("X-Correlation-ID", correlationId)
    val response = ConversionApiResponse.failure(correlationId, errors)
    call.respondText(jsonEncoder.encodeToString(response), ContentType.Application.Json, status)
}

private suspend fun respondFailure(
    call: ApplicationCall,
    correlationId: String,
    error: ValidationError,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
) = respondFailure(call, correlationId, listOf(error), status)

