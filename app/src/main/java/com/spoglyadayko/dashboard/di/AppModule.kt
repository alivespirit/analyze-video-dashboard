package com.spoglyadayko.dashboard.di

import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.preferences.SettingsStore
import com.spoglyadayko.dashboard.ui.gatecrossings.GateCrossingsViewModel
import com.spoglyadayko.dashboard.ui.monitoring.MonitoringViewModel
import com.spoglyadayko.dashboard.ui.overallstats.OverallStatsViewModel
import com.spoglyadayko.dashboard.ui.settings.SettingsViewModel
import com.spoglyadayko.dashboard.ui.today.TodayViewModel
import com.spoglyadayko.dashboard.ui.today.VideoDetailViewModel
import com.spoglyadayko.dashboard.ui.todaystats.TodayStatsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { SettingsStore(androidContext()) }

    single {
        val settingsStore = get<SettingsStore>()
        DashboardApi(baseUrlProvider = {
            runBlocking { settingsStore.serverUrl.first() }
        })
    }

    viewModel { TodayViewModel(get()) }
    viewModel { params -> VideoDetailViewModel(get(), params.get(), params.getOrNull()) }
    viewModel { TodayStatsViewModel(get()) }
    viewModel { OverallStatsViewModel(get()) }
    viewModel { params -> GateCrossingsViewModel(get(), params.getOrNull()) }
    viewModel { MonitoringViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
}
