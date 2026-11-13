package com.chuckerteam.chucker.api

import com.chuckerteam.chucker.internal.data.entity.MapLocalMode
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockAction
import com.chuckerteam.chucker.internal.data.entity.MockType
import com.chuckerteam.chucker.internal.data.entity.toMockRuleUrl
import com.chuckerteam.chucker.internal.data.repository.MockDatabaseRepository
import com.chuckerteam.chucker.internal.data.room.MockDatabase
import com.chuckerteam.chucker.util.ChuckerInterceptorDelegate
import com.chuckerteam.chucker.util.ClientFactory
import com.chuckerteam.chucker.util.NoLoggerRule
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit

@ExtendWith(NoLoggerRule::class)
internal class ChuckerInterceptorMockLocalTest {
    private val server = MockWebServer()
    private val targetServer = MockWebServer()

    private val ruleUrl = server.url("/users?page=1")

    @TempDir
    lateinit var tempDir: File

    @AfterEach
    fun tearDown() {
        server.shutdown()
        targetServer.shutdown()
    }

    private fun rule(
        mode: MapLocalMode = MapLocalMode.MOCK,
        statusCode: Int = 418,
        headers: String = "Content-Type: application/json\nX-Mock: yes",
        body: String = """{"mocked":true}""",
    ) = MockAction.MapLocal(mode, statusCode, headers, body, name = "Teapot")

    private fun chucker(
        rule: MockAction.MapLocal = rule(),
        skipPaths: List<String> = emptyList(),
    ) = ChuckerInterceptorDelegate(
        cacheDirectoryProvider = { tempDir },
        skipPaths = skipPaths,
        mockResolver = { url, _ -> rule.takeIf { url == ruleUrl } },
    )

    /**
     * The repository used by the app, so that mocks are matched the way they are in production.
     * [activeMocks] are the chosen mocks of the enabled endpoints, which the database selects.
     */
    private fun chuckerWithActiveMocks(vararg activeMocks: Mock): ChuckerInterceptorDelegate {
        val database =
            mockk<MockDatabase> {
                every { mockDao() } returns mockk { every { getActiveMocks() } returns activeMocks.toList() }
            }
        return ChuckerInterceptorDelegate(
            cacheDirectoryProvider = { tempDir },
            mockResolver = MockDatabaseRepository(database),
        )
    }

    /** Saved without its query, like the mocks saved from the Chucker UI. */
    private fun savedMock(
        method: String = "GET",
        type: MockType = MockType.MAP_LOCAL,
    ) = Mock(
        id = 1L,
        requestUrl = ruleUrl.toMockRuleUrl(),
        requestMethod = method,
        name = "Saved",
        type = type,
        targetUrl = targetServer.url("/remote").toString(),
        localMode = MapLocalMode.MOCK,
        statusCode = 418,
        headers = "",
        body = """{"mocked":true}""",
        updatedAt = 0L,
    )

    private fun ChuckerInterceptorDelegate.call(
        factory: ClientFactory = ClientFactory.APPLICATION,
        request: Request = Request.Builder().url(ruleUrl).build(),
    ): Pair<Response, String> =
        factory
            .create(this)
            .newCall(request)
            .execute()
            .use { it to it.body.string() }

    @Test
    fun `mock answers the call without reaching the server`() {
        val (response, body) = chucker().call()

        assertThat(server.requestCount).isEqualTo(0)
        assertThat(response.code).isEqualTo(418)
        assertThat(response.header("X-Mock")).isEqualTo("yes")
        assertThat(response.body.contentType().toString()).startsWith("application/json")
        assertThat(body).isEqualTo("""{"mocked":true}""")
    }

    @Test
    fun `mock is recorded with the request and the mocked response`() {
        val chucker = chucker()

        chucker.call(
            request =
                Request
                    .Builder()
                    .url(ruleUrl)
                    .post("payload".toRequestBody())
                    .build(),
        )

        val transaction = chucker.expectTransaction()
        assertThat(transaction.mapLocalMode).isEqualTo("MOCK")
        assertThat(transaction.mockName).isEqualTo("Teapot")
        assertThat(transaction.isMockIgnored).isFalse()
        assertThat(transaction.requestBody).isEqualTo("payload")
        assertThat(transaction.responseCode).isEqualTo(418)
        assertThat(transaction.responseBody).isEqualTo("""{"mocked":true}""")
        assertThat(transaction.getParsedResponseHeaders()?.map { it.name }).contains("X-Mock")
    }

    @Test
    fun `mock drops headers that no longer match its body`() {
        val chucker =
            chucker(
                rule(headers = "Content-Length: 3\nContent-Encoding: gzip\nTransfer-Encoding: chunked"),
            )

        val (response, body) = chucker.call()

        assertThat(body).isEqualTo("""{"mocked":true}""")
        assertThat(response.header("Content-Length")).isNull()
        assertThat(response.header("Content-Encoding")).isNull()
        assertThat(response.header("Transfer-Encoding")).isNull()
        assertThat(chucker.expectTransaction().responseBody).isEqualTo("""{"mocked":true}""")
    }

    @Test
    fun `mock of an empty response has an empty body`() {
        val (response, body) = chucker(rule(statusCode = 204, headers = "", body = "")).call()

        assertThat(response.code).isEqualTo(204)
        assertThat(body).isEmpty()
    }

