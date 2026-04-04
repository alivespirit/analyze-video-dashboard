package com.spoglyadayko.dashboard.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DaysResponse(
    val days: List<String>,
)

@Serializable
data class TodayVideosResponse(
    val day: String,
    val videos: List<VideoSummary>,
)

@Serializable
data class VideoSummary(
    val basename: String,
    val time: String? = null,
    val status: String? = null,
    @SerialName("gate_direction") val gateDirection: String? = null,
    @SerialName("processing_time_s") val processingTimeS: Double? = null,
    @SerialName("raw_events") val rawEvents: Int? = null,
    @SerialName("fast_processing") val fastProcessing: Boolean = false,
    val worker: Boolean = false,
    @SerialName("reid_matched") val reidMatched: Boolean? = null,
    @SerialName("reid_score") val reidScore: Double? = null,
    @SerialName("reid_neg") val reidNeg: Double? = null,
    @SerialName("away_back") val awayBack: String? = null,
    @SerialName("has_frames") val hasFrames: Boolean = false,
)

@Serializable
data class VideoLogsResponse(
    val basename: String,
    val entries: List<LogEntry>,
)

@Serializable
data class LogEntry(
    val ts: String,
    val level: String,
    val content: String,
    val worker: Boolean = false,
)

@Serializable
data class VideoHighlightResponse(
    val basename: String,
    @SerialName("highlight_url") val highlightUrl: String? = null,
)

@Serializable
data class VideoFramesResponse(
    val basename: String,
    val frames: List<String>,
)

@Serializable
data class ReidCropsResponse(
    val basename: String,
    val crops: List<String>,
)

@Serializable
data class TodayStatsResponse(
    val day: String,
    @SerialName("videos_total") val videosTotal: Int,
    @SerialName("status_counts") val statusCounts: Map<String, Int>,
    @SerialName("gate_counts") val gateCounts: Map<String, Int>,
    @SerialName("processing_stats") val processingStats: Map<String, Double>,
    @SerialName("processing_chart") val processingChart: List<ChartEntry>,
    @SerialName("away_intervals") val awayIntervals: List<AwayInterval>,
)

@Serializable
data class ChartEntry(
    val basename: String,
    val time: String? = null,
    val seconds: Double,
    val status: String? = null,
)

@Serializable
data class AwayInterval(
    val start: String? = null,
    val end: String? = null,
    val dur: String? = null,
)

@Serializable
data class OverallStatsResponse(
    @SerialName("per_day") val perDay: List<DayStats>,
    @SerialName("events_heatmap") val eventsHeatmap: EventsHeatmap,
    @SerialName("weekday_heatmap") val weekdayHeatmap: WeekdayHeatmap,
)

@Serializable
data class DayStats(
    val day: String,
    @SerialName("videos_total") val videosTotal: Int,
    @SerialName("status_counts") val statusCounts: Map<String, Int>,
    @SerialName("md_avg") val mdAvg: Double? = null,
    @SerialName("full_avg") val fullAvg: Double? = null,
)

@Serializable
data class EventsHeatmap(
    @SerialName("start_offset") val startOffset: Int,
    @SerialName("bin_minutes") val binMinutes: Int,
    val bins: Int,
    @SerialName("days_count") val daysCount: Int,
    @SerialName("away_days") val awayDays: List<Int>,
    @SerialName("back_days") val backDays: List<Int>,
)

@Serializable
data class WeekdayHeatmap(
    @SerialName("start_offset") val startOffset: Int,
    @SerialName("bin_minutes") val binMinutes: Int,
    val bins: Int,
    @SerialName("weekday_labels") val weekdayLabels: List<String>,
    @SerialName("weekday_day_counts") val weekdayDayCounts: List<Int>,
    @SerialName("away_counts") val awayCounts: List<List<Int>>,
    @SerialName("back_counts") val backCounts: List<List<Int>>,
)

@Serializable
data class MonitoringResponse(
    val master: MasterStats,
    val worker: WorkerStats? = null,
    val tesla: TeslaStats? = null,
    @SerialName("ledger_recent") val ledgerRecent: List<LedgerEntry>,
)

@Serializable
data class TeslaStats(
    @SerialName("battery_percent") val batteryPercent: Int,
    @SerialName("fetched_ts") val fetchedTs: String,
)

@Serializable
data class MasterStats(
    @SerialName("cpu_percent") val cpuPercent: Double,
    @SerialName("memory_percent") val memoryPercent: Double,
    @SerialName("memory_used_mb") val memoryUsedMb: Long,
    @SerialName("memory_total_mb") val memoryTotalMb: Long,
    @SerialName("battery_percent") val batteryPercent: Double? = null,
    @SerialName("battery_plugged") val batteryPlugged: Boolean? = null,
    @SerialName("battery_time_left_s") val batteryTimeLeftS: Long? = null,
)

@Serializable
data class WorkerStats(
    val status: String? = null,
    @SerialName("active_tasks") val activeTasks: Int? = null,
    @SerialName("max_tasks") val maxTasks: Int? = null,
    @SerialName("battery_percent") val batteryPercent: Double? = null,
    @SerialName("load_avg_1m") val loadAvg1m: Double? = null,
    @SerialName("load_avg_5m") val loadAvg5m: Double? = null,
    @SerialName("load_avg_15m") val loadAvg15m: Double? = null,
    @SerialName("memory_percent") val memoryPercent: Double? = null,
    @SerialName("memory_used_mb") val memoryUsedMb: Long? = null,
    @SerialName("memory_total_mb") val memoryTotalMb: Long? = null,
    @SerialName("cpu_temp_c") val cpuTempC: Double? = null,
)

@Serializable
data class LedgerEntry(
    val file: String,
    val status: String? = null,
    @SerialName("detected_ts") val detectedTs: Double? = null,
    @SerialName("start_ts") val startTs: Double? = null,
    @SerialName("end_ts") val endTs: Double? = null,
    @SerialName("fast_processing") val fastProcessing: Boolean? = null,
    @SerialName("telegram_status") val telegramStatus: String? = null,
)

@Serializable
data class EventsLatestResponse(
    val events: List<EventEntry>,
    @SerialName("current_status") val currentStatus: String? = null,
    @SerialName("current_status_since") val currentStatusSince: String? = null,
    @SerialName("server_ts") val serverTs: String,
)

@Serializable
data class EventEntry(
    val type: String,
    val video: String,
    val ts: String? = null,
    val hhmmss: String? = null,
)

@Serializable
data class ReidCopyRequest(
    @SerialName("image_path") val imagePath: String,
    val target: String,
)

@Serializable
data class ReidCopyResponse(
    val status: String,
    val destination: String? = null,
)
