package com.byd.tripstats.data.analysis

import com.byd.tripstats.data.config.CarConfig
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import kotlin.math.abs

// ── Physical constants ────────────────────────────────────────────────────────

private const val GRAVITY_MS2        = 9.80665        // m/s²
private const val JOULES_PER_KWH     = 3_600_000.0    // J/kWh
private const val AIR_DENSITY_KGM3   = 1.225          // kg/m³ at sea level, 15°C
private const val CRR_DEFAULT        = 0.0074         // rolling resistance coeff — BYD LFP EVs on tarmac
                                                       // (ISO 28580 for premium EV tyres: 0.007–0.008;
                                                       //  0.0074 back-calculated from 501 km highway trip)
private const val DRIVETRAIN_ETA     = 0.88           // motor + inverter round-trip efficiency
                                                       // Battery energy = mechanical work / η.
                                                       // Scales modelled forces to battery-equivalent kWh so
                                                       // the residual (drivetrain losses) represents only truly
                                                       // unexplained energy (12 V aux, GPS noise, HVAC, etc.).

// ── Data class ────────────────────────────────────────────────────────────────

/**
 * Physics-based breakdown of trip energy consumption into its main contributors.
 *
 * All energy values are in kWh, signed where direction matters.
 *
 * The model accounts for:
 *  - Gradient (slope resistance) — climb cost vs. downhill recovery
 *  - Rolling resistance — proportional to mass × distance
 *  - Aerodynamic drag — proportional to CdA × speed²
 *  - Acceleration (kinetic energy changes) — net signed; negative = regen recovered
 *  - Drivetrain losses — measured residual after all modelled forces
 *
 * Gradient, rolling, aero, and kinetic estimates require [estimatedKerbMassKg].
 * Aero additionally requires [cdA].
 *
 * HVAC is intentionally excluded: the BYD DiLink firmware does not expose a
 * reliable real-time HVAC power signal. The compressor mode bit stays active
 * during low-load modulation and the refrigerant line temperature lags too
 * far behind actual draw to produce a trustworthy per-trip estimate.
 */
data class TripEnergyBreakdown(
    val totalConsumedKwh: Double,

    // ── Gradient ─────────────────────────────────────────────────────────────
    /** Energy spent climbing (always ≥ 0). */
    val climbKwh: Double,
    /** Potential energy available from descents (always ≥ 0). */
    val descentKwh: Double,
    /** Net gradient effect = climbKwh − descentKwh. Positive = net uphill trip. */
    val netGradientKwh: Double,
    /** Number of intervals that contributed a valid altitude delta. */
    val gradientSamples: Int,

    // ── Rolling resistance ────────────────────────────────────────────────────
    /** Energy lost to tyre rolling resistance over the full trip distance. */
    val rollingResistanceKwh: Double,

    // ── Aerodynamic drag ─────────────────────────────────────────────────────
    /** Energy lost to air resistance, integrated from speed² × distance per interval. */
    val aeroDragKwh: Double,

    // ── Residual ─────────────────────────────────────────────────────────────
    /**
     * Unexplained battery energy after all modelled forces (already scaled by
     * drivetrain efficiency η=0.88). Represents auxiliary 12 V loads, HVAC,
     * GPS/altitude noise, and any remaining model inaccuracies.
     * Motor/inverter conversion losses are already accounted for via the η factor
     * applied to rolling, aero, and gradient terms.
     *
     * Kinetic energy (½mΔv²) is intentionally excluded: OBD speed data is too
     * coarse/noisy to accumulate ΔKE reliably over a full trip — the per-interval
     * signed sum drifts positive with speed jitter rather than cancelling to zero.
     */
    val drivetrainLossesKwh: Double,

    // ── Metadata ──────────────────────────────────────────────────────────────
    val estimatedKerbMassKg: Double? = null,
    val cdA: Double? = null
) {
    // ── Convenience flags ─────────────────────────────────────────────────────

    val hasGradientEstimate: Boolean get() = estimatedKerbMassKg != null && gradientSamples > 0
    val hasPhysicsBreakdown: Boolean get() = estimatedKerbMassKg != null
    val hasAeroEstimate: Boolean get() = cdA != null && hasPhysicsBreakdown

    // ── Share percentages (null when total is zero) ───────────────────────────

    private fun sharePct(kwh: Double): Double? =
        totalConsumedKwh.takeIf { it > 0.0 }?.let { (kwh / it) * 100.0 }

    val rollingSharePct: Double? get() = sharePct(rollingResistanceKwh)
    val aeroSharePct: Double? get() = sharePct(aeroDragKwh)
    val gradientSharePct: Double? get() = sharePct(netGradientKwh)
    val drivetrainSharePct: Double? get() = sharePct(drivetrainLossesKwh)
}

// ── Calculation ───────────────────────────────────────────────────────────────

