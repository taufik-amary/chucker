package com.chuckerteam.chucker.internal.data.entity

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class MockRuleTest {
    @Test
    fun `headers are read one per line, ignoring blank lines and spaces`() {
        val headers = parseMockHeaders("  Content-Type: application/json \n\nX-Mock:yes\n")

        assertThat(headers?.get("Content-Type")).isEqualTo("application/json")
        assertThat(headers?.get("X-Mock")).isEqualTo("yes")
        assertThat(headers?.size).isEqualTo(2)
    }

    @Test
    fun `no headers is valid`() {
        assertThat(parseMockHeaders(" \n ")?.size).isEqualTo(0)
    }

    @Test
    fun `line that is not a header is rejected`() {
        assertThat(parseMockHeaders("Content-Type application/json")).isNull()
        assertThat(parseMockHeaders(": no name")).isNull()
        assertThat(parseMockHeaders("Bad Name: value")).isNull()
    }

    @Test
    fun `formatted headers are read back`() {
        val text = formatMockHeaders(listOf(HttpHeader("Content-Type", "text/plain"), HttpHeader("X-A", "1")))

        assertThat(text).isEqualTo("Content-Type: text/plain\nX-A: 1")
        assertThat(parseMockHeaders(text)?.get("X-A")).isEqualTo("1")
    }

    @Test
    fun `minified json body is laid out on several lines`() {
        val formatted = formatMockBody("""{"status":"00000","data":{"items":[1,2]}}""")

        assertThat(formatted.lines().size).isGreaterThan(1)
        assertThat(formatted).contains("\"status\": \"00000\"")
    }

    @Test
    fun `formatting keeps numbers and characters as they were`() {
        val formatted = formatMockBody("""{"amount":5.000,"big":12345678901234567890,"html":"<b>&</b>"}""")

        assertThat(formatted).contains("5.000")
        assertThat(formatted).contains("12345678901234567890")
        assertThat(formatted).contains("<b>&</b>")
    }

    @Test
    fun `body that is not a json object or array is left as is`() {
        listOf("hello", "  \"text\"", "<a>1</a>", "{not json", "").forEach { body ->
            assertThat(formatMockBody(body)).isEqualTo(body)
        }
    }
}
