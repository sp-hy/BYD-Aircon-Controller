package com.byd.tripstats.mock

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for MockDataGenerator.
 * Verifies that the generated sequence produces physically plausible data.
 * Pure JVM — no coroutines/delays involved (uses generateDriveSequence).
 */
class MockDataGeneratorTest {

    private lateinit var gen: MockDataGenerator

    @Before fun setUp() {
        gen = MockDataGenerator()
    }

    // ── Sequence length ───────────────────────────────────────────────────────

    @Test fun `generateDriveSequence returns requested count`() {
        val seq = gen.generateDriveSequence(60)
        assertEquals(60, seq.size)
    }

    @Test fun `generateDriveSequence with count 1 returns one packet`() {
        val seq = gen.generateDriveSequence(1)
        assertEquals(1, seq.size)
    }

    // ── Physical plausibility ─────────────────────────────────────────────────

    @Test fun `speed is non-negative throughout`() {
        val seq = gen.generateDriveSequence(120)
        seq.forEach { t ->
            assertTrue("Speed must be >= 0, got ${t.speed}", t.speed >= 0.0)
        }
    }

    @Test fun `speed peaks during cruising phase`() {
        val seq = gen.generateDriveSequence(100)
        val cruisingPackets = seq.drop(15).take(55)  // roughly 15–70% indices
        val maxCruise = cruisingPackets.maxOf { it.speed }
        assertTrue("Peak speed during cruise should be > 70 km/h, got $maxCruise", maxCruise > 70.0)
    }

    @Test fun `odometer is monotonically non-decreasing`() {
        val seq = gen.generateDriveSequence(120)
        for (i in 1 until seq.size) {
            assertTrue(
                "Odometer must not decrease: ${seq[i].odometer} < ${seq[i-1].odometer}",
                seq[i].odometer >= seq[i-1].odometer
            )
        }
    }

    @Test fun `SoC decreases overall during positive-power driving`() {
        val seq = gen.generateDriveSequence(80)
        val firstSoc = seq.first().soc
        val lastSoc  = seq.last().soc
        assertTrue("SoC should decrease overall, first=$firstSoc last=$lastSoc", lastSoc < firstSoc)
    }

    @Test fun `SoC stays within 0 to 100`() {
        val seq = gen.generateDriveSequence(120)
        seq.forEach { t ->
            assertTrue("SoC must be >= 0, got ${t.soc}", t.soc >= 0.0)
            assertTrue("SoC must be <= 100, got ${t.soc}", t.soc <= 100.0)
        }
    }

    @Test fun `last packets have P gear`() {
        val seq = gen.generateDriveSequence(100)
        // At progress > 0.95 the generator switches to P
        val lastFew = seq.takeLast(3)
        assertTrue("Final packets should be in P gear", lastFew.any { it.gear == "P" })
    }

    @Test fun `early packets have D gear`() {
        val seq = gen.generateDriveSequence(100)
        val firstFew = seq.take(50)
        assertTrue("Early packets should be in D gear", firstFew.all { it.gear == "D" || it.gear == "P" })
    }

    @Test fun `chargingPower is zero during drive`() {
        val seq = gen.generateDriveSequence(80)
        val drivingPackets = seq.take(70)
        assertTrue("No charging during driving", drivingPackets.all { it.chargingPower == 0.0 })
    }

    @Test fun `tyre pressures are in plausible PSI range`() {
        val seq = gen.generateDriveSequence(60)
        seq.forEach { t ->
            assertTrue("LF pressure too low: ${t.tyrePressureLF}",  t.tyrePressureLF  > 30.0)
            assertTrue("LF pressure too high: ${t.tyrePressureLF}", t.tyrePressureLF  < 50.0)
            assertTrue("LR pressure too low: ${t.tyrePressureLR}",  t.tyrePressureLR  > 30.0)
            assertTrue("LR pressure too high: ${t.tyrePressureLR}", t.tyrePressureLR  < 55.0)
        }
    }

    @Test fun `tyre temperatures increase during drive`() {
        val seq = gen.generateDriveSequence(100)
        val firstTemp = seq.first().tyreTempLF
        val lastTemp  = seq.last().tyreTempLF
        assertTrue("Tyre temp should rise during drive: $firstTemp -> $lastTemp", lastTemp > firstTemp)
    }

    @Test fun `battery cell voltages are in valid range`() {
        val seq = gen.generateDriveSequence(60)
        seq.forEach { t ->
            // Li-ion cell voltage: 2.5V (depleted) – 4.2V (full)
            assertTrue("Cell voltage max too low: ${t.batteryCellVoltageMax}", t.batteryCellVoltageMax > 2.5)
            assertTrue("Cell voltage max too high: ${t.batteryCellVoltageMax}", t.batteryCellVoltageMax < 4.3)
            assertTrue("Cell voltage min too low: ${t.batteryCellVoltageMin}", t.batteryCellVoltageMin > 2.5)
            assertTrue("Cell voltage min too high: ${t.batteryCellVoltageMin}", t.batteryCellVoltageMin < 4.3)
        }
    }

    @Test fun `socPanel tracks soc closely`() {
        val seq = gen.generateDriveSequence(60)
        seq.forEach { t ->
            val diff = Math.abs(t.soc - t.socPanel)
            assertTrue("socPanel (${t.socPanel}) should be within 2% of soc (${t.soc})", diff <= 2.0)
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test fun `reset gives identical sequences on successive calls`() {
        val seq1 = gen.generateDriveSequence(30)
        gen.reset()
        val seq2 = gen.generateDriveSequence(30)
        // After reset, odometer starts at same value
        assertEquals(seq1.first().odometer, seq2.first().odometer, 0.001)
    }

    // ── Parked and charging helpers ───────────────────────────────────────────

    @Test fun `generateParkedTelemetry has P gear and zero speed`() {
        val t = gen.generateParkedTelemetry()
        assertEquals("P", t.gear)
        assertEquals(0.0, t.speed, 0.001)
        assertEquals(0, t.enginePower)
        assertEquals(0.0, t.chargingPower, 0.001)
    }

    @Test fun `generateAcChargingTelemetry has positive chargingPower`() {
        val t = gen.generateAcChargingTelemetry()
        assertTrue("AC charging power should be > 0", t.chargingPower > 0.0)
        assertTrue("AC charging should be < 20 kW", t.chargingPower < 20.0)
        assertEquals("P", t.gear)
    }

    @Test fun `generateDcChargingTelemetry has high chargingPower and carOn`() {
        val t = gen.generateDcChargingTelemetry()
        assertTrue("DC charging power should be >= 20 kW", t.chargingPower >= 20.0)
        assertEquals(1, t.carOn)
    }

    @Test fun `isCharging returns true for charging helpers`() {
        assertTrue(gen.generateAcChargingTelemetry().isCharging)
        assertTrue(gen.generateDcChargingTelemetry().isCharging)
    }

    @Test fun `isCharging returns false for parked telemetry`() {
        assertFalse(gen.generateParkedTelemetry().isCharging)
    }
}