fun calculateTripEnergyBreakdown(
    dataPoints: List<TripDataPointEntity>,
    carConfig: CarConfig?,
    totalEnergyConsumedKwh: Double? = null
): TripEnergyBreakdown? {
    if (dataPoints.size < 2) return null

    // Power-integrated net consumption as a floor. car frequently
    // under-reports totalDischarge (lumpy updates, stale zeros), which otherwise
    // collapses the residual (auxiliary losses) to 0.00 kWh. Power × dt is sampled
    // on every telemetry tick and is a more reliable lower bound.
    var tractionIntegratedKwh = 0.0
    var regenIntegratedKwh = 0.0
    dataPoints.zipWithNext { a, b ->
        val dtHours = (b.timestamp - a.timestamp).coerceAtLeast(0L) / 3_600_000.0
        if (dtHours in 0.0..(60.0 / 3600.0)) {
            if (a.power > 0.0) tractionIntegratedKwh += a.power * dtHours
            if (a.power < 0.0) regenIntegratedKwh    += -a.power * dtHours
        }
    }
    val powerIntegratedKwh = (tractionIntegratedKwh - regenIntegratedKwh).coerceAtLeast(0.0)

    val explicitTripTotalKwh = totalEnergyConsumedKwh
        ?.takeIf { it.isFinite() && it >= 0.0 }
    val reportedKwh = explicitTripTotalKwh
        ?: (dataPoints.last().totalDischarge - dataPoints.first().totalDischarge)
            .takeIf { it.isFinite() && it >= 0.0 }
        ?: 0.0
    val totalConsumed = explicitTripTotalKwh ?: maxOf(reportedKwh, powerIntegratedKwh)

    val massKg = carConfig?.estimatedKerbMassKg?.takeIf { it.isFinite() && it > 0.0 }
    val cdA    = carConfig?.cdA?.takeIf { it.isFinite() && it > 0.0 }

    var climbKwh              = 0.0
    var descentKwh            = 0.0
    var gradientSamples       = 0
    var rollingResistanceKwh  = 0.0
    var aeroDragKwh           = 0.0

    dataPoints.zipWithNext { a, b ->
        val dtHours = (b.timestamp - a.timestamp).coerceAtLeast(0L) / 3_600_000.0
        val dtSecs  = dtHours * 3600.0
        if (dtHours <= 0.0 || dtSecs <= 0.0) return@zipWithNext

        // Physics forces require vehicle mass
        val m = massKg ?: return@zipWithNext

        // Speed in m/s (data points store km/h)
        val vA = (a.speed / 3.6).coerceAtLeast(0.0)
        val vB = (b.speed / 3.6).coerceAtLeast(0.0)
        val vAvg = (vA + vB) / 2.0

        // Distance for this interval (m) — prefer odometer delta, fall back to speed×time
        val odomDeltaM = ((b.odometer - a.odometer) * 1000.0)
            .takeIf { it.isFinite() && it > 0.0 }
            ?: (vAvg * dtSecs)
        val distM = odomDeltaM.coerceAtLeast(0.0)
        if (distM <= 0.0) return@zipWithNext

        // ── Rolling resistance: F_rr = Crr × m × g; W = F × d ───────────────
        // Divided by η to convert wheel-side mechanical work to battery-draw equivalent.
        val rollingJ = CRR_DEFAULT * m * GRAVITY_MS2 * distM
        if (rollingJ.isFinite() && rollingJ > 0.0) {
            rollingResistanceKwh += rollingJ / JOULES_PER_KWH / DRIVETRAIN_ETA
        }

        // ── Aerodynamic drag: F_aero = ½ × ρ × CdA × v²; W = F × d ─────────
        if (cdA != null) {
            val aeroJ = 0.5 * AIR_DENSITY_KGM3 * cdA * vAvg * vAvg * distM
            if (aeroJ.isFinite() && aeroJ > 0.0) {
                aeroDragKwh += aeroJ / JOULES_PER_KWH / DRIVETRAIN_ETA
            }
        }

        // ── Gradient: W = m × g × Δh ─────────────────────────────────────────
        // Climbing draws from battery (÷η); descending recovers to battery (×η for regen).
        val dzM = b.altitude - a.altitude
        if (dzM.isFinite() && abs(dzM) >= 0.5) {
            val gradJ = m * GRAVITY_MS2 * abs(dzM)
            if (gradJ.isFinite() && gradJ > 0.0) {
                gradientSamples++
                if (dzM > 0.0) climbKwh   += gradJ / JOULES_PER_KWH / DRIVETRAIN_ETA
                else           descentKwh += gradJ / JOULES_PER_KWH * DRIVETRAIN_ETA
            }
        }

    }

    // ── Drivetrain losses = measured total minus all modelled forces ──────────
    // Net gradient: climb costs energy, descent recovers it.
    // Net kinetic: positive means net acceleration energy drawn from battery.
    // Negative drivetrainLosses would indicate model over-estimation; clamp to 0.
    val netGradientKwh = climbKwh - descentKwh
    val modelledKwh = rollingResistanceKwh + aeroDragKwh + netGradientKwh
    val drivetrainLossesKwh = (totalConsumed - modelledKwh).coerceAtLeast(0.0)

    return TripEnergyBreakdown(
        totalConsumedKwh      = totalConsumed,
        climbKwh              = climbKwh,
        descentKwh            = descentKwh,
        netGradientKwh        = netGradientKwh,
        gradientSamples       = gradientSamples,
        rollingResistanceKwh  = rollingResistanceKwh,
        aeroDragKwh           = aeroDragKwh,
        drivetrainLossesKwh   = drivetrainLossesKwh,
        estimatedKerbMassKg   = massKg,
        cdA                   = cdA
    )
}

