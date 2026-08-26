package com.byd.tripstats.data.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

// Inline test helper — avoids cross-source-set dependency on TelemetryFactory
private fun telemetry(
    gear          : String = "P",
    speed         : Double = 0.0,
    enginePower   : Int    = 0,
    chargingPower : Double = 0.0,
    soc           : Double = 80.0,
    carOn         : Int    = 1,
    odometer      : Double = 1000.0,
    totalDischarge: Double = 500.0,
    socPanel      : Int    = 80,
    carLocked     : Int    = 0,
    anyDoorOpened : Int    = 0,
    tyrePressureLF: Double = 38.5,
    tyrePressureRF: Double = 38.5,
    tyrePressureLR: Double = 42.0,
    tyrePressureRR: Double = 42.0,
    battery12vVoltage      : Double = 13.4,
    batteryTotalVoltage   : Int    = 570,
    batteryCellVoltageMax : Double = 3.333,
    batteryCellVoltageMin : Double = 3.326,
    electricDrivingRangeKm: Int    = 400
): VehicleTelemetry = VehicleTelemetry(
    battery12vVoltage      = battery12vVoltage,
    batteryCellTempMax     = 25,
    batteryCellVoltageMax  = batteryCellVoltageMax,
    batteryCellTempMin     = 22,
    batteryCellVoltageMin  = batteryCellVoltageMin,
    currentDatetime        = Instant.now().toString(),
    odometer               = odometer,
    soc                    = soc,
    soh                    = 98,
    locationAltitude       = 50.0,
    chargingPower          = chargingPower,
    enginePower            = enginePower,
    engineSpeedFront       = (speed * 85).toInt(),
    gear                   = gear,
    locationLatitude       = 37.9838,
    locationLongitude      = 23.7275,
    engineSpeedRear        = (speed * 100).toInt(),
    speed                  = speed,
    wifiSsid               = "",
    batteryTotalVoltage    = batteryTotalVoltage,
    electricDrivingRangeKm = electricDrivingRangeKm,
    totalDischarge         = totalDischarge,
    carOn                  = carOn,
    tyrePressureLF         = tyrePressureLF,
    tyrePressureRF         = tyrePressureRF,
    tyrePressureLR         = tyrePressureLR,
    tyrePressureRR         = tyrePressureRR,
    socPanel               = socPanel,
    carLocked              = carLocked,
    anyDoorOpened          = anyDoorOpened
)


/**
 * Tests for VehicleTelemetry computed properties and toRawJson().
 * Pure JVM — no Android context required.
 */
class VehicleTelemetryTest {

    // ── isCarOn ───────────────────────────────────────────────────────────────

    @Test fun `isCarOn is true when carOn equals 1 or 2`() {
        assertTrue(telemetry(carOn = 1).isCarOn)
        assertTrue(telemetry(carOn = 2).isCarOn)
    }

    @Test fun `isCarOn is false when carOn equals 0`() {
        assertFalse(telemetry(carOn = 0, battery12vVoltage = 12.4).isCarOn)
    }

    @Test fun `isCarOn is false for negative values`() {
        assertFalse(telemetry(carOn = -1, battery12vVoltage = 12.4).isCarOn)
    }

    @Test fun `isCarOn is true when drivetrain signals imply the car is active`() {
        assertTrue(telemetry(carOn = 0, gear = "D", speed = 1.0).isCarOn)
        assertTrue(telemetry(carOn = 0, speed = 3.0).isCarOn)
        assertTrue(telemetry(carOn = 0, enginePower = 6).isCarOn)
    }

    @Test fun `isCarOn is true for D or R gear regardless of speed`() {
        // Car stopped at red light — segment must not reset
        assertTrue(telemetry(carOn = 0, gear = "D", speed = 0.0, enginePower = 0).isCarOn)
        assertTrue(telemetry(carOn = 0, gear = "R", speed = 0.0, enginePower = 0).isCarOn)
    }

    @Test fun `isCarOn is false when only twelve volt rail is alive`() {
        assertFalse(telemetry(carOn = 0, gear = "P", battery12vVoltage = 13.7).isCarOn)
        assertFalse(telemetry(carOn = 0, gear = "N", battery12vVoltage = 13.7).isCarOn)
    }

    @Test fun `isAwake is true only for carOn equals 1`() {
        assertTrue(telemetry(carOn = 1).isAwake)
        assertFalse(telemetry(carOn = 0).isAwake)
        assertFalse(telemetry(carOn = 2).isAwake)
    }

    @Test fun `isReadyToDrive is true only for carOn equals 2`() {
        assertTrue(telemetry(carOn = 2).isReadyToDrive)
        assertFalse(telemetry(carOn = 0).isReadyToDrive)
        assertFalse(telemetry(carOn = 1).isReadyToDrive)
    }

    @Test fun `unknown drive mode is displayed as unknown`() {
        assertEquals("Unknown", telemetry().copy(driveMode = 0).driveModeName)
    }

    // ── isCharging ────────────────────────────────────────────────────────────

    @Test fun `isCharging is true when chargingPower is positive`() {
        assertTrue(telemetry(chargingPower = 7.2).isCharging)
        assertTrue(telemetry(chargingPower = 50.0).isCharging)
        assertTrue(telemetry(chargingPower = 0.001).isCharging)
    }

