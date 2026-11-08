package com.jondude1.atlasdesk.data

import org.json.JSONObject

/**
 * Models for deckd's GET /api/deck payload (Phase 1 backend on the Pi).
 *
 * Every field is nullable on purpose: the app renders what the backend
 * actually sent and shows a dash for what it didn't. Nothing here ever
 * invents a zero — a missing number must look missing, never read as 0.
 */
data class DeckState(
    val generatedAt: String?,
    val printer: PrinterCard?,
    val house: HouseCard?,
    val shop: ShopCard?,
    val calendar: CalendarCard?,
)

data class PrinterCard(
    val sourceOk: Boolean?,
    val stale: Boolean?,
    val staleSince: String?,
    val available: Boolean?,
    val state: String?,
    val jobName: String?,
    val progressPct: Double?,
    val timeRemainingS: Long?,
    val timeElapsedS: Long?,
    val eta: String?,
    val nozzleC: Double?,
    val bedC: Double?,
    val queued: Int?,
    val activeJobs: Int?,
)

data class HouseCard(
    val sourceOk: Boolean?,
    val stale: Boolean?,
    val staleSince: String?,
    val tempF: Double?,
    val targetF: Double?,
    val mode: String?,
    val hvacAction: String?,
    val jon: String?,
    val jenna: String?,
    val motion: List<String>,
    val summary: String?,
)

data class ShopCard(
    val status: String?,
    val snapshotAt: String?,
    val visitsYesterday: Int?,
    val visits7d: Int?,
    val ordersYesterday: Int?,
    val revenueYesterday: Double?,
    val orders7d: Int?,
    val revenue7d: Double?,
    val activeListings: Int?,
    val adsSpend30d: Double?,
    val adsOrders30d: Int?,
)

data class CalEvent(
    val summary: String?,
    val start: String?,
    val end: String?,
    val location: String?,
    val allDay: Boolean,
)

data class CalendarCard(
    val status: String?,
    val snapshotAt: String?,
    val events: List<CalEvent>,
)

// --- org.json helpers: absent or JSON-null -> null (never a fake 0) ---

private fun JSONObject.obj(key: String): JSONObject? =
    if (isNull(key)) null else optJSONObject(key)

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

private fun JSONObject.bool(key: String): Boolean? =
    if (isNull(key)) null else optBoolean(key)

private fun JSONObject.dbl(key: String): Double? =
    if (isNull(key)) null else optDouble(key)

private fun JSONObject.int(key: String): Int? =
    if (isNull(key)) null else optInt(key)

private fun JSONObject.lng(key: String): Long? =
    if (isNull(key)) null else optLong(key)

object DeckParser {
    fun parse(json: String): DeckState {
        val root = JSONObject(json)
        return DeckState(
            generatedAt = root.str("generated_at"),
            printer = root.obj("printer")?.let { p ->
                PrinterCard(
                    sourceOk = p.bool("source_ok"),
                    stale = p.bool("stale"),
                    staleSince = p.str("stale_since"),
                    available = p.bool("available"),
                    state = p.str("state"),
                    jobName = p.str("job_name"),
                    progressPct = p.dbl("progress"),
                    timeRemainingS = p.lng("time_remaining_s"),
                    timeElapsedS = p.lng("time_elapsed_s"),
                    eta = p.str("eta"),
                    nozzleC = p.dbl("nozzle_c"),
                    bedC = p.dbl("bed_c"),
                    queued = p.int("queued"),
                    activeJobs = p.int("active_jobs"),
                )
            },
            house = root.obj("house")?.let { h ->
                val motionArr = h.optJSONArray("motion")
                HouseCard(
                    sourceOk = h.bool("source_ok"),
                    stale = h.bool("stale"),
                    staleSince = h.str("stale_since"),
                    tempF = h.dbl("temp_f"),
                    targetF = h.dbl("target_f"),
                    mode = h.str("mode"),
                    hvacAction = h.str("hvac_action"),
                    jon = h.str("jon"),
                    jenna = h.str("jenna"),
                    motion = buildList {
                        if (motionArr != null) {
                            for (i in 0 until motionArr.length()) {
                                motionArr.optString(i).takeIf { it.isNotEmpty() }?.let { add(it) }
                            }
                        }
                    },
                    summary = h.str("summary"),
                )
            },
            shop = root.obj("shop")?.let { s ->
                ShopCard(
                    status = s.str("status"),
                    snapshotAt = s.str("snapshot_at") ?: s.str("generated_at"),
                    visitsYesterday = s.int("visits_yesterday"),
                    visits7d = s.int("visits_7d"),
                    ordersYesterday = s.int("orders_yesterday"),
                    revenueYesterday = s.dbl("revenue_yesterday"),
                    orders7d = s.int("orders_7d"),
                    revenue7d = s.dbl("revenue_7d"),
                    activeListings = s.int("active_listings"),
                    adsSpend30d = s.dbl("ads_spend_30d"),
                    adsOrders30d = s.int("ads_orders_30d"),
                )
            },
            calendar = root.obj("calendar")?.let { c ->
                val arr = c.optJSONArray("events")
                CalendarCard(
                    status = c.str("status"),
                    snapshotAt = c.str("snapshot_at"),
                    events = buildList {
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val e = arr.optJSONObject(i) ?: continue
                                add(
                                    CalEvent(
                                        summary = e.str("summary"),
                                        start = e.str("start"),
                                        end = e.str("end"),
                                        location = e.str("location"),
                                        allDay = e.bool("all_day") ?: false,
                                    )
                                )
                            }
                        }
                    },
                )
            },
        )
    }
}
