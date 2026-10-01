package dev.maintracker.ui.pro

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.maintracker.domain.ProFeature
import dev.maintracker.domain.ProState
import dev.maintracker.ui.common.PrimaryButton
import dev.maintracker.ui.common.app
import kotlinx.coroutines.launch

/** What opened the paywall and what to carry on with if the user buys (e.g. open "new vehicle"). */
data class PaywallRequest(val feature: ProFeature?, val onUnlocked: (() -> Unit)? = null)

/** List price shown until Play supplies the localised one (and in the comparison table). */
const val PRO_LIST_PRICE = "$9.99"

private fun headline(f: ProFeature?) = when (f) {
    ProFeature.SECOND_VEHICLE -> "Track more than one vehicle"
    ProFeature.ATTACHMENTS -> "Keep photos and receipts with your records"
    ProFeature.EXPORT -> "Export your whole history"
    null -> "Maintenance Tracker Pro"
}

private fun pitch(f: ProFeature?) = when (f) {
    ProFeature.SECOND_VEHICLE ->
        "The free version covers one vehicle completely. Pro adds as many as you own: RV, tow car, boat, bikes."
    ProFeature.ATTACHMENTS -> "Pro stores receipts, photos and manuals on this phone, next to the work they belong to."
    ProFeature.EXPORT -> "Pro saves everything to a file you choose, as JSON or a spreadsheet-friendly CSV."
    null -> "One payment unlocks every vehicle, attachments and export. No subscription."
}

/**
 * Build Guide §5.8: reached only in context, one comparison table, a restore link, and a
 * dismiss that's impossible to miss. Declining changes nothing; the offer simply returns
 * the next time the user reaches for a Pro feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Paywall(request: PaywallRequest, message: String?, onDismiss: () -> Unit) {
    val app = LocalContext.current.app()
    val activity = LocalActivity.current
    val status by app.pro.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val price = status.price ?: PRO_LIST_PRICE

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                headline(request.feature),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(pitch(request.feature), style = MaterialTheme.typography.bodyLarge)

            Comparison(price)

            when (status.state) {
                ProState.PENDING -> Notice(
                    "Payment pending. Pro unlocks as soon as Google Play confirms it; you don't need to buy again.",
                    Icons.Filled.HourglassTop,
                )
                ProState.PRO -> Notice("Pro is unlocked on this phone. Thank you!")
                ProState.FREE -> Unit
            }
            message?.let { Notice(it) }

            if (status.state == ProState.FREE) {
                PrimaryButton(
                    "Get Pro · $price once",
                    onClick = {
                        if (activity != null && !busy) {
                            busy = true
                            scope.launch { try { app.pro.buy(activity) } finally { busy = false } }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (status.state == ProState.FREE) "Not now" else "Close")
            }
            TextButton(
                onClick = { scope.launch { busy = true; try { app.pro.restore() } finally { busy = false } } },
                enabled = !busy,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
            ) { Text("Restore purchases") }
            Text(
                "Your vehicles and records are never locked, whatever you decide.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Notice(text: String, icon: ImageVector = Icons.Filled.Info) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

private enum class Cell { YES, NO }

private class Feature(val label: String, val free: Any, val pro: Any, val plus: Any)

private val FEATURES = listOf(
    Feature("Vehicles", "1", "Unlimited", "Unlimited"),
    Feature("Schedules, reminders, history, parts", Cell.YES, Cell.YES, Cell.YES),
    Feature("Photos & receipts", Cell.NO, Cell.YES, Cell.YES),
    Feature("Export (JSON / CSV)", Cell.NO, Cell.YES, Cell.YES),
    Feature("Cloud backup & sync", Cell.NO, Cell.NO, Cell.YES),
)

/** Free | Pro | Plus. Every tick and dash is also spoken, never colour-only (§8). */
@Composable
private fun Comparison(price: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1.5f))
            Header("Free", "$0")
            Header("Pro", "$price once", highlight = true)
            Header("Plus", "$2.99/mo or $19.99/yr · coming later")
        }
        FEATURES.forEach { f ->
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.5f).padding(vertical = 8.dp))
                Value(f.free, "Free", f.label)
                Value(f.pro, "Pro", f.label)
                Value(f.plus, "Plus", f.label)
            }
        }
    }
}

@Composable
private fun RowScope.Header(name: String, sub: String, highlight: Boolean = false) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (highlight) FontWeight.Bold else null,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            sub, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowScope.Value(v: Any, tier: String, feature: String) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        when (v) {
            Cell.YES -> Icon(
                Icons.Filled.Check, contentDescription = "$tier includes $feature",
                tint = MaterialTheme.colorScheme.primary,
            )
            Cell.NO -> Text(
                "—", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$tier doesn't include $feature" },
            )
            else -> Text(v.toString(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}
