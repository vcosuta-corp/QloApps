package com.hotel.converter

import com.hotel.converter.domain.ValidationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestBodyValidationTest {
    @Test
    fun `empty or blank body returns INVALID_SCHEMA error`() {
        val result = validateRequestBody("   ")
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("payload", failure.errors[0].field)
        assertEquals("INVALID_SCHEMA", failure.errors[0].errorCode)
    }

    @Test
    fun `malformed JSON returns INVALID_SCHEMA error`() {
        val result = validateRequestBody("{ invalid json }")
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("payload", failure.errors[0].field)
        assertEquals("INVALID_SCHEMA", failure.errors[0].errorCode)
    }

    @Test
    fun `JSON array at root returns INVALID_SCHEMA error`() {
        val result = validateRequestBody("""[{"provider": "PROVIDER_A"}]""")
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("payload", failure.errors[0].field)
        assertEquals("INVALID_SCHEMA", failure.errors[0].errorCode)
        assertEquals("O corpo da requisição deve ser um objeto JSON.", failure.errors[0].message)
    }

    @Test
    fun `JSON primitive string at root returns INVALID_SCHEMA error`() {
        val result = validateRequestBody(""""some string"""")
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("payload", failure.errors[0].field)
        assertEquals("INVALID_SCHEMA", failure.errors[0].errorCode)
    }

    @Test
    fun `missing provider field returns FIELD_REQUIRED error`() {
        val result = validateRequestBody("""{"payload": {}}""")
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("provider", failure.errors[0].field)
        assertEquals("FIELD_REQUIRED", failure.errors[0].errorCode)
    }

    @Test
    fun `non-string provider field returns INVALID_SCHEMA error`() {
        val result = validateRequestBody("""{"provider": 123, "payload": {}}""")
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("provider", failure.errors[0].field)
        assertEquals("INVALID_SCHEMA", failure.errors[0].errorCode)
        assertEquals("Campo 'provider' deve ser uma string.", failure.errors[0].message)
    }

    @Test
    fun `empty or blank provider string returns INVALID_SCHEMA error`() {
        val result = validateRequestBody("""{"provider": "   ", "payload": {}}""")
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("provider", failure.errors[0].field)
        assertEquals("INVALID_SCHEMA", failure.errors[0].errorCode)
        assertEquals("Campo 'provider' não pode ser vazio.", failure.errors[0].message)
    }

    @Test
    fun `missing payload field returns FIELD_REQUIRED error`() {
        val result = validateRequestBody("""{"provider": "PROVIDER_A"}""")
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("payload", failure.errors[0].field)
        assertEquals("FIELD_REQUIRED", failure.errors[0].errorCode)
    }

    @Test
    fun `non-object payload field returns INVALID_SCHEMA error`() {
        val result = validateRequestBody("""{"provider": "PROVIDER_A", "payload": "not-an-object"}""")
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("payload", failure.errors[0].field)
        assertEquals("INVALID_SCHEMA", failure.errors[0].errorCode)
        assertEquals("Campo 'payload' deve ser um objeto JSON.", failure.errors[0].message)
    }

    @Test
    fun `both provider and payload missing returns multiple FIELD_REQUIRED errors`() {
        val result = validateRequestBody("{}")
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertEquals(2, failure.errors.size)
        assertEquals("provider", failure.errors[0].field)
        assertEquals("FIELD_REQUIRED", failure.errors[0].errorCode)
        assertEquals("payload", failure.errors[1].field)
        assertEquals("FIELD_REQUIRED", failure.errors[1].errorCode)
    }

    @Test
    fun `valid provider and payload returns Success with ConversionRequest`() {
        val result = validateRequestBody("""{"provider": "PROVIDER_A", "payload": {"key": "value"}}""")
        assertTrue(result is ValidationResult.Success)
        val success = result as ValidationResult.Success
        assertEquals("PROVIDER_A", success.value.provider)
        assertEquals("value", success.value.payload?.get("key")?.toString()?.replace("\"", ""))
    }
}
