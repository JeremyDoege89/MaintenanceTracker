package dev.maintracker.ui.common

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RvHookup
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.maintracker.MaintTrackerApp
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.VehicleType
import dev.maintracker.ui.theme.LocalStatusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Context.app(): MaintTrackerApp = applicationContext as MaintTrackerApp

/** Grease-friendly primary action: 56dp tall (Build Guide §8 asks 52dp+). */
val PrimaryButtonHeight = 56.dp

val VehicleType.icon: ImageVector
    get() = when (this) {
        VehicleType.MOTORHOME -> Icons.Filled.AirportShuttle
        VehicleType.TRAVEL_TRAILER -> Icons.Filled.RvHookup
        VehicleType.BOAT_POWER -> Icons.Filled.DirectionsBoat
        VehicleType.BOAT_SAIL -> Icons.Filled.Sailing
        VehicleType.MOTORCYCLE -> Icons.Filled.TwoWheeler
        VehicleType.ATV_UTV -> Icons.Filled.Agriculture
        VehicleType.PWC -> Icons.Filled.Waves
    }

val TaskStatus.label: String
    get() = when (this) {
        TaskStatus.OVERDUE -> "Overdue"
        TaskStatus.DUE_SOON -> "Due soon"
        TaskStatus.OK -> "OK"
        TaskStatus.MANUAL -> "Checklist"
    }

/** Color is always paired with an icon and words — never red-without-text (Build Guide §8). */
@Composable
fun StatusPill(status: TaskStatus, modifier: Modifier = Modifier, count: Int? = null) {
    val c = LocalStatusColors.current
    val (bg, fg, icon) = when (status) {
        TaskStatus.OVERDUE -> Triple(c.overdue, c.onOverdue, Icons.Filled.Error)
        TaskStatus.DUE_SOON -> Triple(c.dueSoon, c.onDueSoon, Icons.Filled.Schedule)
        TaskStatus.OK -> Triple(c.ok, c.onOk, Icons.Filled.CheckCircle)
        TaskStatus.MANUAL -> Triple(c.manual, c.onManual, Icons.Filled.Checklist)
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(
            if (count != null) "$count ${status.label.lowercase()}" else status.label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun TypeBadge(type: VehicleType, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 5))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            type.icon,
            contentDescription = type.label,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** Vehicle photo if one is stored, else the type badge. */
@Composable
fun VehicleImage(
    photoPath: String?,
    type: VehicleType,
    size: Dp,
    modifier: Modifier = Modifier,
    maxPx: Int = 512,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, photoPath) {
        value = photoPath?.let { p ->
            withContext(Dispatchers.IO) { context.app().photos.load(p, maxPx)?.asImageBitmap() }
        }
    }
    val b = bitmap
    if (b != null) {
        Image(
            b, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(RoundedCornerShape(size / 5)),
        )
    } else {
        TypeBadge(type, size, modifier)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = PrimaryButtonHeight),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}
