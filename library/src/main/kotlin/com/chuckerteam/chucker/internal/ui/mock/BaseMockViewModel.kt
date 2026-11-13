package com.chuckerteam.chucker.internal.ui.mock

import android.database.sqlite.SQLiteException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.chuckerteam.chucker.internal.data.repository.MockRepository
import com.chuckerteam.chucker.internal.data.repository.RepositoryProvider
import com.chuckerteam.chucker.internal.support.Logger
import kotlinx.coroutines.CancellationException

/** Reports database failures of the mock screens instead of crashing. */
internal abstract class BaseMockViewModel : ViewModel() {
    protected val repository: MockRepository = RepositoryProvider.mock()

    private val mutableDatabaseFailed = MutableLiveData<Boolean>()

    /** Emits when the mocks couldn't be read or changed. */
    val databaseFailed: LiveData<Boolean> = mutableDatabaseFailed

    /** Runs [action], returning null and reporting through [databaseFailed] if the database fails. */
    protected suspend fun <T : Any> attempt(action: suspend () -> T?): T? =
        try {
            action()
        } catch (e: CancellationException) {
            // Is an IllegalStateException, and has to keep cancelling the coroutine.
            throw e
        } catch (e: SQLiteException) {
            onDatabaseFailed(e)
        } catch (e: IllegalStateException) {
            // What Room throws when it can't open the database, e.g. after a downgrade.
            onDatabaseFailed(e)
        }

    private fun onDatabaseFailed(cause: Exception): Nothing? {
        Logger.error("Failed to access mocks", cause)
        mutableDatabaseFailed.postValue(true)
        return null
    }
}
