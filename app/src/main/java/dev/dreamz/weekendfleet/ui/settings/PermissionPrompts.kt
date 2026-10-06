package dev.dreamz.weekendfleet.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamz.weekendfleet.ui.common.app
import kotlinx.coroutines.launch

/**
 * Permission moments in context, never up front (Build Guide §5.1, §6.3): once there is something
 * to remind about, a plain-words pre-prompt, then the system dialog; then — once, and only if
 * notifications are allowed — an explainer if precise alarms are off. Each is answered once and never shown again; Settings can change either.
 */
@Composable
fun ReminderPermissionPrompts() {
    val context = LocalContext.current
    val app = context.app()
    val scope = rememberCoroutineScope()
    val settings by app.settings.flow.collectAsStateWithLifecycle(null)
    val vehicles by app.repository.observeVehicleCount().collectAsStateWithLifecycle(0)
    var dismissedThisSession by remember { mutableStateOf(false) }
    val s = settings ?: return
    if (vehicles == 0 || dismissedThisSession) return

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { app.settings.setNotificationPromptAnswered() }
    }

    val needsNotif = Build.VERSION.SDK_INT >= 33 && !s.notificationPromptAnswered && !notificationsAllowed(context)
    // Precise timing only matters once notifications can actually be shown.
    val needsExact = !needsNotif && notificationsAllowed(context) &&
        !s.exactAlarmExplainerShown && !app.alarms.canScheduleExact()

    when {
        needsNotif -> AlertDialog(
            onDismissRequest = { dismissedThisSession = true },
            title = { Text("Get a heads-up before things are due?") },
            text = {
                Text(
                    "We'll notify you when maintenance is coming due or overdue — by date, miles or hours. " +
                        "Nothing else, ever. You can change this any time in Settings.",
                )
            },
            confirmButton = { TextButton(onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") } },
            dismissButton = {
                TextButton(onClick = { scope.launch { app.settings.setNotificationPromptAnswered() } }) { Text("Not now") }
            },
        )
        needsExact -> AlertDialog(
            onDismissRequest = { dismissedThisSession = true },
            title = { Text("Reminders may be a few minutes late") },
            text = {
                Text(
                    "Android holds back reminders a little unless the app may set precise alarms. " +
                        "That's fine for maintenance — or allow precise timing so reminders land right on time.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.settings.setExactAlarmExplainerShown() }
                    openExactAlarmSettings(context)
                }) { Text("Allow precise timing") }
            },
            dismissButton = {
                TextButton(onClick = { scope.launch { app.settings.setExactAlarmExplainerShown() } }) { Text("Keep as is") }
            },
        )
    }
}