    @Test
    fun `override calls the server and replaces its status and body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("real"))
        val chucker = chucker(rule(mode = MapLocalMode.OVERRIDE, statusCode = 500, headers = "", body = "overridden"))

        val (response, body) = chucker.call()

        assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull()
        assertThat(response.code).isEqualTo(500)
        assertThat(body).isEqualTo("overridden")
        val transaction = chucker.expectTransaction()
        assertThat(transaction.mapLocalMode).isEqualTo("OVERRIDE")
        assertThat(transaction.responseCode).isEqualTo(500)
        assertThat(transaction.responseBody).isEqualTo("overridden")
    }

    @Test
    fun `override keeps the headers of the server that the rule does not name`() {
        server.enqueue(
            MockResponse()
                .setHeader("X-Server", "kept")
                .setHeader("X-Mock", "replaced")
                .setBody("real"),
        )

        val (response, _) = chucker(rule(mode = MapLocalMode.OVERRIDE)).call()

        assertThat(response.header("X-Server")).isEqualTo("kept")
        assertThat(response.headers("X-Mock")).containsExactly("yes")
    }

    @Test
    fun `override keeps the message of the server only for the same status`() {
        server.enqueue(MockResponse().setStatus("HTTP/1.1 200 Fine"))
        server.enqueue(MockResponse().setStatus("HTTP/1.1 200 Fine"))

        val (same, _) = chucker(rule(mode = MapLocalMode.OVERRIDE, statusCode = 200)).call()
        val (other, _) = chucker(rule(mode = MapLocalMode.OVERRIDE, statusCode = 404)).call()

        assertThat(same.message).isEqualTo("Fine")
        assertThat(other.message).isEmpty()
    }

    @Test
    fun `override replaces a compressed body of the server`() {
        val gzipped = Buffer()
        GzipSink(gzipped).buffer().use { it.writeUtf8("a real body that is longer than the override") }
        server.enqueue(MockResponse().setHeader("Content-Encoding", "gzip").setBody(gzipped))

        val (response, body) =
            chucker(rule(mode = MapLocalMode.OVERRIDE, headers = "", body = "short")).call()

        assertThat(body).isEqualTo("short")
        assertThat(response.header("Content-Length")).isNull()
        assertThat(response.header("Content-Encoding")).isNull()
    }

    @Test
    fun `failed call of an override is reported as failed`() {
        server.shutdown()
        val chucker = chucker(rule(mode = MapLocalMode.OVERRIDE))

        runCatching { chucker.call() }

        val transaction = chucker.expectTransaction()
        assertThat(transaction.error).isNotNull()
        assertThat(transaction.mapLocalMode).isEqualTo("OVERRIDE")
    }

    @Test
    fun `mock of one method leaves the other methods of the same url alone`() {
        server.enqueue(MockResponse().setBody("real post"))
        val chucker = chuckerWithActiveMocks(savedMock(method = "GET"))

        val (get, getBody) = chucker.call()
        val (post, postBody) =
            chucker.call(
                request =
                    Request
                        .Builder()
                        .url(ruleUrl)
                        .post("payload".toRequestBody())
                        .build(),
            )

        assertThat(get.code).isEqualTo(418)
        assertThat(getBody).isEqualTo("""{"mocked":true}""")
        assertThat(post.code).isEqualTo(200)
        assertThat(postBody).isEqualTo("real post")
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)!!.method).isEqualTo("POST")
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(chucker.expectTransaction().mapLocalMode).isEqualTo("MOCK")
        assertThat(chucker.expectTransaction().mapLocalMode).isNull()
    }

    @Test
    fun `only the selected type of a mock is applied`() {
        targetServer.enqueue(MockResponse().setBody("remote"))
        val chucker = chuckerWithActiveMocks(savedMock(type = MockType.MAP_REMOTE))

        val (_, body) = chucker.call()

        // The mock also has Map Local settings, which are kept but not applied.
        assertThat(body).isEqualTo("remote")
        assertThat(server.requestCount).isEqualTo(0)
        val transaction = chucker.expectTransaction()
        assertThat(transaction.mappedUrl).startsWith(targetServer.url("/remote").toString())
        assertThat(transaction.mapLocalMode).isNull()
    }

    @Test
    fun `call without a matching rule is left alone`() {
        server.enqueue(MockResponse().setBody("real"))
        val chucker = chucker()

        val (_, body) = chucker.call(request = Request.Builder().url(server.url("/other")).build())

        assertThat(body).isEqualTo("real")
        val transaction = chucker.expectTransaction()
        assertThat(transaction.mapLocalMode).isNull()
        assertThat(transaction.isMockIgnored).isFalse()
    }

    @Test
    fun `network interceptor ignores the rule instead of failing the call`() {
        server.enqueue(MockResponse().setBody("real"))
        val chucker = chucker()

        val (response, body) = chucker.call(ClientFactory.NETWORK)

        assertThat(response.code).isEqualTo(200)
        assertThat(body).isEqualTo("real")
        val transaction = chucker.expectTransaction()
        assertThat(transaction.mapLocalMode).isNull()
        assertThat(transaction.isMockIgnored).isTrue()
    }

    @Test
    fun `skipped call is not mocked`() {
        server.enqueue(MockResponse().setBody("real"))
        val chucker = chucker(skipPaths = listOf("/users"))

        val (_, body) = chucker.call()

        assertThat(body).isEqualTo("real")
        chucker.expectNoTransactions()
    }
}
