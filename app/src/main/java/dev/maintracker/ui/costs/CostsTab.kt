package dev.maintracker.ui.costs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.maintracker.data.model.ServiceRecord
import dev.maintracker.domain.CostPeriod
import dev.maintracker.domain.CostSummary
import dev.maintracker.domain.Costs
import dev.maintracker.domain.Format
import dev.maintracker.ui.common.app

/** Build Guide §5.4 Costs: month/year totals, per-area bars, top spends. Only records with a cost count. */
@Composable
fun CostsTab(vehicleId: String) {
    val repo = LocalContext.current.app().repository
    val records by remember(vehicleId) { repo.observeAllRecords(vehicleId) }.collectAsStateWithLifecycle(null)
    var period by rememberSaveable { mutableStateOf(CostPeriod.THIS_YEAR) }
    val list = records ?: return
    if (list.none { it.cost != null }) {
        Empty(list)
        return
    }
    val summary = remember(list, period) {
        val currency = Costs.headlineCurrency(list, Format.defaultCurrency())
        Costs.summarize(list, currency, period, System.currentTimeMillis())
    }
    CostsContent(summary, period, onPeriod = { period = it })
}

@Composable
private fun Empty(list: List<ServiceRecord>) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No costs yet", style = MaterialTheme.typography.titleMedium)
        Text(
            if (list.isEmpty()) "Add a cost when you log service and totals show up here."
            else "None of the ${list.size} records here has a cost. Add one from a record's Edit screen and it'll count.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun CostsContent(s: CostSummary, period: CostPeriod, onPeriod: (CostPeriod) -> Unit) {
    fun money(v: Double) = Format.money(v, s.currency)
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The totals double as the period picker: tap one and the breakdown below follows it.
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    CostPeriod.THIS_MONTH to s.thisMonth, CostPeriod.THIS_YEAR to s.thisYear, CostPeriod.ALL_TIME to s.allTime,
                ).forEach { (p, total) ->
                    Tile(p.label, money(total), selected = p == period, onClick = { onPeriod(p) }, modifier = Modifier.weight(1f))
                }
            }
        }
        if (s.byArea.isEmpty()) {
            item { Text("Nothing costed ${period.label.lowercase()}.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item { Heading("By area · ${period.label.lowercase()}") }
            val max = s.byArea.first().total
            items(s.byArea, key = { it.area.name }) { a -> Bar(a.area.label, money(a.total), (a.total / max).toFloat()) }
            item { Heading("Biggest spends") }
            items(s.topItems, key = { it.title + it.total }) { i ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(i.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (i.count == 1) "1 record" else "${i.count} records",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(money(i.total), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        val notes = buildList {
            s.otherCurrencies.forEach { (c, v) -> add("Also ${Format.money(v, c)} in $c, not included above.") }
            if (s.uncostedRecords > 0) {
                add("${s.uncostedRecords} record${if (s.uncostedRecords == 1) " has" else "s have"} no cost entered and aren't counted.")
            }
        }
        if (notes.isNotEmpty()) {
            item {
                Text(
                    notes.joinToString("\n"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Heading(text: String) = Text(
    text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() },
)

/** Selected = filled primary container + a check, so it never relies on colour alone. */
@Composable
private fun Tile(label: String, value: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Card(
        modifier.heightIn(min = 72.dp).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Area name and amount are always printed; the bar only adds the at-a-glance proportion. */
@Composable
private fun Bar(label: String, amount: String, fraction: Float) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$label: $amount" }) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(amount, style = MaterialTheme.typography.bodyMedium)
        }
        Canvas(Modifier.fillMaxWidth().height(12.dp).padding(top = 4.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0.02f, 1f), size.height), cornerRadius = r)
        }
    }
}
