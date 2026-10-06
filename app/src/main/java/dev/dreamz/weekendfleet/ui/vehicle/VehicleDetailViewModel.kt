package dev.dreamz.weekendfleet.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dreamz.weekendfleet.data.VehicleRepository
import dev.dreamz.weekendfleet.data.model.MaintenanceTask
import dev.dreamz.weekendfleet.data.model.ServiceRecord
import dev.dreamz.weekendfleet.data.model.Vehicle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailState(
    val loaded: Boolean = false,
    val vehicle: Vehicle? = null,
    val tasks: List<MaintenanceTask> = emptyList(),
    val recent: List<ServiceRecord> = emptyList(),
    val partCount: Int = 0,
)

class VehicleDetailViewModel(private val repo: VehicleRepository, private val vehicleId: String) : ViewModel() {

    val state: StateFlow<DetailState> = combine(
        repo.observeVehicle(vehicleId),
        repo.observeTasks(vehicleId),
        repo.observeRecentRecords(vehicleId),
        repo.observeParts(vehicleId),
    ) { v, t, r, p -> DetailState(true, v, t, r, p.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    fun updateMeters(miles: Double?, engineHours: Double?, generatorHours: Double?) = viewModelScope.launch {
        repo.updateMeters(vehicleId, miles, engineHours, generatorHours)
    }

    fun setArchived(archived: Boolean) = viewModelScope.launch { repo.setArchived(vehicleId, archived) }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        repo.deleteVehicle(vehicleId)
        onDeleted()
    }
}