    @Test fun `isCharging is false when chargingPower is zero`() {
        assertFalse(telemetry(chargingPower = 0.0).isCharging)
    }

    @Test fun `isCharging is false when chargingPower is negative`() {
        assertFalse(telemetry(chargingPower = -1.0).isCharging)
    }

    // ── isRegenerating ────────────────────────────────────────────────────────

    @Test fun `isRegenerating is true when enginePower is below minus 1`() {
        assertTrue(telemetry(enginePower = -2).isRegenerating)
        assertTrue(telemetry(enginePower = -50).isRegenerating)
    }

    @Test fun `isRegenerating is false at exactly minus 1 (noise threshold)`() {
        assertFalse(telemetry(enginePower = -1).isRegenerating)
    }

    @Test fun `isRegenerating is false when power is positive`() {
        assertFalse(telemetry(enginePower = 10).isRegenerating)
    }

    @Test fun `isRegenerating is false when power is zero`() {
        assertFalse(telemetry(enginePower = 0).isRegenerating)
    }

    // ── isDriving / isMoving / isParked ───────────────────────────────────────

    @Test fun `isDriving is true for D gear`() {
        assertTrue(telemetry(gear = "D").isDriving)
    }

    @Test fun `isDriving is true for R gear`() {
        assertTrue(telemetry(gear = "R").isDriving)
    }

    @Test fun `isDriving is false for P gear`() {
        assertFalse(telemetry(gear = "P").isDriving)
    }

    @Test fun `isDriving is false for N gear`() {
        assertFalse(telemetry(gear = "N").isDriving)
    }

    @Test fun `isMoving requires D or R gear AND positive speed`() {
        assertTrue(telemetry(gear = "D", speed = 30.0).isMoving)
        assertTrue(telemetry(gear = "R", speed = 5.0).isMoving)
        assertFalse(telemetry(gear = "D", speed = 0.0).isMoving)  // stationary in D
        assertFalse(telemetry(gear = "P", speed = 5.0).isMoving)  // impossible but defensive
    }

    @Test fun `isParked is true only for P gear`() {
        assertTrue(telemetry(gear = "P").isParked)
        assertFalse(telemetry(gear = "D").isParked)
        assertFalse(telemetry(gear = "N").isParked)
    }

    // ── batteryTempAvg ────────────────────────────────────────────────────────

    @Test fun `batteryTempAvg is mean of min and max cell temperature`() {
        val t = telemetry().copy(batteryCellTempMax = 30, batteryCellTempMin = 20)
        assertEquals(25.0, t.batteryTempAvg, 0.001)
    }

    @Test fun `batteryTempAvg is exact for equal temps`() {
        val t = telemetry().copy(batteryCellTempMax = 22, batteryCellTempMin = 22)
        assertEquals(22.0, t.batteryTempAvg, 0.001)
    }

    // ── toRawJson ─────────────────────────────────────────────────────────────

    @Test fun `toRawJson returns empty object for BEV defaults`() {
        // All rawJson fields are at their defaults — result should be minimal
        val t = telemetry(chargingPower = 0.0)
        assertEquals("{}", t.toRawJson(isPhev = false))
    }

    @Test fun `toRawJson includes chargingPower when charging`() {
        val t = telemetry(chargingPower = 7.2)
        val json = t.toRawJson(isPhev = false)
        assertTrue("Expected charging_power in json", json.contains("\"charging_power\":7.2"))
    }

    @Test fun `toRawJson excludes PHEV fields when isPhev is false`() {
        // Manually construct with PHEV values non-zero
        val t = telemetry().copy(
            fuelPercentage = 50,
            fuelDrivingRangeKm = 200
        )
        val json = t.toRawJson(isPhev = false)
        assertFalse("fuel_percentage should not appear for BEV", json.contains("fuel_percentage"))
        assertFalse("fuel_driving_range_km should not appear for BEV", json.contains("fuel_driving_range_km"))
    }

    @Test fun `toRawJson includes PHEV fields when isPhev is true and values non-zero`() {
        val t = telemetry().copy(fuelPercentage = 50, fuelDrivingRangeKm = 200)
        val json = t.toRawJson(isPhev = true)
        assertTrue(json.contains("\"fuel_percentage\":50"))
        assertTrue(json.contains("\"fuel_driving_range_km\":200"))
    }

    @Test fun `toRawJson excludes PHEV fields even when isPhev true but values are zero`() {
        val t = telemetry().copy(fuelPercentage = 0, fuelDrivingRangeKm = 0)
        val json = t.toRawJson(isPhev = true)
        assertFalse(json.contains("fuel_percentage"))
    }

    @Test fun `toRawJson includes wifiSsid when non-blank`() {
        val t = telemetry().copy(wifiSsid = "HomeNetwork")
        val json = t.toRawJson()
        assertTrue(json.contains("\"wifi_ssid\":\"HomeNetwork\""))
    }

    @Test fun `toRawJson produces valid JSON structure`() {
        val t = telemetry(chargingPower = 7.2).copy(wifiSsid = "Test")
        val json = t.toRawJson()
        assertTrue("Should start with {", json.startsWith("{"))
        assertTrue("Should end with }", json.endsWith("}"))
        // Both fields present — check comma-separated structure
        assertTrue(json.contains(","))
    }
}
