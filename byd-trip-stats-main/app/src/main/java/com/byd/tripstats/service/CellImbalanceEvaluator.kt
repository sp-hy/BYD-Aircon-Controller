package com.byd.tripstats.service

/**
 * Pure decision logic for the battery cell-imbalance alert — no Android
 * dependencies, so it is unit-testable on the JVM. [CellImbalanceMonitor] owns
 * one instance and feeds it every telemetry tick, posting a notification when
 * [evaluate] returns true.
 *
 * The state machine deliberately avoids firing on a single noisy sample:
 *  - **Validity gate** — cells read 0.0 until the BMS device registers; those
 *    ticks are ignored (and do not break an in-progress streak).
 *  - **SoC guard** — a healthy pack naturally widens its spread near full
 *    (top-balancing) and near empty (voltage sag), so breaches outside
 *    [[SOC_GUARD_LOW], [SOC_GUARD_HIGH]] are ignored and reset the streak.
 *  - **Sustained breach** — the spread must stay at/above the threshold for
 *    [REQUIRED_STREAK] consecutive valid, in-band ticks before [evaluate] fires.
 *  - **One-shot with hysteresis** — [evaluate] returns true exactly once per
 *    episode; it re-arms only after the spread recovers below
 *    (threshold − [HYSTERESIS_V]), so a value hovering at the threshold neither
 *    re-fires nor flaps.
 */
class CellImbalanceEvaluator {

    /** Consecutive in-band ticks the spread has been at/above the threshold. */
    var breachStreak = 0
        private set

    /** True once an alert has fired this episode; cleared on full recovery. */
    var alerted = false
        private set

    /**
     * @return true on the single tick a new alert should be raised.
     */
    fun evaluate(
        enabled: Boolean,
        vMax: Double,
        vMin: Double,
        soc: Double,
        thresholdV: Double,
    ): Boolean {
        if (!enabled) {                                   // feature off — clean slate
            breachStreak = 0
            alerted = false
            return false
        }
        if (vMax <= 0.0 || vMin <= 0.0) return false      // BMS not reporting yet
        if (soc < SOC_GUARD_LOW || soc > SOC_GUARD_HIGH) {
            breachStreak = 0
            return false
        }

        val spread = (vMax - vMin).coerceAtLeast(0.0)
        if (spread >= thresholdV) {
            breachStreak++
            return if (breachStreak >= REQUIRED_STREAK && !alerted) {
                alerted = true
                true
            } else {
                false
            }
        }

        // Below threshold — break the streak; re-arm only on a full recovery so a
        // value sitting just under the threshold cannot retrigger.
        breachStreak = 0
        if (spread < thresholdV - HYSTERESIS_V) alerted = false
        return false
    }

    companion object {
        const val REQUIRED_STREAK = 5        // consecutive breaching ticks
        const val HYSTERESIS_V = 0.005       // 5 mV recovery margin
        const val SOC_GUARD_LOW = 5.0        // suppress below this SoC %
        const val SOC_GUARD_HIGH = 95.0      // suppress above this SoC %
    }
}
