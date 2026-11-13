package com.chuckerteam.chucker.internal.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockEndpoint

/**
 * Kept apart from [ChuckerDatabase], which drops all of its tables on every schema change:
 * losing recorded traffic on upgrade is fine, losing mocks set up by hand is not.
 */
@Database(entities = [MockEndpoint::class, Mock::class], version = 2, exportSchema = false)
internal abstract class MockDatabase : RoomDatabase() {
    abstract fun mockDao(): MockDao

    companion object {
        private const val DB_NAME = "chucker_mock_rules.db"

        /** Version 1 had a single rule per endpoint and was never released. */
        private const val UNRELEASED_VERSION = 1

        fun create(applicationContext: Context): MockDatabase =
            Room
                .databaseBuilder(applicationContext, MockDatabase::class.java, DB_NAME)
                // The mocks are read from the interceptor, on whichever thread the app runs its calls.
                .allowMainThreadQueries()
                // Later versions need a migration, so that mocks are kept.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, UNRELEASED_VERSION)
                .build()
    }
}
