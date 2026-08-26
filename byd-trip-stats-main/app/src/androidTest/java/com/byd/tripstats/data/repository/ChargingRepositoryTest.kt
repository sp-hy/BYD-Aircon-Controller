package com.byd.tripstats.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.byd.tripstats.data.config.CarCatalog
import com.byd.tripstats.data.local.BydStatsDatabase
import com.byd.tripstats.data.local.TestDatabaseRule
import org.junit.Rule
import com.byd.tripstats.data.local.entity.ChargingSessionEntity
import com.byd.tripstats.mock.MockDataGenerator
import com.byd.tripstats.test.telemetry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for ChargingRepository (hybrid approach).
 *
 * Two recording modes:
 *   REAL-TIME  — car is ON (carOn=1), chargingPower>0 → live session + data points
 *   SYNTHETIC  — car was OFF during charge → SoC-delta reconstruction on next wake-up
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ChargingRepositoryTest {

    private lateinit var db  : BydStatsDatabase
    private lateinit var repo: ChargingRepository
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val car = CarCatalog.BYD_SEAL_EXCELLENCE   // 82.5 kWh
    private val gen = MockDataGenerator()
    private val SETTLE = 400L

    private val PREFS_NAME  = "charging_shutdown_state"
    private val KEY_SOC     = "last_soc"
    private val KEY_TS      = "last_timestamp"
    private val KEY_TEMP    = "last_temp_avg"
    private val KEY_VOLTAGE = "last_voltage"

    @get:Rule val dbRule = TestDatabaseRule()

    @Before fun setUp() {
        // dbRule already injected the in-memory DB into BydStatsDatabase.INSTANCE
        db = dbRule.db
        ChargingRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        repo = ChargingRepository.getInstance(context)
        Thread.sleep(SETTLE)
        gen.reset()
    }

    @After fun tearDown() {
        repo.close()
        ChargingRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        // BydStatsDatabase INSTANCE reset and db.close() handled by TestDatabaseRule.after()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test fun initiallyNotCharging() {
        assertFalse(repo.isCharging.value)
        assertNull(repo.activeSessionId.value)
    }

    @Test fun initiallyNoSessions() = runBlocking {
        assertTrue(repo.getAllSessions().first().isEmpty())
    }

    // ── Real-time: car ON ─────────────────────────────────────────────────────

    @Test fun carOnChargingPacketOpensRealTimeSession() = runBlocking {
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, chargingPower = 7.2), car)
        Thread.sleep(SETTLE)
        assertTrue("Should be charging", repo.isCharging.value)
        assertNotNull("Active session ID should be set", repo.activeSessionId.value)
    }

    @Test fun realTimeSessionStoresCorrectStartSoC() = runBlocking {
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, soc = 45.0), car)
        Thread.sleep(SETTLE)
        val session = repo.getSessionById(repo.activeSessionId.value!!)
        assertEquals(45.0, session!!.socStart, 0.1)
    }

    @Test fun realTimeSessionIsMarkedActive() = runBlocking {
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1), car)
        Thread.sleep(SETTLE)
        assertTrue(repo.getAllSessions().first().first().isActive)
    }

    @Test fun realTimeSessionRecordsDataPoints() = runBlocking {
        repeat(3) {
            repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1), car)
            Thread.sleep(50)
        }
        Thread.sleep(SETTLE)
        val id = repo.activeSessionId.value!!
        assertTrue(repo.getDataPointsForSessionSync(id).isNotEmpty())
    }

    @Test fun peakKwUpdatedDuringRealTimeSession() = runBlocking {
        repo.onTelemetry(gen.generateDcChargingTelemetry(carOn = 1, chargingPower = 50.0), car)
        Thread.sleep(SETTLE)
        repo.onTelemetry(gen.generateDcChargingTelemetry(carOn = 1, chargingPower = 80.0), car)
        Thread.sleep(SETTLE)
        val session = repo.getSessionById(repo.activeSessionId.value!!)
        assertEquals(80.0, session!!.peakKw, 0.1)
    }

    @Test fun multipleConsecutivePacketsResultInOneSession() = runBlocking {
        repeat(5) {
            repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1), car)
            Thread.sleep(50)
        }
        Thread.sleep(SETTLE)
        assertEquals(1, repo.getAllSessions().first().size)
    }

    // ── Real-time: closing on non-charging packet ─────────────────────────────

    @Test fun nonChargingPacketWithCarOnClosesSession() = runBlocking {
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1), car)
        Thread.sleep(SETTLE)
        assertNotNull(repo.activeSessionId.value)

        // Non-charging packet while car still on
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, chargingPower = 0.0), car)
        Thread.sleep(SETTLE)

        assertNull("Session should be closed", repo.activeSessionId.value)
        assertFalse(repo.isCharging.value)
        // The session ran for ~400ms with zero SoC delta, so the micro-session guard
        // deletes it rather than persisting it. State reset is the key invariant here.
        assertTrue("Micro-session should be cleaned up", repo.getAllSessions().first().isEmpty())
    }

    @Test fun carOffPacketKeepsSessionOpenWhenStillCharging() = runBlocking {
        // Car-off while still charging = session stays open and continues recording.
        // The process survives ACC_OFF so real-time data collection continues.
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1), car)
        Thread.sleep(SETTLE)
        assertNotNull(repo.activeSessionId.value)

        // Car turns off but charging power is still > 0
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 0), car)
        Thread.sleep(SETTLE)

        // Session should remain open — car-off + charging = keep recording
        assertNotNull("Session should remain open while charging continues after car-off", repo.activeSessionId.value)
        assertTrue(repo.isCharging.value)
    }

    @Test fun carOffWithNoChargingClosesSession() = runBlocking {
        // Car-off + no charging = session closes
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1), car)
        Thread.sleep(SETTLE)
        assertNotNull(repo.activeSessionId.value)

        // Car turns off and charging stops
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 0, chargingPower = 0.0), car)
        Thread.sleep(SETTLE)

        assertNull("Session should close when car-off and not charging", repo.activeSessionId.value)
        assertFalse(repo.isCharging.value)
    }

    // ── SoC-delta reconstruction (car-off charging) ───────────────────────────

    @Test fun socIncreaseOnCarWakeCreatessyntheticSession() = runBlocking {
        writeShutdownState(soc = 50.0f, tsOffsetMs = -3_600_000L)

        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, soc = 75.0, chargingPower = 0.0), car)
        Thread.sleep(SETTLE)

        val sessions = repo.getAllSessions().first()
        assertEquals(1, sessions.size)
        val session = sessions.first()
        assertFalse(session.isActive)
        assertEquals(0.0, session.peakKw, 0.0)   // synthetic — no live data
        assertEquals(0.0, session.avgKw, 0.0)
        assertEquals(50.0, session.socStart, 0.1)
        assertEquals(75.0, session.socEnd!!, 0.1)
    }

    @Test fun syntheticSessionKwhIsCorrect() = runBlocking {
        writeShutdownState(soc = 40.0f, tsOffsetMs = -7_200_000L)
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, soc = 80.0, chargingPower = 0.0), car)
        Thread.sleep(SETTLE)
        // 40% × 82.5 kWh = 33.0 kWh
        assertEquals(33.0, repo.getAllSessions().first().first().kwhAdded!!, 0.5)
    }

    @Test fun socBelowThresholdOnWakeCreatesNoSyntheticSession() = runBlocking {
        writeShutdownState(soc = 79.5f, tsOffsetMs = -600_000L)  // only 0.5% increase
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, soc = 80.0, chargingPower = 0.0), car)
        Thread.sleep(SETTLE)
        assertTrue(repo.getAllSessions().first().isEmpty())
    }

    @Test fun firstEverRunWithNoSavedStateCreatesNoSyntheticSession() = runBlocking {
        // No SharedPreferences written
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, soc = 80.0, chargingPower = 0.0), car)
        Thread.sleep(SETTLE)
        assertTrue(repo.getAllSessions().first().isEmpty())
    }

    @Test fun reconstructionRunsOnlyOncePerServiceLifecycle() = runBlocking {
        writeShutdownState(soc = 50.0f, tsOffsetMs = -3_600_000L)

        // Two carOn packets — reconstruction only on the first
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, soc = 75.0, chargingPower = 0.0), car)
        Thread.sleep(SETTLE)
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, soc = 90.0, chargingPower = 0.0), car)
        Thread.sleep(SETTLE)

        assertEquals(1, repo.getAllSessions().first().filter { !it.isActive }.size)
    }

    @Test fun duplicateGuardPreventsDoubleReconstruction() = runBlocking {
        val shutdownTs = System.currentTimeMillis() - 3_600_000L
        writeShutdownState(soc = 50.0f, tsOffsetMs = -3_600_000L)

        db.chargingSessionDao().insertSession(
            ChargingSessionEntity(
                startTime = shutdownTs + 60_000L,
                socStart  = 50.0, batteryKwh = 82.5,
                carConfigId = "BYD_SEAL_EXCELLENCE",
                isActive = false, socEnd = 75.0, kwhAdded = 20.6
            )
        )
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, soc = 75.0, chargingPower = 0.0), car)
        Thread.sleep(SETTLE)
        assertEquals(1, repo.getAllSessions().first().size)
    }

    // ── SoC persistence ───────────────────────────────────────────────────────

    @Test fun carOffPacketsDoNotOverwriteShutdownBaseline() = runBlocking {
        // Baseline captured while the car was last on (e.g. 60 %).
        writeShutdownState(soc = 60.0f, tsOffsetMs = -60_000L)

        // An off-state packet arrives showing a higher SoC (mid-charge). The
        // baseline must remain unchanged so the next wake-up can compute the
        // correct delta and reconstruct the session.
        repo.onTelemetry(
            telemetry(
                gear = "P", speed = 0.0, enginePower = 0,
                chargingPower = 0.0, carOn = 0, soc = 72.0, battery12vVoltage = 12.4
            ),
            car
        )
        assertEquals(60.0f, repoPrefs().getFloat(KEY_SOC, -1f), 0.1f)
    }

    @Test fun shutdownStatePersistsOnCarOnPackets() = runBlocking {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        // On the first carOn packet, persistShutdownState runs inside the reconstruction
        // coroutine (after the reconstruction check completes), so we must wait for it.
        repo.onTelemetry(
            telemetry(
                gear = "P", speed = 0.0, enginePower = 0,
                chargingPower = 0.0, carOn = 1, soc = 72.0
            ),
            car
        )
        Thread.sleep(SETTLE)
        assertEquals(72.0f, repoPrefs().getFloat(KEY_SOC, -1f), 0.1f)
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    @Test fun deleteSessionRemovesFromDatabase() = runBlocking {
        writeShutdownState(soc = 50.0f, tsOffsetMs = -3_600_000L)
        repo.onTelemetry(gen.generateAcChargingTelemetry(carOn = 1, soc = 75.0, chargingPower = 0.0), car)
        Thread.sleep(SETTLE)
        val id = repo.getAllSessions().first().first().id
        repo.deleteSession(id)
        Thread.sleep(SETTLE)
        assertTrue(repo.getAllSessions().first().isEmpty())
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun writeShutdownState(soc: Float, tsOffsetMs: Long) {
        repoPrefs().edit()
            .putFloat(KEY_SOC,     soc)
            .putLong(KEY_TS,       System.currentTimeMillis() + tsOffsetMs)
            .putFloat(KEY_TEMP,    22.0f)
            .putInt(KEY_VOLTAGE,   450)
            .commit()
    }

    private fun repoPrefs(): SharedPreferences =
        ChargingRepository::class.java.getDeclaredField("prefs")
            .also { it.isAccessible = true }
            .get(repo) as SharedPreferences
}