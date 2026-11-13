package com.chuckerteam.chucker.internal.data.repository

import android.content.Context
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chuckerteam.chucker.internal.data.entity.MapLocalMode
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockAction
import com.chuckerteam.chucker.internal.data.entity.MockType
import com.chuckerteam.chucker.internal.data.entity.toMockRuleUrl
import com.chuckerteam.chucker.internal.data.room.MockDao
import com.chuckerteam.chucker.internal.data.room.MockDatabase
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
internal class MockDatabaseRepositoryTest {
    private lateinit var db: MockDatabase
    private lateinit var testObject: MockDatabaseRepository

    private val requestUrl = "https://example.com/users".toHttpUrl()
    private val endpointUrl = requestUrl.toMockRuleUrl()

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db =
            Room
                .inMemoryDatabaseBuilder(context, MockDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        testObject = MockDatabaseRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun mock(
        name: String = "Staging",
        type: MockType = MockType.MAP_REMOTE,
        target: String? = "https://mock.example.com/users",
        method: String = "GET",
        id: Long = 0L,
        statusCode: Int? = 503,
    ) = Mock(
        id = id,
        requestUrl = endpointUrl,
        requestMethod = method,
        name = name,
        type = type,
        targetUrl = target,
        localMode = MapLocalMode.OVERRIDE,
        statusCode = statusCode,
        headers = "X-A: 1",
        body = "local",
        updatedAt = 0L,
    )

    private fun save(mock: Mock) = runBlocking { testObject.saveMock(mock) }

    private fun actionOf(
        url: String = requestUrl.toString(),
        method: String = "GET",
    ) = testObject.findAction(url.toHttpUrl(), method)

    private fun targetOf(
        url: String = requestUrl.toString(),
        method: String = "GET",
    ) = (actionOf(url, method) as? MockAction.MapRemote)?.target?.toString()

    /** Room queries the database on another thread, then delivers the result on the main one. */
    private fun <T> LiveData<T>.read(): T? {
        observeForever { }
        repeat(READ_ATTEMPTS) {
            shadowOf(Looper.getMainLooper()).idle()
            value?.let { return it }
            Thread.sleep(READ_INTERVAL_MS)
        }
        return value
    }

    @Test
    fun `first mock of an endpoint is chosen and applied`() {
        save(mock())

        // A new instance has nothing in memory yet, like the first call after the app starts.
        val action = MockDatabaseRepository(db).findAction(requestUrl, "GET")

        assertThat(action).isEqualTo(MockAction.MapRemote("https://mock.example.com/users".toHttpUrl(), "Staging"))
    }

    @Test
    fun `further mocks are added without replacing the chosen one`() {
        save(mock(name = "First", target = "https://mock.example.com/first"))

        save(mock(name = "Second", target = "https://mock.example.com/second"))

        assertThat(targetOf()).isEqualTo("https://mock.example.com/first")
        val endpoint = testObject.getEndpoint(endpointUrl, "GET").read()!!
        assertThat(endpoint.mocks.map { it.name }).containsExactly("First", "Second").inOrder()
    }

    @Test
    fun `choosing another mock applies it`() {
        save(mock(name = "First", target = "https://mock.example.com/first"))
        val second = save(mock(name = "Second", target = "https://mock.example.com/second"))

        runBlocking { testObject.selectMock(endpointUrl, "GET", second) }

        assertThat(targetOf()).isEqualTo("https://mock.example.com/second")
    }

    @Test
    fun `map local mock answers with its response and name`() {
        save(mock(name = "Server down", type = MockType.MAP_LOCAL))

        assertThat(actionOf())
            .isEqualTo(MockAction.MapLocal(MapLocalMode.OVERRIDE, 503, "X-A: 1", "local", "Server down"))
    }

    @Test
    fun `chosen mock without the settings of its type is not applied`() {
        save(mock(type = MockType.MAP_LOCAL, statusCode = null))

        assertThat(actionOf()).isNull()
    }

    @Test
    fun `nothing is applied while the endpoint is off, until it is on again`() {
        save(mock())

        runBlocking { testObject.setEnabled(endpointUrl, "GET", false) }
        assertThat(actionOf()).isNull()

        runBlocking { testObject.setEnabled(endpointUrl, "GET", true) }
        assertThat(actionOf()).isNotNull()
    }

    @Test
    fun `turning on an endpoint without mocks applies nothing`() {
        runBlocking { testObject.setEnabled(endpointUrl, "GET", true) }

        assertThat(actionOf()).isNull()
        val endpoint = testObject.getEndpoint(endpointUrl, "GET").read()!!
        assertThat(endpoint.isEnabled).isTrue()
        assertThat(endpoint.activeMock).isNull()
    }

    @Test
    fun `editing the chosen mock changes future calls`() {
        val id = save(mock(target = "https://mock.example.com/first"))

        save(mock(id = id, target = "https://mock.example.com/edited"))

        assertThat(targetOf()).isEqualTo("https://mock.example.com/edited")
        assertThat(testObject.getEndpoint(endpointUrl, "GET").read()!!.mocks).hasSize(1)
    }

    @Test
    fun `deleting the chosen mock leaves none chosen`() {
        val id = save(mock(name = "First"))
        save(mock(name = "Second"))

        runBlocking { testObject.deleteMock(id) }

        assertThat(actionOf()).isNull()
        val endpoint = testObject.getEndpoint(endpointUrl, "GET").read()!!
        assertThat(endpoint.activeMock).isNull()
        assertThat(endpoint.mocks.map { it.name }).containsExactly("Second")
    }

    @Test
    fun `mocks are specific to a method, matched ignoring case`() {
        save(mock(method = "get", target = "https://mock.example.com/get"))
        save(mock(method = "POST", target = "https://mock.example.com/post"))

        assertThat(targetOf(method = "GET")).isEqualTo("https://mock.example.com/get")
        assertThat(targetOf(method = "POST")).isEqualTo("https://mock.example.com/post")
        assertThat(actionOf(method = "DELETE")).isNull()
    }

    @Test
    fun `query parameters of the call are forwarded to a target without any`() {
        save(mock())

        assertThat(targetOf("https://example.com/users?page=2&q=a%20b"))
            .isEqualTo("https://mock.example.com/users?page=2&q=a%20b")
        assertThat(actionOf("https://example.com/other")).isNull()
    }

    @Test
    fun `deleting an endpoint removes its mocks only`() {
        save(mock(method = "GET"))
        save(mock(method = "POST"))

        runBlocking { testObject.deleteEndpoint(endpointUrl, "GET") }

        assertThat(actionOf(method = "GET")).isNull()
        assertThat(actionOf(method = "POST")).isNotNull()
        assertThat(testObject.getEndpoints().read()!!.map { it.requestMethod }).containsExactly("POST")
    }

    @Test
    fun `disabling all endpoints keeps their mocks`() {
        save(mock(method = "GET"))
        save(mock(method = "POST"))
        runBlocking { testObject.setEnabled(endpointUrl, "POST", false) }

        val disabled = runBlocking { testObject.disableAll() }

        assertThat(disabled).isEqualTo(1)
        assertThat(actionOf(method = "GET")).isNull()
        val endpoints = testObject.getEndpoints().read()!!
        assertThat(endpoints.map { it.isEnabled }).containsExactly(false, false)
        assertThat(endpoints.flatMap { it.mocks }).hasSize(2)
    }

    @Test
    fun `deleting all removes every endpoint and mock`() {
        save(mock(method = "GET"))
        save(mock(method = "POST"))

        val deleted = runBlocking { testObject.deleteAll() }

        assertThat(deleted).isEqualTo(2)
        assertThat(actionOf()).isNull()
        assertThat(testObject.getEndpoints().read()).isEmpty()
    }

    @Test
    fun `failing to open the database does not break the call`() {
        val brokenDao =
            mockk<MockDao> {
                every { getActiveMocks() } throws IllegalStateException("A migration was required")
            }
        val brokenDb = mockk<MockDatabase> { every { mockDao() } returns brokenDao }

        assertThat(MockDatabaseRepository(brokenDb).findAction(requestUrl, "GET")).isNull()
    }

    @Test
    fun `failing to observe the mocks shows no endpoints`() {
        val brokenDao =
            mockk<MockDao> {
                every { observeEndpoints() } returns flow { throw IllegalStateException("A migration was required") }
                every { observeMocks() } returns flow { emit(emptyList()) }
            }
        val brokenDb = mockk<MockDatabase> { every { mockDao() } returns brokenDao }

        assertThat(MockDatabaseRepository(brokenDb).getEndpoints().read()).isEmpty()
    }

    private companion object {
        const val READ_ATTEMPTS = 100
        const val READ_INTERVAL_MS = 20L
    }
}
