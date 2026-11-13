package com.chuckerteam.chucker.internal.data.repository

import android.database.sqlite.SQLiteException
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.room.withTransaction
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockAction
import com.chuckerteam.chucker.internal.data.entity.MockEndpoint
import com.chuckerteam.chucker.internal.data.entity.MockEndpointWithMocks
import com.chuckerteam.chucker.internal.data.entity.MockType
import com.chuckerteam.chucker.internal.data.entity.toMockRuleUrl
import com.chuckerteam.chucker.internal.data.room.MockDatabase
import com.chuckerteam.chucker.internal.support.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Suppress("TooManyFunctions")
internal class MockDatabaseRepository(
    private val database: MockDatabase,
) : MockRepository {
    private val dao = database.mockDao()
    private val lock = Any()

    /** In-memory copy of the mocks applied to calls, so that calls don't have to query the database. */
    @Volatile
    private var activeMocks: Map<EndpointKey, Mock>? = null

    override fun findAction(
        url: HttpUrl,
        method: String,
    ): MockAction? {
        val mocks = loadActiveMocks()
        if (mocks.isEmpty()) return null
        val mock = mocks[EndpointKey(url.toMockRuleUrl(), method.uppercase())] ?: return null
        return when (mock.type) {
            MockType.MAP_REMOTE -> mock.remoteTarget(url)?.let { MockAction.MapRemote(it, mock.name) }
            MockType.MAP_LOCAL -> mock.localAction()
        }
    }

    /**
     * A mock matches an endpoint, so the parameters of [url] still have to reach the target
     * unless the mock asks for specific ones.
     */
    private fun Mock.remoteTarget(url: HttpUrl): HttpUrl? {
        val target = targetUrl?.toHttpUrlOrNull() ?: return null
        return if (target.encodedQuery == null) {
            target.newBuilder().encodedQuery(url.encodedQuery).build()
        } else {
            target
        }
    }

    private fun Mock.localAction(): MockAction? {
        val mode = localMode ?: return null
        val code = statusCode ?: return null
        return MockAction.MapLocal(mode, code, headers.orEmpty(), body.orEmpty(), name)
    }

    private fun loadActiveMocks(): Map<EndpointKey, Mock> =
        activeMocks ?: synchronized(lock) {
            activeMocks ?: try {
                dao
                    .getActiveMocks()
                    .associateBy { EndpointKey(it.requestUrl, it.requestMethod) }
                    .also { activeMocks = it }
            } catch (e: SQLiteException) {
                onReadFailed(e)
            } catch (e: IllegalStateException) {
                // What Room throws when it can't open the database, e.g. after a downgrade.
                onReadFailed(e)
            }
        }

    /** Failing to read the mocks must not break the calls of the host app. */
    private fun onReadFailed(cause: Exception): Map<EndpointKey, Mock> {
        Logger.error("Failed to read mocks", cause)
        return emptyMap()
    }

    /** Runs [action], then makes the next call read the mocks again. */
    private suspend fun <T> change(action: suspend () -> T): T =
        try {
            database.withTransaction { action() }
        } finally {
            synchronized(lock) { activeMocks = null }
        }

    private fun endpoints(): Flow<List<MockEndpointWithMocks>> =
        combine(dao.observeEndpoints(), dao.observeMocks()) { endpoints, mocks ->
            val mocksByEndpoint = mocks.groupBy { EndpointKey(it.requestUrl, it.requestMethod) }
            endpoints.map { endpoint ->
                MockEndpointWithMocks(
                    requestUrl = endpoint.requestUrl,
                    requestMethod = endpoint.requestMethod,
                    endpoint = endpoint,
                    mocks = mocksByEndpoint[EndpointKey(endpoint.requestUrl, endpoint.requestMethod)].orEmpty(),
                )
            }
        }.catch { cause ->
            Logger.error("Failed to read mocks", cause)
            emit(emptyList())
        }

    override fun getEndpoints(): LiveData<List<MockEndpointWithMocks>> = endpoints().asLiveData()

    override fun getEndpoint(
        requestUrl: String,
        requestMethod: String,
    ): LiveData<MockEndpointWithMocks> {
        val method = requestMethod.uppercase()
        return endpoints()
            .map { endpoints ->
                endpoints.firstOrNull { it.requestUrl == requestUrl && it.requestMethod == method }
                    ?: MockEndpointWithMocks(requestUrl, method, endpoint = null, mocks = emptyList())
            }.asLiveData()
    }

    override suspend fun getMock(id: Long): Mock? = dao.getMock(id)

    override suspend fun saveMock(mock: Mock): Long =
        change {
            val saved = mock.copy(requestMethod = mock.requestMethod.uppercase(), updatedAt = now())
            val id =
                if (saved.id == 0L) {
                    dao.insertMock(saved)
                } else {
                    dao.updateMock(saved)
                    saved.id
                }
            val endpoint = dao.getEndpoint(saved.requestUrl, saved.requestMethod)
            dao.saveEndpoint(
                MockEndpoint(
                    requestUrl = saved.requestUrl,
                    requestMethod = saved.requestMethod,
                    isEnabled = endpoint?.isEnabled ?: true,
                    activeMockId = endpoint?.activeMockId ?: id,
                    updatedAt = now(),
                ),
            )
            id
        }

    override suspend fun deleteMock(id: Long) =
        change {
            dao.unselectMock(id)
            dao.deleteMock(id)
        }

    override suspend fun setEnabled(
        requestUrl: String,
        requestMethod: String,
        isEnabled: Boolean,
    ) = updateEndpoint(requestUrl, requestMethod) { it.copy(isEnabled = isEnabled) }

    override suspend fun selectMock(
        requestUrl: String,
        requestMethod: String,
        mockId: Long,
    ) = updateEndpoint(requestUrl, requestMethod) { it.copy(activeMockId = mockId) }

    private suspend fun updateEndpoint(
        requestUrl: String,
        requestMethod: String,
        update: (MockEndpoint) -> MockEndpoint,
    ) = change {
        val method = requestMethod.uppercase()
        val endpoint =
            dao.getEndpoint(requestUrl, method)
                ?: MockEndpoint(requestUrl, method, isEnabled = false, activeMockId = null, updatedAt = 0L)
        dao.saveEndpoint(update(endpoint).copy(updatedAt = now()))
    }

    override suspend fun deleteEndpoint(
        requestUrl: String,
        requestMethod: String,
    ) = change {
        val method = requestMethod.uppercase()
        dao.deleteMocksOf(requestUrl, method)
        dao.deleteEndpoint(requestUrl, method)
    }

    override suspend fun disableAll(): Int = change { dao.disableAllEndpoints() }

    override suspend fun deleteAll(): Int =
        change {
            dao.deleteAllMocks()
            dao.deleteAllEndpoints()
        }

    private fun now() = System.currentTimeMillis()

    private data class EndpointKey(
        val requestUrl: String,
        val requestMethod: String,
    )
}
