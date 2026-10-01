package dev.maintracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maintracker.data.VehicleRepository
import dev.maintracker.data.db.DueItem
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.domain.DueText
import dev.maintracker.ui.common.LocalAppActions
import dev.maintracker.ui.common.PrimaryButton
import dev.maintracker.ui.common.ServiceSheetRequest
import dev.maintracker.ui.common.StatusPill
import dev.maintracker.ui.common.app
import dev.maintracker.ui.theme.LocalStatusColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val loaded: Boolean = false,
    val vehicleCount: Int = 0,
    val due: List<DueItem> = emptyList(),
)

class HomeViewModel(private val repo: VehicleRepository) : ViewModel() {
    val state: StateFlow<HomeState> = combine(repo.observeVehicleCount(), repo.observeDue()) { n, due ->
        HomeState(true, n, due)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Build Guide §6.2: statuses re-evaluate on every Home entry (date may have rolled over). */
    fun refresh() = viewModelScope.launch { repo.recomputeAll() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onAddVehicle: () -> Unit, onOpenTask: (String) -> Unit) {
    val actions = LocalAppActions.current
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel { HomeViewModel(context.app().repository) }
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Due") }) },
        floatingActionButton = {
            // The single most-used action: one tap from Home (Build Guide §5.2).
            if (s.vehicleCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = { actions.openServiceSheet(ServiceSheetRequest()) },
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    text = { Text("Log service") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !s.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.vehicleCount == 0 -> Onboarding(onAddVehicle)
                else -> DueList(s.due, onOpenTask)
            }
        }
    }
}

/** First run: one-line value prop, one CTA, no permissions (Build Guide §5.1). */
@Composable
private fun Onboarding(onAddVehicle: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Track maintenance for the rigs car apps forgot.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "Motorhomes, trailers, boats, motorcycles, ATVs and jet skis — with a starting schedule for each.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
        )
        PrimaryButton("Add your first vehicle", onAddVehicle, Modifier.fillMaxWidth())
    }
}

@Composable
private fun DueList(due: List<DueItem>, onOpenTask: (String) -> Unit) {
    val now = System.currentTimeMillis()
    val overdue = due.count { it.task.status == TaskStatus.OVERDUE }
    val soon = due.count { it.task.status == TaskStatus.DUE_SOON }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Hero(overdue, soon) }
        items(due, key = { it.task.id }) { d ->
            DueCard(d, now, onClick = { onOpenTask(d.task.id) })
        }
    }
}

/** Priority: overdue, then due soon, then all caught up. Colour always paired with icon + words. */
@Composable
private fun Hero(overdue: Int, soon: Int) {
    val c = LocalStatusColors.current
    val (bg, fg, icon, text) = when {
        overdue > 0 -> Hero4(c.overdue, c.onOverdue, Icons.Filled.Error,
            "$overdue overdue" + if (soon > 0) " · $soon due soon" else "")
        soon > 0 -> Hero4(c.dueSoon, c.onDueSoon, Icons.Filled.Schedule, "$soon due soon")
        else -> Hero4(c.ok, c.onOk, Icons.Filled.CheckCircle, "All caught up")
    }
    Card(colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text, style = MaterialTheme.typography.headlineSmall)
                if (overdue == 0 && soon == 0) {
                    Text("Nothing needs attention right now.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private data class Hero4(val bg: Color, val fg: Color, val icon: ImageVector, val text: String)

@Composable
private fun DueCard(d: DueItem, now: Long, onClick: () -> Unit) {
    val t = d.task
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.vehicleName, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val reason = DueText.reason(t, t.meterUnit?.let(d::currentMeter), now)
                if (reason.isNotEmpty()) Text(reason, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(12.dp))
            StatusPill(t.status)
        }
    }
}
