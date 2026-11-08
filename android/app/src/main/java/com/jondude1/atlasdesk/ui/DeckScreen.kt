package com.jondude1.atlasdesk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.atlasdesk.data.CalendarCard
import com.jondude1.atlasdesk.data.HouseCard
import com.jondude1.atlasdesk.data.PrinterCard
import com.jondude1.atlasdesk.data.ShopCard
import kotlinx.coroutines.delay

/**
 * Direction A "Soft Midnight" layout: one card family, flat mantle cards
 * at 22dp radius on the crust background, a pastel icon-in-shape per
 * card, one hero value, one secondary line, pill chips for sub-status.
 * Printer and Shop heroes use a tabular monospace (the Direction C
 * borrow); House and Next Up heroes stay in the clean sans.
 */

private enum class CardKind { Printer, House, Shop, NextUp }

private fun kindAccent(kind: CardKind): Color = when (kind) {
    CardKind.Printer -> Mocha.Mauve
    CardKind.House -> Mocha.Teal
    CardKind.Shop -> Mocha.Green
    CardKind.NextUp -> Mocha.Blue
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(state: DeckUiState, onRefresh: () -> Unit) {
    val connStale = state.staleSinceMs != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        Header(state)
        Spacer(Modifier.height(16.dp))
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val deck = state.deck
            if (deck == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (connStale) {
                            "Can't reach the Pi — retrying…"
                        } else {
                            "Waiting for first data…"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val cards: List<@Composable () -> Unit> = listOf(
                    { PrinterCardView(deck.printer, connStale) },
                    { HouseCardView(deck.house, connStale) },
                    { ShopCardView(deck.shop, connStale) },
                    { NextUpCardView(deck.calendar, connStale) },
                )
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth >= 700.dp) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(cards.size) { i -> cards[i]() }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(cards.size) { i -> cards[i]() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: DeckUiState) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(10_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "ATLAS Desk",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = fmtClockMs(nowMs),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        val stale = state.staleSinceMs
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (stale == null) LiveGreen else StaleAmber),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                state.deck == null && stale == null -> "connecting"
                stale != null -> "stale since ${fmtClockMs(stale)}"
                else -> "live · ${state.endpointLabel ?: ""}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Simple geometric glyph for a card, drawn in its accent colour. */
@Composable
private fun CardGlyph(kind: CardKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f
        fun x(v: Float) = v * sx
        fun y(v: Float) = v * sy
        when (kind) {
            CardKind.Printer -> {
                // paper sheet
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x(7f), y(3f)),
                    size = Size(x(10f), y(6f)),
                    cornerRadius = CornerRadius(x(1f), y(1f)),
                    style = Stroke(width = x(1.6f)),
                )
                // body
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x(3f), y(8f)),
                    size = Size(x(18f), y(8f)),
                    cornerRadius = CornerRadius(x(2f), y(2f)),
                    style = Stroke(width = x(1.8f)),
                )
                // output tray line
                drawLine(color, Offset(x(7f), y(19f)), Offset(x(17f), y(19f)), strokeWidth = x(1.8f))
                // status dot
                drawCircle(color, radius = x(1.4f), center = Offset(x(17.5f), y(11.5f)))
            }
            CardKind.House -> {
                val roof = Path().apply {
                    moveTo(x(4f), y(12f))
                    lineTo(x(12f), y(4.5f))
                    lineTo(x(20f), y(12f))
                }
                drawPath(roof, color, style = Stroke(width = x(2f)))
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x(7f), y(11f)),
                    size = Size(x(10f), y(8f)),
                    cornerRadius = CornerRadius(x(1f), y(1f)),
                    style = Stroke(width = x(1.8f)),
                )
                drawCircle(color, radius = x(1.3f), center = Offset(x(12f), y(15.5f)))
            }
            CardKind.Shop -> {
                // bag body
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x(5f), y(8f)),
                    size = Size(x(14f), y(12f)),
                    cornerRadius = CornerRadius(x(2f), y(2f)),
                    style = Stroke(width = x(1.8f)),
                )
                // handle
                val handle = Path().apply {
                    moveTo(x(9f), y(11f))
                    quadraticBezierTo(x(12f), y(4.5f), x(15f), y(11f))
                }
                drawPath(handle, color, style = Stroke(width = x(1.8f)))
            }
            CardKind.NextUp -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x(4f), y(5f)),
                    size = Size(x(16f), y(15f)),
                    cornerRadius = CornerRadius(x(2.5f), y(2.5f)),
                    style = Stroke(width = x(1.8f)),
                )
                drawLine(color, Offset(x(4f), y(10f)), Offset(x(20f), y(10f)), strokeWidth = x(1.6f))
                drawLine(color, Offset(x(8.5f), y(3f)), Offset(x(8.5f), y(7.5f)), strokeWidth = x(2f))
                drawLine(color, Offset(x(15.5f), y(3f)), Offset(x(15.5f), y(7.5f)), strokeWidth = x(2f))
                drawCircle(color, radius = x(1.3f), center = Offset(x(9f), y(14.5f)))
                drawCircle(color, radius = x(1.3f), center = Offset(x(14f), y(14.5f)))
            }
        }
    }
}

/** Mushroom-style icon shape: accent-tinted circle with the glyph inside. */
@Composable
private fun IconShape(kind: CardKind) {
    val accent = kindAccent(kind)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        CardGlyph(kind, accent)
    }
}

