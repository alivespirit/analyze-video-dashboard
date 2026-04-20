package com.spoglyadayko.dashboard.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class DashboardApi(private val baseUrlProvider: () -> String) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            config {
                connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    private val baseUrl: String get() = baseUrlProvider()

    suspend fun getDays(): DaysResponse =
        client.get("$baseUrl/api/days").body()

    suspend fun getTodayVideos(day: String? = null): TodayVideosResponse {
        return client.get("$baseUrl/api/today/videos") {
            day?.let { parameter("day", it) }
        }.body()
    }

    suspend fun getVideoLogs(basename: String, severity: String? = null, day: String? = null): VideoLogsResponse {
        return client.get("$baseUrl/api/today/video/$basename/logs") {
            severity?.let { parameter("severity", it) }
            day?.let { parameter("day", it) }
        }.body()
    }

    suspend fun getVideoReidCrops(basename: String): ReidCropsResponse =
        client.get("$baseUrl/api/today/video/$basename/reid-crops").body()

    suspend fun getVideoFrames(basename: String): VideoFramesResponse =
        client.get("$baseUrl/api/today/video/$basename/frames").body()

    suspend fun getVideoHighlight(basename: String): VideoHighlightResponse =
        client.get("$baseUrl/api/today/video/$basename/highlight").body()

    fun highlightUrl(path: String): String = "$baseUrl$path"

    suspend fun getTodayStats(day: String? = null): TodayStatsResponse {
        return client.get("$baseUrl/api/today/stats") {
            day?.let { parameter("day", it) }
        }.body()
    }

    suspend fun getGateCrossings(day: String? = null): GateCrossingsResponse {
        return client.get("$baseUrl/api/today/gate-crossings") {
            day?.let { parameter("day", it) }
        }.body()
    }

    suspend fun getOverallStats(): OverallStatsResponse =
        client.get("$baseUrl/api/stats/overall").body()

    suspend fun getMonitoring(): MonitoringResponse =
        client.get("$baseUrl/api/monitoring").body()

    suspend fun getEventsLatest(since: String? = null): EventsLatestResponse {
        return client.get("$baseUrl/api/events/latest") {
            since?.let { parameter("since", it) }
        }.body()
    }

    suspend fun copyReidCrop(imagePath: String, target: String): ReidCopyResponse {
        return client.post("$baseUrl/api/reid/copy") {
            contentType(ContentType.Application.Json)
            setBody(ReidCopyRequest(imagePath = imagePath, target = target))
        }.body()
    }

    fun galleryImageUrl(target: String, filename: String): String =
        "$baseUrl/api/gallery/$target/$filename"

    suspend fun deleteGalleryCrop(target: String, filename: String) {
        client.delete("$baseUrl/api/gallery/$target/$filename")
    }

    fun videoUrl(basename: String): String = "$baseUrl/video/$basename"

    fun imageUrl(path: String): String = "$baseUrl$path"
}
