package com.byd.tripstats.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardViewModelSeasonTest {

    @Test
    fun `april maps to autumn for Australia`() {
        val hemisphere = DashboardViewModel.hemisphereForCountry("AU")
        val season = DashboardViewModel.seasonForMonth(month = 4, hemisphere = hemisphere)

        assertEquals(DashboardViewModel.Season.AUTUMN, season)
    }

    @Test
    fun `april maps to spring for Greece`() {
        val hemisphere = DashboardViewModel.hemisphereForCountry("GR")
        val season = DashboardViewModel.seasonForMonth(month = 4, hemisphere = hemisphere)

        assertEquals(DashboardViewModel.Season.SPRING, season)
    }

    @Test
    fun `january maps to summer for Australia`() {
        val hemisphere = DashboardViewModel.hemisphereForCountry("AU")
        val season = DashboardViewModel.seasonForMonth(month = 1, hemisphere = hemisphere)

        assertEquals(DashboardViewModel.Season.SUMMER, season)
    }
}
