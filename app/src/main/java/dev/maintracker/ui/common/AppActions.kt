package dev.maintracker.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import dev.maintracker.data.UndoSnapshot

/** What the quick-add sheet should open for. All null = pick a vehicle, log service. */
data class ServiceSheetRequest(
    val vehicleId: String? = null,
    /** Prefill "Mark complete" for this task. */
    val taskId: String? = null,
    /** Edit an existing record instead of adding one. */
    val recordId: String? = null,
    val metersOnly: Boolean = false,
)

/** App-wide actions hosted by AppNav: the quick-add sheet and the 10-second Undo snackbar. */
class AppActions(
    val openServiceSheet: (ServiceSheetRequest) -> Unit,
    val showMessage: (message: String, undo: UndoSnapshot?) -> Unit,
)

val LocalAppActions = staticCompositionLocalOf<AppActions> { error("AppActions not provided") }
