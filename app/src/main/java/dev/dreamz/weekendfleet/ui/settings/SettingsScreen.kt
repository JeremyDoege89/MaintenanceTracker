package dev.dreamz.weekendfleet.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamz.weekendfleet.BuildConfig
import dev.dreamz.weekendfleet.MaintTrackerApp
import dev.dreamz.weekendfleet.billing.FakeBillingGateway
import dev.dreamz.weekendfleet.data.AppSettings
import dev.dreamz.weekendfleet.domain.ProState
import dev.dreamz.weekendfleet.ui.common.LocalAppActions
import dev.dreamz.weekendfleet.ui.pro.PaywallRequest
import dev.dreamz.weekendfleet.domain.ReminderPrefs
import dev.dreamz.weekendfleet.ui.common.app
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

fun notificationsAllowed(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

fun openNotificationSettings(context: Context) = context.startActivity(
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
)

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
    }
}

private fun minuteText(m: Int): String =
    LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** Build Guide §5.7. No account row anywhere — that is the point. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.app()
    val scope = rememberCoroutineScope()
    val settings by app.settings.flow.collectAsStateWithLifecycle(AppSettings())
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val notifOn = remember(resumeTick) { notificationsAllowed(context) }
    val exactOn = remember(resumeTick) { app.alarms.canScheduleExact() }
    val next by produceState<Long?>(null, resumeTick, settings) { value = app.reminders.nextFireAt() }
    val pro by app.pro.status.collectAsStateWithLifecycle()
    val actions = LocalAppActions.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { app.settings.setNotificationPromptAnswered() }
        if (!granted) openNotificationSettings(context)
        resumeTick++
    }

    fun saveReminders(r: ReminderPrefs) = scope.launch {
        app.settings.setReminders(r)
        app.repository.recomputeAll() // re-plans every reminder with the new rules
    }

    var pickTime by remember { mutableStateOf<String?>(null) } // "remind" | "quietStart" | "quietEnd"
    var editWindow by remember { mutableStateOf(false) }
    val r = settings.reminders

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("Reminders")
            Row(
                "Notifications",
                if (notifOn) "On · tap to choose which kinds" else "Off — tap to turn on. Due items still show in the app.",
            ) {
                if (!notifOn && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    "Precise timing",
                    if (exactOn) "On" else "Off — reminders may arrive a few minutes late. Tap to allow.",
                ) { openExactAlarmSettings(context) }
            }
            Row("Remind me at", minuteText(r.remindAtMinute)) { pickTime = "remind" }
            SwitchRow(
                "Quiet hours",
                if (r.quietEnabled) "${minuteText(r.quietStartMinute)} – ${minuteText(r.quietEndMinute)}: held until morning"
                else "Off",
                r.quietEnabled,
            ) { saveReminders(r.copy(quietEnabled = it)) }
            if (r.quietEnabled) {
                Row("Quiet from", minuteText(r.quietStartMinute)) { pickTime = "quietStart" }
                Row("Quiet until", minuteText(r.quietEndMinute)) { pickTime = "quietEnd" }
            }
            Row("Due-soon window", "Up to ${settings.dueSoonDays} days ahead (or 10% of the interval, if shorter)") {
                editWindow = true
            }
            SwitchRow("Repeat overdue reminders", "Nudge again 7 and 30 days after the due date", r.escalation) {
                saveReminders(r.copy(escalation = it))
            }
            Row(
                "Next reminder",
                next?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
                    ?: "Nothing scheduled",
            )

            Section("Pro")
            Row(
                "Maintenance Tracker Pro",
                when (pro.state) {
                    ProState.FREE -> "Free: one vehicle, everything else included. Tap to see what Pro adds."
                    ProState.PENDING -> "Payment pending. Pro unlocks when Google Play confirms it."
                    ProState.PRO -> "Unlocked. Thank you!"
                },
            ) { actions.openPaywall(PaywallRequest(feature = null)) }
            Row("Restore purchases", "Checks Google Play for Pro bought on this Google account") {
                scope.launch { app.pro.restore() }
            }
            if (BuildConfig.DEBUG) DebugBillingRows(app)

            UnitsSection(settings.display)
            DataSection()
            AboutSection(version)
        }
    }

    pickTime?.let { which ->
        val initial = when (which) {
            "quietStart" -> r.quietStartMinute
            "quietEnd" -> r.quietEndMinute
            else -> r.remindAtMinute
        }
        TimeDialog(initial, onDismiss = { pickTime = null }) { m ->
            pickTime = null
            saveReminders(
                when (which) {
                    "quietStart" -> r.copy(quietStartMinute = m)
                    "quietEnd" -> r.copy(quietEndMinute = m)
                    else -> r.copy(remindAtMinute = m)
                },
            )
        }
    }
    if (editWindow) {
        WindowDialog(settings.dueSoonDays, onDismiss = { editWindow = false }) { days ->
            editWindow = false
            scope.launch {
                app.settings.setDueSoonDays(days)
                app.repository.applyLeadCap()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialMinute / 60, initialMinute % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state) },
    )
}

@Composable
private fun WindowDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val n = text.toIntOrNull()
    val valid = n != null && n in 1..60
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Due-soon window") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(2) },
                label = { Text("Days ahead (1–60)") },
                isError = !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(n!!) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun Section(title: String) {
    HorizontalDivider()
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
internal fun Row(title: String, detail: String, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        modifier = Modifier.heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
internal fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.heightIn(min = 56.dp).clickable { onChange(!checked) },
    )
}

/** Debug builds only: drive the fake Play backend from the phone (adb: DebugBillingReceiver). */
@Composable
private fun DebugBillingRows(app: MaintTrackerApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fake = app.billing as? FakeBillingGateway
    var tick by remember { mutableIntStateOf(0) }
    Section("Debug · billing")
    SwitchRow(
        "Use fake billing",
        if (fake != null) "On: no money moves. Turn off + restart to talk to real Google Play." else "Off: real Google Play. Turn on + restart.",
        FakeBillingGateway.enabled(context).also { tick },
    ) { FakeBillingGateway.setEnabled(context, it); tick++ }
    if (fake == null) return
    val state = remember(tick) { fake.describe() }
    Row("Fake Play state", state)
    Row("Next purchase result", "${fake.nextOutcome.name.lowercase()} · tap to cycle") {
        val all = FakeBillingGateway.Outcome.entries
        fake.nextOutcome = all[(fake.nextOutcome.ordinal + 1) % all.size]
        tick++
    }
    SwitchRow("Play unreachable", "Queries fail; cached entitlement must hold", fake.offline.also { tick }) {
        fake.offline = it
        tick++
    }
    Row("Complete pending payment", "As if the cash payment cleared") { scope.launch { fake.completePending(); tick++ } }
    Row("Refund in Play Console", "App notices on its next Play check (restart or Restore)") { fake.refund(); tick++ }
}
