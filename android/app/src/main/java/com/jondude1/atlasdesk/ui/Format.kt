package com.jondude1.atlasdesk.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display formatting. Anything unknown renders as an em dash, not a 0. */

val ET: ZoneId = ZoneId.of("America/New_York")

private val clockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

fun dash(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "—"

fun fmtInt(v: Int?): String = v?.toString() ?: "—"

fun fmtMoney(v: Double?): String = v?.let { "$" + String.format(Locale.US, "%.2f", it) } ?: "—"

fun fmtTempF(v: Double?): String = v?.let {
    if (it % 1.0 == 0.0) "${it.toInt()}°" else String.format(Locale.US, "%.1f°", it)
} ?: "—"

fun fmtTempC(v: Double?): String = v?.let { String.format(Locale.US, "%.0f°C", it) } ?: "—"

fun fmtPct(v: Double?): String = v?.let {
    if (it % 1.0 == 0.0) "${it.toInt()}%" else String.format(Locale.US, "%.1f%%", it)
} ?: "—"

fun fmtDuration(totalSeconds: Long?): String {
    if (totalSeconds == null) return "—"
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "<1m"
    }
}

/** "9:41 AM" for an epoch-ms wall clock, in Eastern time. */
fun fmtClockMs(epochMs: Long): String =
    clockFmt.format(java.time.Instant.ofEpochMilli(epochMs).atZone(ET))

/** deckd's stale_since is an ISO-8601 UTC timestamp; show just the clock. */
fun fmtIsoClock(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return try {
        clockFmt.format(OffsetDateTime.parse(iso).atZoneSameInstant(ET))
    } catch (e: Exception) {
        "—"
    }
}

/** "as of 9:41 AM" for snapshot timestamps (ISO, possibly UTC). */
fun fmtSnapshotAt(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        "as of " + clockFmt.format(OffsetDateTime.parse(iso).atZoneSameInstant(ET))
    } catch (e: Exception) {
        ""
    }
}

/** True when an ISO snapshot timestamp is older than [hours]. */
fun snapshotOlderThan(iso: String?, hours: Long): Boolean {
    if (iso.isNullOrBlank()) return true
    return try {
        val t = OffsetDateTime.parse(iso).toInstant()
        java.time.Instant.now().isAfter(t.plusSeconds(hours * 3600))
    } catch (e: Exception) {
        true
    }
}

/** Event start label: "Today 9:00 AM", "Tomorrow 7:55 AM", "Mon, Nov 9". */
fun fmtEventStart(start: String?, allDay: Boolean): String {
    if (start.isNullOrBlank()) return "—"
    val today = LocalDate.now(ET)
    return try {
        if (allDay) {
            val d = LocalDate.parse(start.take(10))
            when (d) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> dayFmt.format(d)
            }
        } else {
            val dt = OffsetDateTime.parse(start).atZoneSameInstant(ET)
            val d = dt.toLocalDate()
            val day = when (d) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> dayFmt.format(d)
            }
            "$day ${clockFmt.format(dt)}"
        }
    } catch (e: Exception) {
        try {
            LocalDateTime.parse(start).format(clockFmt)
        } catch (e2: Exception) {
            start
        }
    }
}

/** Title-case a backend state string like "finished" or "heat". */
fun fmtState(s: String?): String =
    s?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "—"
