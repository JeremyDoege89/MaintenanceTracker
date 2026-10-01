package dev.maintracker.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.maintracker.ui.common.AppActions
import dev.maintracker.ui.common.LocalAppActions
import dev.maintracker.ui.common.ServiceSheetRequest
import dev.maintracker.ui.common.app
import dev.maintracker.ui.garage.GarageScreen
import dev.maintracker.ui.history.RecordDetailScreen
import dev.maintracker.ui.home.HomeScreen
import dev.maintracker.ui.parts.PartEditScreen
import dev.maintracker.ui.parts.PartsScreen
import dev.maintracker.ui.settings.ReminderPermissionPrompts
import dev.maintracker.ui.service.ServiceSheet
import dev.maintracker.ui.settings.SettingsScreen
import dev.maintracker.ui.task.TaskDetailScreen
import dev.maintracker.ui.task.TaskEditScreen
import dev.maintracker.ui.vehicle.VehicleDetailScreen
import dev.maintracker.ui.vehicle.VehicleEditScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private object Routes {
    const val HOME = "home"
    const val GARAGE = "garage"
    const val SETTINGS = "settings"
    const val VEHICLE_NEW = "vehicle/new"
    const val VEHICLE = "vehicle/{id}"
    const val VEHICLE_EDIT = "vehicle/{id}/edit"
    const val PARTS = "vehicle/{id}/parts"
    const val PART_EDIT = "vehicle/{id}/part?partId={partId}&taskId={taskId}"
    const val TASK_NEW = "vehicle/{id}/task/new"
    const val TASK = "task/{taskId}"
    const val TASK_EDIT = "vehicle/{id}/task/{taskId}/edit"
    const val RECORD = "record/{recordId}"

    fun vehicle(id: String) = "vehicle/$id"
    fun vehicleEdit(id: String) = "vehicle/$id/edit"
    fun parts(id: String) = "vehicle/$id/parts"
    fun partEdit(id: String, partId: String? = null, taskId: String? = null) =
        "vehicle/$id/part?partId=${partId.orEmpty()}&taskId=${taskId.orEmpty()}"
    fun taskNew(id: String) = "vehicle/$id/task/new"
    fun task(taskId: String) = "task/$taskId"
    fun taskEdit(id: String, taskId: String) = "vehicle/$id/task/$taskId/edit"
    fun record(recordId: String) = "record/$recordId"
}

private enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "Home", Icons.Filled.Home),
    GARAGE(Routes.GARAGE, "Garage", Icons.Filled.Garage),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
}

private const val UNDO_WINDOW_MS = 10_000L

private fun NavBackStackEntry.arg(name: String): String? = arguments?.getString(name)?.takeIf { it.isNotEmpty() }

/** Single activity, three bottom tabs; everything else is a pushed screen (Build Guide §5). */
@Composable
fun AppNav(openTask: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val pendingTask by openTask.collectAsState()
    LaunchedEffect(pendingTask) {
        pendingTask?.let { nav.navigate(Routes.task(it)) { launchSingleTop = true } }
        openTask.value = null
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = TopLevel.entries.any { it.route == route }

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<ServiceSheetRequest?>(null) }
    val actions = remember {
        AppActions(
            openServiceSheet = { sheet = it },
            showMessage = { message, undo ->
                uiScope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    // Undo stays available for 10 seconds (Build Guide §5.5), then the snackbar goes away.
                    val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                        snackbar.showSnackbar(
                            message,
                            actionLabel = if (undo != null) "Undo" else null,
                            duration = if (undo != null) SnackbarDuration.Indefinite else SnackbarDuration.Short,
                        )
                    }
                    if (result == SnackbarResult.ActionPerformed && undo != null) {
                        // App scope: the undo must finish even if the screen that asked for it is gone.
                        val app = context.app()
                        app.appScope.launch { app.repository.undo(undo) }
                    }
                }
            },
        )
    }

    CompositionLocalProvider(LocalAppActions provides actions) {
        Scaffold(
            // Pushed screens own their top bars and system insets; this only reserves the tab bar.
            contentWindowInsets = WindowInsets(0),
            bottomBar = { if (showBar) BottomBar(nav, route) },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { pad ->
            NavHost(
                nav,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(pad).consumeWindowInsets(pad),
            ) {
                graph(nav)
            }
        }
        sheet?.let { req -> ServiceSheet(req, onDismiss = { sheet = null }) }
        ReminderPermissionPrompts()
    }
}

