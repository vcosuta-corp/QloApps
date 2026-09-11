package com.hotel.converter

import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExceptionMappingTest {
    @Test
    fun `SerializationException maps to INVALID_SCHEMA with malformed json prefix`() {
        val ex = SerializationException("Unexpected token")
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("JSON malformatado: Unexpected token", error.message)
    }

    @Test
    fun `IllegalStateException with message preserves message`() {
        val ex = IllegalStateException("Canal desativado temporariamente")
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Canal desativado temporariamente", error.message)
    }

    @Test
    fun `IllegalStateException without message falls back to Estado invalido`() {
        val ex = IllegalStateException()
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Estado inválido", error.message)
    }

    @Test
    fun `IllegalArgumentException with message preserves message`() {
        val ex = IllegalArgumentException("Parâmetro inválido")
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Parâmetro inválido", error.message)
    }

    @Test
    fun `IllegalArgumentException without message falls back to Argumento invalido`() {
        val ex = IllegalArgumentException()
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Argumento inválido", error.message)
    }

    @Test
    fun `generic Exception without message falls back to Erro de processamento da requisicao`() {
        val ex = RuntimeException()
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Erro de processamento da requisição", error.message)
    }
}
