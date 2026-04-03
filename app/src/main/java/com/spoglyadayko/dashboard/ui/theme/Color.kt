package com.spoglyadayko.dashboard.ui.theme

import androidx.compose.ui.graphics.Color

// Status colors matching the web dashboard
val StatusNoMotion = Color(0xFF64748B)
val StatusNoSignificantMotion = Color(0xFFEB980A)
val StatusNoPerson = Color(0xFFF8E805)
val StatusSignificantMotion = Color(0xFF6B52FA)
val StatusGateCrossing = Color(0xFF22C55E)
val StatusError = Color(0xFFEF4444)
val StatusUnknown = Color(0xFF9FB0FF)

// Severity colors
val SeverityDebug = Color(0xFF9CA3AF)
val SeverityInfo = Color(0xFF3B82F6)
val SeverityWarning = Color(0xFFF59E0B)
val SeverityError = Color(0xFFEF4444)
val SeverityCritical = Color(0xFFDC2626)

// ReID colors
val ReidMatched = Color(0xFF2BA8C4)
val ReidUnmatched = Color(0xFF1E3A8A)
val ReidNeg = Color(0xFF602085)

// Away/back
val AwayColor = Color(0xFF24BF91)
val BackColor = Color(0xFFEF4444)

fun statusColor(status: String?): Color = when (status) {
    "no_motion" -> StatusNoMotion
    "no_significant_motion" -> StatusNoSignificantMotion
    "no_person" -> StatusNoPerson
    "significant_motion" -> StatusSignificantMotion
    "gate_crossing" -> StatusGateCrossing
    "error" -> StatusError
    else -> StatusUnknown
}

fun severityColor(level: String): Color = when (level) {
    "DEBUG" -> SeverityDebug
    "INFO" -> SeverityInfo
    "WARNING" -> SeverityWarning
    "ERROR" -> SeverityError
    "CRITICAL" -> SeverityCritical
    else -> SeverityDebug
}
