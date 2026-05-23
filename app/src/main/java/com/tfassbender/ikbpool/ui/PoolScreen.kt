package com.tfassbender.ikbpool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tfassbender.ikbpool.data.model.LaneReservations
import com.tfassbender.ikbpool.data.model.Occupancy
import com.tfassbender.ikbpool.data.model.PoolStatus
import com.tfassbender.ikbpool.data.model.Source
import com.tfassbender.ikbpool.data.model.SourceWarning
import com.tfassbender.ikbpool.domain.Recommendation
import com.tfassbender.ikbpool.ui.theme.IKBPoolMonitorTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SUPER_GREEN = Color(0xFF2E7D32)
private val LIGHT_GREEN = Color(0xFF8BC34A)
private val OKAY_YELLOW = Color(0xFFFBC02D)
private val SCHLECHT_RED = Color(0xFFC62828)
private val UNKNOWN_GREY = Color(0xFF9E9E9E)

internal fun Recommendation.color(): Color = when (this) {
    Recommendation.SUPER -> SUPER_GREEN
    Recommendation.GUT -> LIGHT_GREEN
    Recommendation.OKAY -> OKAY_YELLOW
    Recommendation.SCHLECHT -> SCHLECHT_RED
    Recommendation.UNBEKANNT -> UNKNOWN_GREY
}

@Composable
fun PoolScreen(
    modifier: Modifier = Modifier,
    viewModel: PoolViewModel = viewModel(factory = PoolViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PoolScreenContent(state = state, modifier = modifier, onRefresh = viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PoolScreenContent(
    state: PoolUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !state.hasLoadedOnce && state.isRefreshing -> InitialLoadingView()
            !state.hasLoadedOnce && state.errorMessage != null -> ErrorView(state.errorMessage, onRefresh)
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LoadedView(state)
            }
        }
    }
}

@Composable
private fun InitialLoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Fehler beim Laden", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Erneut versuchen") }
    }
}

@Composable
private fun LoadedView(state: PoolUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Hallenbad Höttinger Au", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        TrafficLight(state.recommendation)

        Spacer(Modifier.height(16.dp))
        Text(
            state.recommendation.message,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(32.dp))
        FactRow(label = "Auslastung", value = formatOccupancy(state.status?.occupancy))
        FactRow(label = "Bahnen 1–3", value = formatLanes(state.status?.reservations))
        FactRow(label = "Stand", value = formatFetchedAt(state.status?.fetchedAt))

        if (state.errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Aktualisierung fehlgeschlagen: ${state.errorMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.status?.warnings?.isNotEmpty() == true) {
            Spacer(Modifier.height(24.dp))
            WarningsList(state.status.warnings)
        }
    }
}

@Composable
private fun TrafficLight(rec: Recommendation) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(rec.color()),
    )
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WarningsList(warnings: List<SourceWarning>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Warnungen",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(4.dp))
        warnings.forEach { w ->
            Text(
                "• ${sourceLabel(w.source)}: ${w.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun sourceLabel(source: Source): String = when (source) {
    Source.OPENING_HOURS -> "Öffnungszeiten"
    Source.OCCUPANCY -> "Auslastung"
    Source.RESERVATIONS -> "Bahnenreservierung"
}

private fun formatOccupancy(occ: Occupancy?): String =
    if (occ == null) "—" else "${occ.percent} %"

private fun formatLanes(res: LaneReservations?): String = when {
    res == null -> "—"
    res.lowerThreeAllReserved -> "Alle reserviert"
    !res.lane1Reserved && !res.lane2Reserved && !res.lane3Reserved -> "Frei"
    else -> buildString {
        if (res.lane1Reserved) append("1 ")
        if (res.lane2Reserved) append("2 ")
        if (res.lane3Reserved) append("3 ")
        append("reserviert")
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private fun formatFetchedAt(instant: Instant?): String {
    if (instant == null) return "—"
    return instant.atZone(ZoneId.of("Europe/Vienna")).toLocalTime().format(timeFormat) + " Uhr"
}

// ---- Previews ----

private fun previewStatus(
    occ: Int = 14,
    lanes: LaneReservations = LaneReservations(false, false, false),
) = PoolStatus(
    openingHours = null,
    occupancy = Occupancy(occ),
    reservations = lanes,
    fetchedAt = Instant.parse("2026-05-23T10:42:00Z"),
    warnings = emptyList(),
)

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun PreviewSuper() {
    IKBPoolMonitorTheme {
        PoolScreenContent(
            state = PoolUiState(
                hasLoadedOnce = true,
                status = previewStatus(occ = 14),
                recommendation = Recommendation.SUPER,
            )
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun PreviewGut() {
    IKBPoolMonitorTheme {
        PoolScreenContent(
            state = PoolUiState(
                hasLoadedOnce = true,
                status = previewStatus(occ = 25),
                recommendation = Recommendation.GUT,
            )
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun PreviewOkay() {
    IKBPoolMonitorTheme {
        PoolScreenContent(
            state = PoolUiState(
                hasLoadedOnce = true,
                status = previewStatus(occ = 38, lanes = LaneReservations(true, true, true)),
                recommendation = Recommendation.OKAY,
            )
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun PreviewSchlecht() {
    IKBPoolMonitorTheme {
        PoolScreenContent(
            state = PoolUiState(
                hasLoadedOnce = true,
                status = previewStatus(occ = 55),
                recommendation = Recommendation.SCHLECHT,
            )
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun PreviewWithWarnings() {
    IKBPoolMonitorTheme {
        PoolScreenContent(
            state = PoolUiState(
                hasLoadedOnce = true,
                status = PoolStatus(
                    openingHours = null,
                    occupancy = Occupancy(20),
                    reservations = null,
                    fetchedAt = Instant.parse("2026-05-23T10:42:00Z"),
                    warnings = listOf(
                        SourceWarning(Source.RESERVATIONS, "HTTP 403 fetching widget/api/slot"),
                    ),
                ),
                recommendation = Recommendation.GUT,
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewLoading() {
    IKBPoolMonitorTheme {
        PoolScreenContent(state = PoolUiState(isRefreshing = true))
    }
}
