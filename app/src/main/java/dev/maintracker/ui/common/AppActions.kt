package dev.maintracker.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import dev.maintracker.data.UndoSnapshot
import dev.maintracker.ui.pro.PaywallRequest

/** What the quick-add sheet should open for. All null = pick a vehicle, log service. */
data class ServiceSheetRequest(
    val vehicleId: String? = null,
    /** Prefill "Mark complete" for this task. */
    val taskId: String? = null,
    /** Edit an existing record instead of adding one. */
    val recordId: String? = null,
    val metersOnly: Boolean = false,
)

/** App-wide actions hosted by AppNav: the quick-add sheet, the 10-second Undo snackbar and the paywall. */
class AppActions(
    val openServiceSheet: (ServiceSheetRequest) -> Unit,
    val showMessage: (message: String, undo: UndoSnapshot?) -> Unit,
    val openPaywall: (PaywallRequest) -> Unit,
    /**
     * Runs [then] if one more active vehicle fits the user's tier; otherwise opens the paywall,
     * which runs [then] after a successful purchase. The tap that got here is the user's
     * "yes, I want another vehicle" (Build Guide §5.3), so the paywall never comes first.
     */
    val requireVehicleSlot: (then: () -> Unit) -> Unit,
)

val LocalAppActions = staticCompositionLocalOf<AppActions> { error("AppActions not provided") }