/** Fully-round status pill. */
@Composable
private fun Pill(text: String, accent: Color = Mocha.Subtext) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = accent)
    }
}

@Composable
private fun CardShell(
    kind: CardKind,
    title: String,
    staleText: String?,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconShape(kind)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (staleText != null) Pill(staleText, StaleAmber)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

private fun staleLabel(connStale: Boolean, deckStaleSinceIso: String?): String? = when {
    deckStaleSinceIso != null -> "stale since ${fmtIsoClock(deckStaleSinceIso)}"
    connStale -> "connection lost"
    else -> null
}

/** Hero value. mono=true gives the Direction C tabular monospace. */
@Composable
private fun Hero(text: String, mono: Boolean = false) {
    Text(
        text = text,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SecondaryLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ChipRow(chips: List<Pair<String, Color>>) {
    if (chips.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (text, accent) -> Pill(text, accent) }
    }
}

@Composable
private fun PrinterCardView(card: PrinterCard?, connStale: Boolean) {
    CardShell(
        kind = CardKind.Printer,
        title = "Printer",
        staleText = if (card?.stale == true) {
            staleLabel(connStale, card.staleSince)
        } else {
            staleLabel(connStale, null)
        },
    ) {
        if (card == null || card.available == false) {
            Hero("Unavailable", mono = true)
            SecondaryLine("The printer isn't reporting right now.")
            return@CardShell
        }
        val printing = card.state?.equals("printing", ignoreCase = true) == true ||
            card.state?.equals("running", ignoreCase = true) == true
        if (printing && card.progressPct != null) {
            Hero(fmtPct(card.progressPct), mono = true)
        } else {
            Hero(fmtState(card.state), mono = true)
        }
        SecondaryLine(
            card.jobName ?: when {
                (card.queued ?: 0) > 0 -> "${card.queued} job(s) in the slicer queue"
                else -> "Nothing in the queue"
            }
        )
        card.progressPct?.let { p ->
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (p / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Mocha.Mauve,
                trackColor = Mocha.Surface0,
            )
        }
        ChipRow(
            listOfNotNull(
                card.timeRemainingS?.takeIf { it > 0 }?.let { "${fmtDuration(it)} left" to Mocha.Peach },
                if (card.nozzleC != null || card.bedC != null) {
                    "${fmtTempC(card.nozzleC)} / ${fmtTempC(card.bedC)}" to Mocha.Subtext
                } else {
                    null
                },
                card.queued?.let { "Queue $it" to Mocha.Subtext },
            )
        )
    }
}

@Composable
private fun HouseCardView(card: HouseCard?, connStale: Boolean) {
    CardShell(
        kind = CardKind.House,
        title = "House",
        staleText = if (card?.stale == true) {
            staleLabel(connStale, card.staleSince)
        } else {
            staleLabel(connStale, null)
        },
    ) {
        if (card == null) {
            Hero("—")
            return@CardShell
        }
        Hero("${fmtTempF(card.tempF)} inside")
        SecondaryLine("Target ${fmtTempF(card.targetF)} · ${dash(card.mode)} · ${dash(card.hvacAction)}")
        ChipRow(
            listOf(
                "Jon · ${dash(card.jon)}" to if (card.jon == "home") Mocha.Green else Mocha.Subtext,
                "Jenna · ${dash(card.jenna)}" to if (card.jenna == "home") Mocha.Green else Mocha.Subtext,
            )
        )
        card.summary?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShopCardView(card: ShopCard?, connStale: Boolean) {
    val old = card != null && snapshotOlderThan(card.snapshotAt, 26)
    CardShell(
        kind = CardKind.Shop,
        title = "Shop",
        staleText = when {
            connStale -> "connection lost"
            old -> "old snapshot"
            else -> null
        },
    ) {
        if (card == null) {
            Hero("—", mono = true)
            return@CardShell
        }
        Hero("${fmtInt(card.visits7d)} visits", mono = true)
        SecondaryLine(
            "7 days · yesterday ${fmtInt(card.visitsYesterday)} visits, " +
                "${fmtInt(card.ordersYesterday)} orders, ${fmtMoney(card.revenueYesterday)}"
        )
        ChipRow(
            listOfNotNull(
                card.activeListings?.let { "$it listings" to Mocha.Subtext },
                card.orders7d?.let { "$it orders · 7d" to Mocha.Subtext },
                card.adsSpend30d?.let { "Ads ${fmtMoney(it)} · 30d" to Mocha.Subtext },
                fmtSnapshotAt(card.snapshotAt).takeIf { it.isNotEmpty() }
                    ?.let { it to Mocha.Subtext },
            )
        )
    }
}

@Composable
private fun NextUpCardView(card: CalendarCard?, connStale: Boolean) {
    val old = card != null && snapshotOlderThan(card.snapshotAt, 26)
    CardShell(
        kind = CardKind.NextUp,
        title = "Next Up",
        staleText = when {
            connStale -> "connection lost"
            old -> "old snapshot"
            else -> null
        },
    ) {
        if (card == null || card.events.isEmpty()) {
            Hero("Nothing scheduled")
            return@CardShell
        }
        val first = card.events.first()
        Hero(dash(first.summary))
        SecondaryLine(
            listOfNotNull(
                fmtEventStart(first.start, first.allDay),
                first.location?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        )
        card.events.drop(1).take(3).forEach { ev ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = dash(ev.summary),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = fmtEventStart(ev.start, ev.allDay),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
