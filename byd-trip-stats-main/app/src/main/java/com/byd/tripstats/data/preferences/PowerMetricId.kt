package com.byd.tripstats.data.preferences

/**
 * Stable identifiers for the always-visible power-metric tiles (top row of the CARDS
 * layout). Reorderable but never hideable. Persisted order (see
 * `PreferencesManager.dashboardPowerOrder`) is keyed by [name], so these must not be renamed.
 */
enum class PowerMetricId {
    POWER,
    SPEED,
    SOC,
    RANGE,
    DISTANCE;

    companion object {
        val DEFAULT_ORDER: List<PowerMetricId> = entries.toList()

        fun fromNameOrNull(name: String): PowerMetricId? =
            entries.firstOrNull { it.name == name }

        /** Keep the saved order, drop unknowns, and append any newly-added tiles. */
        fun parseOrder(csv: String?): List<PowerMetricId> {
            if (csv.isNullOrBlank()) return DEFAULT_ORDER
            val saved = csv.split(',').mapNotNull { fromNameOrNull(it.trim()) }.distinct()
            val missing = DEFAULT_ORDER.filter { it !in saved }
            return saved + missing
        }
    }
}
