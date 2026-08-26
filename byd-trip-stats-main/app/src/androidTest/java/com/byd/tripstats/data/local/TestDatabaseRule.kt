package com.byd.tripstats.data.local

import android.content.Context
import androidx.room.Room
import org.junit.rules.ExternalResource

/**
 * JUnit rule that creates a fresh in-memory [BydStatsDatabase] for each test
 * and injects it into the singleton before the test runs, then tears it down
 * and resets the singleton after.
 *
 * Use this in every androidTest class that touches the database:
 *
 *     @get:Rule val dbRule = TestDatabaseRule()
 *
 *     @Before fun setUp() {
 *         // dbRule.db is already set and injected — just create your repositories
 *         repo = TripRepository.getInstance(context)
 *     }
 *
 * This guarantees the real on-disk database at
 * /data/data/com.byd.tripstats/databases/byd_stats_database is never opened
 * or modified by any test, even if setUp() throws or the test crashes.
 */
class TestDatabaseRule(
    private val context: Context =
        androidx.test.core.app.ApplicationProvider.getApplicationContext()
) : ExternalResource() {

    lateinit var db: BydStatsDatabase
        private set

    override fun before() {
        db = Room.inMemoryDatabaseBuilder(context, BydStatsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Inject before any repository singleton can call getDatabase()
        BydStatsDatabase::class.java
            .getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }
            .set(null, db)
    }

    override fun after() {
        // Reset singleton first so the next test gets a fresh instance
        BydStatsDatabase::class.java
            .getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }
            .set(null, null)
        db.close()
    }
}