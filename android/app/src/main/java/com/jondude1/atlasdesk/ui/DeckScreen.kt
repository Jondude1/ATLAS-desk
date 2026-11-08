package com.jondude1.atlasdesk.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jondude1.atlasdesk.data.CalendarCard
import com.jondude1.atlasdesk.data.HouseCard
import com.jondude1.atlasdesk.data.PrinterCard
import com.jondude1.atlasdesk.data.ShopCard

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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "ATLAS Desk",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
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

/** Amber chip shown when data on a card is not current. */
@Composable
private fun StaleChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(StaleAmber.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = StaleAmber)
    }
}

@Composable
private fun CardShell(
    title: String,
    staleText: String?,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (staleText != null) StaleChip(staleText)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Connection-stale text shared by all cards; deckd-side staleness wins if present. */
private fun staleLabel(connStale: Boolean, deckStaleSinceIso: String?): String? = when {
    deckStaleSinceIso != null -> "stale since ${fmtIsoClock(deckStaleSinceIso)}"
    connStale -> "connection lost"
    else -> null
}

@Composable
private fun BigValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PrinterCardView(card: PrinterCard?, connStale: Boolean) {
    CardShell(
        title = "Printer",
        staleText = if (card?.stale == true) {
            staleLabel(connStale, card.staleSince)
        } else {
            staleLabel(connStale, null)
        },
    ) {
        if (card == null || card.available == false) {
            BigValue("Unavailable")
            Text(
                text = "The printer isn't reporting right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CardShell
        }
        BigValue(fmtState(card.state))
        card.jobName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        card.progressPct?.let { p ->
            LinearProgressIndicator(
                progress = { (p / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            DetailLine("Progress", fmtPct(p))
        }
        DetailLine("Remaining", fmtDuration(card.timeRemainingS))
        DetailLine("Nozzle / bed", "${fmtTempC(card.nozzleC)} / ${fmtTempC(card.bedC)}")
        DetailLine("Slicer queue", "${fmtInt(card.queued)} waiting · ${fmtInt(card.activeJobs)} running")
    }
}

@Composable
private fun HouseCardView(card: HouseCard?, connStale: Boolean) {
    CardShell(
        title = "House",
        staleText = if (card?.stale == true) {
            staleLabel(connStale, card.staleSince)
        } else {
            staleLabel(connStale, null)
        },
    ) {
        if (card == null) {
            BigValue("—")
            return@CardShell
        }
        BigValue("${fmtTempF(card.tempF)} inside")
        DetailLine("Target", "${fmtTempF(card.targetF)} · ${dash(card.mode)} (${dash(card.hvacAction)})")
        DetailLine("Jon", dash(card.jon))
        DetailLine("Jenna", dash(card.jenna))
        card.summary?.let {
            Spacer(Modifier.height(4.dp))
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
        title = "Shop",
        staleText = when {
            connStale -> "connection lost"
            old -> "old snapshot"
            else -> null
        },
    ) {
        if (card == null) {
            BigValue("—")
            return@CardShell
        }
        BigValue("${fmtInt(card.visits7d)} visits · 7d")
        DetailLine("Yesterday", "${fmtInt(card.visitsYesterday)} visits · ${fmtInt(card.ordersYesterday)} orders · ${fmtMoney(card.revenueYesterday)}")
        DetailLine("Orders · 7d", "${fmtInt(card.orders7d)} · ${fmtMoney(card.revenue7d)}")
        DetailLine("Active listings", fmtInt(card.activeListings))
        DetailLine("Ads · 30d", "${fmtMoney(card.adsSpend30d)} · ${fmtInt(card.adsOrders30d)} orders")
        fmtSnapshotAt(card.snapshotAt).takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextUpCardView(card: CalendarCard?, connStale: Boolean) {
    val old = card != null && snapshotOlderThan(card.snapshotAt, 26)
    CardShell(
        title = "Next Up",
        staleText = when {
            connStale -> "connection lost"
            old -> "old snapshot"
            else -> null
        },
    ) {
        if (card == null || card.events.isEmpty()) {
            BigValue("Nothing scheduled")
            return@CardShell
        }
        card.events.take(5).forEachIndexed { i, ev ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Text(
                text = dash(ev.summary),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    fmtEventStart(ev.start, ev.allDay),
                    ev.location?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
