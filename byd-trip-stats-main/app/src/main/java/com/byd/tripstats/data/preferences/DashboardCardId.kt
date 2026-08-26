package com.byd.tripstats.data.preferences

/**
 * Stable identifiers for the customisable middle-section cards of the [DashboardLayout.CARDS]
 * layout. The persisted order/visibility (see `PreferencesManager.dashboardCardOrder` /
 * `dashboardHiddenCards`) is keyed by [name], so these constants must never be renamed.
 */
enum class DashboardCardId {
    BATTERY,
    ENVIRONMENT,
    HV_12V,
    MOTORS,
    DRIVING,
    TYRES,
    ODOMETER,
    DISCHARGE;

    companion object {
        /**
         * Default order. In the split CARDS layout indices 0–3 fill the left 2×2 flank
         * (row-major) and 4–7 the right flank, so this lays out as:
         *   left:  Battery / Motors   ·   HV / Tyres
         *   right: Environment / Driving · Odometer / Discharge
         */
        val DEFAULT_ORDER: List<DashboardCardId> = listOf(
            BATTERY, MOTORS, HV_12V, TYRES,
            ENVIRONMENT, DRIVING, ODOMETER, DISCHARGE,
        )

        fun fromNameOrNull(name: String): DashboardCardId? =
            entries.firstOrNull { it.name == name }

        /**
         * Reconcile a persisted order string with the current enum: keep the saved order,
         * drop unknown ids, and append any cards added in newer versions so they are never
         * silently missing.
         */
        fun parseOrder(csv: String?): List<DashboardCardId> {
            if (csv.isNullOrBlank()) return DEFAULT_ORDER
            val saved = csv.split(',').mapNotNull { fromNameOrNull(it.trim()) }.distinct()
            val missing = DEFAULT_ORDER.filter { it !in saved }
            return saved + missing
        }

        fun parseHidden(csv: String?): Set<DashboardCardId> =
            csv?.split(',')?.mapNotNull { fromNameOrNull(it.trim()) }?.toSet() ?: emptySet()
    }
}