private fun androidx.navigation.NavGraphBuilder.graph(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }

    composable(Routes.HOME) {
        HomeScreen(
            onAddVehicle = { nav.navigate(Routes.VEHICLE_NEW) },
            onOpenTask = { nav.navigate(Routes.task(it)) },
        )
    }
    composable(Routes.GARAGE) {
        GarageScreen(
            onAddVehicle = { nav.navigate(Routes.VEHICLE_NEW) },
            onOpenVehicle = { nav.navigate(Routes.vehicle(it)) },
            onEditVehicle = { nav.navigate(Routes.vehicleEdit(it)) },
        )
    }
    composable(Routes.SETTINGS) { SettingsScreen() }
    composable(Routes.VEHICLE_NEW) {
        VehicleEditScreen(
            vehicleId = null,
            onDone = { id -> nav.navigate(Routes.vehicle(id)) { popUpTo(Routes.VEHICLE_NEW) { inclusive = true } } },
            onCancel = back,
        )
    }
    composable(Routes.VEHICLE) { e ->
        val id = e.arg("id") ?: return@composable
        VehicleDetailScreen(
            vehicleId = id,
            onBack = back,
            onEdit = { nav.navigate(Routes.vehicleEdit(id)) },
            onOpenTask = { nav.navigate(Routes.task(it)) },
            onAddTask = { nav.navigate(Routes.taskNew(id)) },
            onEditTask = { nav.navigate(Routes.taskEdit(id, it)) },
            onOpenRecord = { nav.navigate(Routes.record(it)) },
            onOpenParts = { nav.navigate(Routes.parts(id)) },
        )
    }
    composable(Routes.VEHICLE_EDIT) { e ->
        val id = e.arg("id") ?: return@composable
        VehicleEditScreen(vehicleId = id, onDone = { back() }, onCancel = back)
    }
    composable(Routes.TASK) { e ->
        val taskId = e.arg("taskId") ?: return@composable
        TaskDetailScreen(
            taskId = taskId,
            onBack = back,
            onEdit = { vehicleId -> nav.navigate(Routes.taskEdit(vehicleId, taskId)) },
            onOpenRecord = { nav.navigate(Routes.record(it)) },
            onAddPart = { vehicleId -> nav.navigate(Routes.partEdit(vehicleId, taskId = taskId)) },
            onEditPart = { vehicleId, partId -> nav.navigate(Routes.partEdit(vehicleId, partId = partId)) },
        )
    }
    composable(Routes.TASK_NEW) { e ->
        val id = e.arg("id") ?: return@composable
        TaskEditScreen(vehicleId = id, taskId = null, onDone = back, onCancel = back)
    }
    composable(Routes.TASK_EDIT) { e ->
        val id = e.arg("id") ?: return@composable
        TaskEditScreen(vehicleId = id, taskId = e.arg("taskId"), onDone = back, onCancel = back)
    }
    composable(Routes.RECORD) { e ->
        val recordId = e.arg("recordId") ?: return@composable
        RecordDetailScreen(recordId, onBack = back, onOpenTask = { nav.navigate(Routes.task(it)) })
    }
    composable(Routes.PARTS) { e ->
        val id = e.arg("id") ?: return@composable
        PartsScreen(
            id, onBack = back,
            onAdd = { nav.navigate(Routes.partEdit(id)) },
            onEdit = { nav.navigate(Routes.partEdit(id, partId = it)) },
        )
    }
    composable(
        Routes.PART_EDIT,
        arguments = listOf(
            navArgument("partId") { type = NavType.StringType; defaultValue = "" },
            navArgument("taskId") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { e ->
        val id = e.arg("id") ?: return@composable
        PartEditScreen(id, partId = e.arg("partId"), taskId = e.arg("taskId"), onDone = back)
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar {
        TopLevel.entries.forEach { tab ->
            NavigationBarItem(
                selected = route == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}
