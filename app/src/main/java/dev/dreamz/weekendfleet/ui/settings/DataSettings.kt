package dev.dreamz.weekendfleet.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.dreamz.weekendfleet.data.BackupManager
import dev.dreamz.weekendfleet.domain.Backup
import dev.dreamz.weekendfleet.domain.BackupException
import dev.dreamz.weekendfleet.domain.DateStyle
import dev.dreamz.weekendfleet.domain.DisplayPrefs
import dev.dreamz.weekendfleet.domain.DistanceUnit
import dev.dreamz.weekendfleet.domain.Format
import dev.dreamz.weekendfleet.domain.ProFeature
import dev.dreamz.weekendfleet.ui.common.LocalAppActions
import dev.dreamz.weekendfleet.ui.common.app
import dev.dreamz.weekendfleet.ui.documents.sizeText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Currency

/** Shown in About; also the developer contact on the Play listing. */
const val SUPPORT_EMAIL = "jaydoege8969@gmail.com"
const val PRIVACY_POLICY_URL = "https://jeremydoege89.github.io/maintenance-tracker-privacy/"

/** Settings › Units (Build Guide §5.7). Display-only: stored values never change. */
@Composable
fun UnitsSection(display: DisplayPrefs) {
    val app = LocalContext.current.app()
    val scope = rememberCoroutineScope()
    var pick by remember { mutableStateOf<String?>(null) }
    fun save(d: DisplayPrefs) = scope.launch { app.settings.setDisplay(d) }

    Section("Units")
    Row("Distance", if (display.distance == DistanceUnit.MILES) "Miles" else "Kilometres") { pick = "distance" }
    Row("Currency for new records", display.currency ?: "${Format.localeCurrency()} (phone default)") { pick = "currency" }
    Row("Date format", "${display.dateStyle.label} · today is ${Format.date(System.currentTimeMillis())}") { pick = "date" }

    when (pick) {
        "distance" -> Choice("Distance", DistanceUnit.entries, display.distance, { it.long }, onDismiss = { pick = null }) {
            pick = null; save(display.copy(distance = it))
        }
        "date" -> Choice("Date format", DateStyle.entries, display.dateStyle, { it.label }, onDismiss = { pick = null }) {
            pick = null; save(display.copy(dateStyle = it))
        }
        "currency" -> CurrencyDialog(display.currency, onDismiss = { pick = null }) { pick = null; save(display.copy(currency = it)) }
    }
}

