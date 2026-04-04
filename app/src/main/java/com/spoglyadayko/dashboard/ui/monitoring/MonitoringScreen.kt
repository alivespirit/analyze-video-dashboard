package com.spoglyadayko.dashboard.ui.monitoring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spoglyadayko.dashboard.data.api.LedgerEntry
import com.spoglyadayko.dashboard.data.api.MasterStats
import com.spoglyadayko.dashboard.data.api.TeslaStats
import com.spoglyadayko.dashboard.data.api.WorkerStats
import com.spoglyadayko.dashboard.ui.theme.fmt
import com.spoglyadayko.dashboard.ui.theme.statusColor
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(viewModel: MonitoringViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.error != null && state.data == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                    }
                }
            }
            state.data != null -> {
                val data = state.data!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { MasterCard(data.master) }
                    if (data.worker != null) {
                        item { WorkerCard(data.worker) }
                    }
                    if (data.tesla != null) {
                        item { TeslaCard(data.tesla) }
                    }
                    if (data.ledgerRecent.isNotEmpty()) {
                        item {
                            Text(
                                "Recent processing",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        items(data.ledgerRecent) { entry ->
                            LedgerEntryRow(entry)
                        }
                    }

                    if (state.error != null) {
                        item {
                            Text(
                                "Update error: ${state.error}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            !state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MasterCard(master: MasterStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Master", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            StatRow(icon = Icons.Default.Memory, label = "CPU", value = "${master.cpuPercent.fmt("%.1f")}%")
            LinearProgressIndicator(
                progress = { (master.cpuPercent / 100.0).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
            )

            StatRow(
                icon = Icons.Default.Storage,
                label = "RAM",
                value = "${master.memoryUsedMb}/${master.memoryTotalMb} MB (${master.memoryPercent.fmt("%.1f")}%)",
            )
            LinearProgressIndicator(
                progress = { (master.memoryPercent / 100.0).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.outline,
            )

            if (master.batteryPercent != null) {
                val batteryIcon = when {
                    master.batteryPlugged == true -> Icons.Default.BatteryChargingFull
                    master.batteryPercent > 50 -> Icons.Default.BatteryFull
                    master.batteryPercent > 20 -> Icons.Default.Battery3Bar
                    else -> Icons.Default.Battery1Bar
                }
                val timeLeft = master.batteryTimeLeftS?.let {
                    val h = it / 3600
                    val m = (it % 3600) / 60
                    "${h}h${m}m"
                }
                val pluggedStr = if (master.batteryPlugged == true) " (plugged)" else ""
                val timeStr = timeLeft?.let { " \u2014 $it left" } ?: ""
                StatRow(
                    icon = batteryIcon,
                    label = "Battery",
                    value = "${master.batteryPercent.fmt("%.0f")}%$pluggedStr$timeStr",
                )
                LinearProgressIndicator(
                    progress = { (master.batteryPercent / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = when {
                        master.batteryPercent > 50 -> MaterialTheme.colorScheme.secondary
                        master.batteryPercent > 20 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun WorkerCard(worker: WorkerStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Worker", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = if (worker.status == "ok") MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        worker.status ?: "unknown",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (worker.activeTasks != null && worker.maxTasks != null) {
                StatRow(
                    icon = Icons.Default.PlayArrow,
                    label = "Tasks",
                    value = "${worker.activeTasks}/${worker.maxTasks}",
                )
            }

            if (worker.loadAvg1m != null) {
                StatRow(
                    icon = Icons.Default.Speed,
                    label = "Load avg",
                    value = "${worker.loadAvg1m.fmt("%.2f")} / ${(worker.loadAvg5m ?: 0.0).fmt("%.2f")} / ${(worker.loadAvg15m ?: 0.0).fmt("%.2f")}",
                )
            }

            if (worker.cpuTempC != null) {
                StatRow(
                    icon = Icons.Default.Thermostat,
                    label = "CPU temp",
                    value = "${worker.cpuTempC.fmt("%.1f")}\u00B0C",
                )
            }

            if (worker.memoryPercent != null) {
                StatRow(
                    icon = Icons.Default.Storage,
                    label = "RAM",
                    value = "${worker.memoryUsedMb ?: 0}/${worker.memoryTotalMb ?: 0} MB (${worker.memoryPercent.fmt("%.1f")}%)",
                )
                LinearProgressIndicator(
                    progress = { (worker.memoryPercent / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.outline,
                )
            }

            if (worker.batteryPercent != null) {
                StatRow(
                    icon = Icons.Default.BatteryFull,
                    label = "Battery",
                    value = "${worker.batteryPercent.fmt("%.0f")}%",
                )
                LinearProgressIndicator(
                    progress = { (worker.batteryPercent / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = when {
                        worker.batteryPercent > 50 -> MaterialTheme.colorScheme.secondary
                        worker.batteryPercent > 20 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun TeslaCard(tesla: TeslaStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Tesla", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            val timeStr = tesla.fetchedTs.substringAfterLast(" ").substringBeforeLast(":")
            StatRow(
                icon = Icons.Default.ElectricCar,
                label = "Battery",
                value = "${tesla.batteryPercent}% (updated at $timeStr)",
            )
            LinearProgressIndicator(
                progress = { tesla.batteryPercent / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = when {
                    tesla.batteryPercent > 50 -> MaterialTheme.colorScheme.secondary
                    tesla.batteryPercent > 30 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                },
                trackColor = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun StatRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LedgerEntryRow(entry: LedgerEntry) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = statusColor(entry.status),
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier.size(8.dp),
            ) {}

            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    entry.file,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        entry.status ?: "?",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entry.telegramStatus?.let {
                        Text(
                            "tg:$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val tsStr = entry.endTs?.let { formatTimestamp(it) }
                ?: entry.startTs?.let { formatTimestamp(it) }
            if (tsStr != null) {
                Text(
                    tsStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatTimestamp(ts: Double): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        sdf.format(Date((ts * 1000).toLong()))
    } catch (e: Exception) {
        ""
    }
}
