package com.spoglyadayko.dashboard.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spoglyadayko.dashboard.data.preferences.SettingsStore
import com.spoglyadayko.dashboard.service.EventPollService
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // Server URL
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = { viewModel.setServerUrl(it) },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
        )

        // Poll interval
        OutlinedTextField(
            value = state.pollIntervalSeconds.toString(),
            onValueChange = { it.toIntOrNull()?.let { v -> viewModel.setPollInterval(v.coerceIn(10, 300)) } },
            label = { Text("Poll interval (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )

        // Notifications toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Away/Back notifications")
            Switch(
                checked = state.notificationsEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setNotificationsEnabled(enabled)
                    val intent = Intent(context, EventPollService::class.java)
                    if (enabled) {
                        context.startForegroundService(intent)
                    } else {
                        context.stopService(intent)
                    }
                },
            )
        }

        Text(
            "Notifications will show current Home/Away status and alert on changes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        // Theme selector
        Text("Theme", style = MaterialTheme.typography.titleSmall)
        val themeOptions = listOf(
            SettingsStore.THEME_AUTO to "Auto (system)",
            SettingsStore.THEME_LIGHT to "Light",
            SettingsStore.THEME_DARK to "Dark",
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            themeOptions.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = state.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}