@Composable
private fun <T> Choice(title: String, options: List<T>, current: T, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = o == current, role = Role.RadioButton, onClick = { onPick(o) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o == current, onClick = null)
                        Text(label(o), Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CurrencyDialog(current: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var text by remember { mutableStateOf(current.orEmpty()) }
    val code = text.trim().uppercase()
    val valid = code.isEmpty() || runCatching { Currency.getInstance(code) }.isSuccess
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency for new records") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Existing records keep the currency they were saved with.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    text, { text = it.filter(Char::isLetter).take(3) },
                    label = { Text("3-letter code, e.g. USD, CAD, EUR") },
                    supportingText = {
                        Text(
                            when {
                                code.isEmpty() -> "Blank = phone default (${Format.localeCurrency()})"
                                valid -> Currency.getInstance(code).displayName
                                else -> "Not a currency code"
                            },
                        )
                    },
                    isError = !valid, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
        },
        confirmButton = { TextButton({ onSave(code.ifEmpty { null }) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

private enum class ExportKind(val mime: String, val ext: String) {
    BACKUP_JSON("application/json", "json"),
    BACKUP_ZIP("application/zip", "zip"),
    RECORDS_CSV("text/csv", "csv"),
    TASKS_CSV("text/csv", "csv"),
}

/** Settings › Data: export (Pro), import, delete everything, storage used. */
@Composable
fun DataSection() {
    val context = LocalContext.current
    val app = context.app()
    val actions = LocalAppActions.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var pendingExport by remember { mutableStateOf<ExportKind?>(null) }
    var preview by remember { mutableStateOf<Pair<Uri, Backup>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    val usage by produceState<BackupManager.Usage?>(null, refresh) { value = app.backups.usage() }

    fun run(label: String, block: suspend () -> String) {
        busy = label
        scope.launch {
            try {
                actions.showMessage(block(), null)
            } catch (e: BackupException) {
                error = e.message
            } catch (e: Exception) {
                error = "Something went wrong: ${e.message ?: e.javaClass.simpleName}. Nothing was changed."
            } finally {
                busy = null
                refresh++
            }
        }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val kind = pendingExport
        pendingExport = null
        if (uri == null || kind == null) return@rememberLauncherForActivityResult
        run("Exporting…") {
            when (kind) {
                ExportKind.BACKUP_JSON -> app.backups.exportBackup(uri, withFiles = false)
                ExportKind.BACKUP_ZIP -> app.backups.exportBackup(uri, withFiles = true)
                ExportKind.RECORDS_CSV -> app.backups.exportRecordsCsv(uri)
                ExportKind.TASKS_CSV -> app.backups.exportTasksCsv(uri)
            }
            "Exported"
        }
    }
    fun export(kind: ExportKind, name: String) = actions.requirePro(ProFeature.EXPORT) {
        pendingExport = kind
        create.launch("weekendfleet-$name-${LocalDate.now()}.${kind.ext}")
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Reading backup…"
        scope.launch {
            try {
                preview = uri to app.backups.preview(uri)
            } catch (e: BackupException) {
                error = e.message
            } catch (e: Exception) {
                error = "Couldn't read that file."
            } finally {
                busy = null
            }
        }
    }

    Section("Data")
    Row("Back up everything", "Full backup with photos & documents (.zip). Restore it with Import.") {
        export(ExportKind.BACKUP_ZIP, "backup")
    }
    Row("Back up records only", "Smaller .json file without photos & documents") { export(ExportKind.BACKUP_JSON, "backup") }
    Row("Service history spreadsheet", "Every record as CSV, for Excel or Sheets") { export(ExportKind.RECORDS_CSV, "history") }
    Row("Schedule spreadsheet", "Every task and when it's due, as CSV") { export(ExportKind.TASKS_CSV, "schedule") }
    Row("Import a backup", "Replaces what's on this phone with a backup made by this app") {
        open.launch(arrayOf("application/json", "application/zip", "application/octet-stream", "text/plain"))
    }
    Row(
        "Storage used",
        usage?.let { "${sizeText(it.total)} · records ${sizeText(it.databaseBytes)}, photos ${sizeText(it.photoBytes)}, documents ${sizeText(it.attachmentBytes)}" }
            ?: "Calculating…",
    )
    Row("Delete all data", "Removes every vehicle, record, photo and document from this phone") { confirmWipe = true }

    busy?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(label, Modifier.padding(start = 16.dp))
                }
            },
        )
    }
    error?.let { e ->
        AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton({ error = null }) { Text("OK") } },
            title = { Text("Couldn't finish") }, text = { Text(e) })
    }
    preview?.let { (uri, b) ->
        val files = b.attachments.size
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Replace everything with this backup?") },
            text = {
                Text(
                    "The backup has ${b.vehicles.size} vehicle(s), ${b.records.size} service record(s) and $files document(s)" +
                        (if (!b.filesIncluded && files > 0) " (file contents not included in this backup)" else "") +
                        ", made ${b.exportedAt.take(10)}.\n\nEverything currently on this phone will be replaced. " +
                        "Make a backup first if you might want it back.",
                )
            },
            confirmButton = {
                TextButton({
                    preview = null
                    run("Importing…") {
                        val r = app.backups.import(uri)
                        "Imported ${r.vehicles} vehicle(s), ${r.records} record(s)" +
                            if (r.skippedFiles > 0) ". ${r.skippedFiles} document(s) had no file and were skipped." else ""
                    }
                }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ preview = null }) { Text("Cancel") } },
        )
    }
    if (confirmWipe) {
        var typed by remember { mutableStateOf("") }
        val ok = typed.trim().equals("delete", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete all data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Every vehicle, task, record, part, photo and document on this phone is deleted. This can't be undone. " +
                            "Your settings and Pro unlock are kept.",
                    )
                    OutlinedTextField(typed, { typed = it }, label = { Text("Type DELETE to confirm") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton({
                    confirmWipe = false
                    run("Deleting…") { app.backups.deleteAll(); "All data deleted" }
                }, enabled = ok) { Text("Delete everything", color = if (ok) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            dismissButton = { TextButton({ confirmWipe = false }) { Text("Cancel") } },
        )
    }
}

/** Settings › About. */
@Composable
fun AboutSection(version: String) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(false) }
    Section("About")
    Row("Version", version)
    Row(
        "Privacy",
        "Everything stays on this phone. No account, no analytics, no ads. Nothing leaves unless you export it, " +
            "or tap Decode to look up a VIN with NHTSA's free decoder. VIN/HIN scans are read on the phone." +
            if (PRIVACY_POLICY_URL.isNotEmpty()) " Tap for the full policy." else "",
    ) { if (PRIVACY_POLICY_URL.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())) }
    if (SUPPORT_EMAIL.isNotEmpty()) {
        Row("Contact & support", SUPPORT_EMAIL) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_SENDTO, "mailto:$SUPPORT_EMAIL".toUri())
                        .putExtra(Intent.EXTRA_SUBJECT, "Maintenance Tracker $version"),
                )
            }
        }
    }
    Row("Open-source licences", "Libraries this app is built with") { licenses = true }
    if (licenses) LicensesDialog { licenses = false }
}

private val LICENSES = listOf(
    "AndroidX (Compose, Room, Navigation, Lifecycle, DataStore, WorkManager, Core)" to "Apache License 2.0",
    "Material Components & Material Icons for Compose" to "Apache License 2.0",
    "Kotlin standard library, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0",
    "Google Play Billing Library" to "Android Software Development Kit License",
)

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open-source licences") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LICENSES.forEach { (lib, lic) ->
                    Column {
                        Text(lib, style = MaterialTheme.typography.bodyMedium)
                        Text(lic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}
