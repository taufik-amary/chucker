package com.chuckerteam.chucker.internal.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockEndpoint
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
internal interface MockDao {
    /** The mocks applied to calls: the selected ones of the enabled endpoints. */
    @Query(
        "SELECT mocks.* FROM mocks INNER JOIN mock_endpoints ON mocks.id = mock_endpoints.activeMockId " +
            "WHERE mock_endpoints.isEnabled = 1",
    )
    fun getActiveMocks(): List<Mock>

    @Query("SELECT * FROM mock_endpoints ORDER BY updatedAt DESC")
    fun observeEndpoints(): Flow<List<MockEndpoint>>

    /** In the order they were added, so that editing one doesn't move it. */
    @Query("SELECT * FROM mocks ORDER BY id")
    fun observeMocks(): Flow<List<Mock>>

    @Query("SELECT * FROM mock_endpoints WHERE requestUrl = :requestUrl AND requestMethod = :requestMethod")
    suspend fun getEndpoint(
        requestUrl: String,
        requestMethod: String,
    ): MockEndpoint?

    @Query("SELECT * FROM mocks WHERE id = :id")
    suspend fun getMock(id: Long): Mock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEndpoint(endpoint: MockEndpoint)

    @Insert
    suspend fun insertMock(mock: Mock): Long

    @Update
    suspend fun updateMock(mock: Mock)

    @Query("DELETE FROM mocks WHERE id = :id")
    suspend fun deleteMock(id: Long)

    @Query("UPDATE mock_endpoints SET activeMockId = NULL WHERE activeMockId = :mockId")
    suspend fun unselectMock(mockId: Long)

    @Query("DELETE FROM mocks WHERE requestUrl = :requestUrl AND requestMethod = :requestMethod")
    suspend fun deleteMocksOf(
        requestUrl: String,
        requestMethod: String,
    )

    @Query("DELETE FROM mock_endpoints WHERE requestUrl = :requestUrl AND requestMethod = :requestMethod")
    suspend fun deleteEndpoint(
        requestUrl: String,
        requestMethod: String,
    )

    @Query("UPDATE mock_endpoints SET isEnabled = 0 WHERE isEnabled = 1")
    suspend fun disableAllEndpoints(): Int

    @Query("DELETE FROM mock_endpoints")
    suspend fun deleteAllEndpoints(): Int

    @Query("DELETE FROM mocks")
    suspend fun deleteAllMocks()
}
