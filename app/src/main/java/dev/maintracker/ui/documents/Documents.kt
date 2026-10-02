package dev.maintracker.ui.documents

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.maintracker.data.model.Attachment
import dev.maintracker.data.model.AttachmentKind
import dev.maintracker.domain.Format
import dev.maintracker.domain.ProFeature
import dev.maintracker.ui.common.LocalAppActions
import dev.maintracker.ui.common.PrimaryButton
import dev.maintracker.ui.common.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val AttachmentKind.label: String
    get() = when (this) {
        AttachmentKind.PHOTO -> "Photo"
        AttachmentKind.RECEIPT -> "Receipt"
        AttachmentKind.MANUAL -> "Manual"
        AttachmentKind.INSURANCE -> "Insurance"
        AttachmentKind.OTHER -> "Other"
    }

private fun Attachment.isImage() = mimeType.startsWith("image/")

/**
 * Vehicle detail › Documents (Build Guide §5.4). Free tier sees a locked placeholder with an
 * upgrade CTA — but anything already stored (e.g. restored from a backup) is always shown.
 */
@Composable
fun DocumentsTab(vehicleId: String) {
    val app = LocalContext.current.app()
    val pro by app.pro.status.collectAsStateWithLifecycle()
    val all by remember(vehicleId) { app.repository.observeAttachments(vehicleId) }.collectAsStateWithLifecycle(null)
    var filter by rememberSaveable { mutableStateOf<AttachmentKind?>(null) }
    val list = all ?: return

    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            if (pro.isPro) EmptyDocuments() else LockedDocuments()
        } else {
            Column {
                val kinds = list.map { it.kind }.distinct().sorted()
                if (kinds.size > 1) {
                    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(filter == null, { filter = null }, { Text("All") }, Modifier.heightIn(min = 48.dp))
                        kinds.forEach { k ->
                            FilterChip(filter == k, { filter = if (filter == k) null else k }, { Text(k.label) }, Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
                AttachmentGrid(list.filter { filter == null || it.kind == filter })
            }
        }
        AddAttachmentFab(vehicleId, recordId = null, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@Composable
private fun EmptyDocuments() = CenterMessage(
    "No documents yet",
    "Keep receipts, photos, manuals and insurance papers here. They're stored only on this phone.",
)

@Composable
private fun LockedDocuments() {
    val actions = LocalAppActions.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Photos & receipts are a Pro feature", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp))
        Text(
            "Keep receipts, photos, manuals and insurance papers with the vehicle, on this phone only.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        OutlinedButton(onClick = { actions.openPaywall(dev.maintracker.ui.pro.PaywallRequest(ProFeature.ATTACHMENTS)) },
            modifier = Modifier.heightIn(min = 52.dp)) { Text("See what Pro adds") }
    }
}

@Composable
private fun CenterMessage(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AttachmentGrid(list: List<Attachment>) {
    var open by remember { mutableStateOf<Attachment?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(list, key = { it.id }) { a -> AttachmentTile(a) { open = a } }
    }
    open?.let { a -> AttachmentViewer(a, onDismiss = { open = null }) }
}

@Composable
fun AttachmentTile(a: Attachment, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open", onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Thumb(a, Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(8.dp)) {
            Text(a.caption ?: a.kind.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${a.kind.label} · ${Format.date(a.createdAt)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Thumb(a: Attachment, modifier: Modifier, maxPx: Int = 480) {
    val photos = LocalContext.current.app().photos
    val bmp by produceState<ImageBitmap?>(null, a.fileName, maxPx) {
        if (a.isImage()) value = withContext(Dispatchers.IO) { photos.load(photos.attachmentPath(a.fileName), maxPx)?.asImageBitmap() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                if (a.mimeType == "application/pdf") Icons.Filled.PictureAsPdf else Icons.Filled.Description,
                contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Add" for a vehicle's documents or one record's receipts. Pro-gated at the moment of intent (§5.8). */
@Composable
fun AddAttachmentFab(vehicleId: String, recordId: String?, modifier: Modifier = Modifier) {
    val adder = rememberAttachmentAdder(vehicleId, recordId)
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        ExtendedFloatingActionButton(
            onClick = { adder.start { menu = true } },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(if (recordId == null) "Add document" else "Add photo / receipt") },
        )
        AddMenu(menu, onDismiss = { menu = false }, adder)
    }
    adder.Dialogs()
}

/** Inline button variant for record detail. */
@Composable
fun AddAttachmentButton(vehicleId: String, recordId: String) {
    val adder = rememberAttachmentAdder(vehicleId, recordId)
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { adder.start { menu = true } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add photo / receipt", Modifier.padding(start = 8.dp))
        }
        AddMenu(menu, onDismiss = { menu = false }, adder)
    }
    adder.Dialogs()
}

@Composable
private fun AddMenu(expanded: Boolean, onDismiss: () -> Unit, adder: AttachmentAdder) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Take photo") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
            onClick = { onDismiss(); adder.camera() })
        DropdownMenuItem(text = { Text("Choose file or photo") }, leadingIcon = { Icon(Icons.Filled.UploadFile, null) },
            onClick = { onDismiss(); adder.pick() })
    }
}

class AttachmentAdder internal constructor(
    val start: (showMenu: () -> Unit) -> Unit,
    val camera: () -> Unit,
    val pick: () -> Unit,
    val Dialogs: @Composable () -> Unit,
)

@Composable
private fun rememberAttachmentAdder(vehicleId: String, recordId: String?): AttachmentAdder {
    val app = LocalContext.current.app()
    val actions = LocalAppActions.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Uri?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val defaultKind = if (recordId != null) AttachmentKind.RECEIPT else AttachmentKind.PHOTO

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending = cameraUri?.let(Uri::parse)
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pending = uri }

    return AttachmentAdder(
        start = { showMenu -> actions.requirePro(ProFeature.ATTACHMENTS, showMenu) },
        camera = {
            val uri = app.photos.newCameraUri()
            cameraUri = uri.toString()
            try { takePicture.launch(uri) } catch (_: ActivityNotFoundException) { error = "No camera app found." }
        },
        pick = { openDoc.launch(arrayOf("image/*", "application/pdf")) },
        Dialogs = {
            pending?.let { uri ->
                DetailsDialog(
                    title = "Save document",
                    initialKind = defaultKind,
                    initialCaption = "",
                    onDismiss = { pending = null },
                ) { kind, caption ->
                    pending = null
                    scope.launch {
                        val saved = app.repository.addAttachment(vehicleId, uri, kind, caption, recordId)
                        if (saved == null) error = "Couldn't read that file. Nothing was saved."
                        else actions.showMessage("${kind.label} saved", null)
                    }
                }
            }
            error?.let { e ->
                AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton({ error = null }) { Text("OK") } },
                    text = { Text(e) })
            }
        },
    )
}

@Composable
private fun DetailsDialog(
    title: String,
    initialKind: AttachmentKind,
    initialCaption: String,
    onDismiss: () -> Unit,
    onSave: (AttachmentKind, String) -> Unit,
) {
    var kind by remember { mutableStateOf(initialKind) }
    var caption by remember { mutableStateOf(initialCaption) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttachmentKind.entries.forEach { k ->
                        FilterChip(kind == k, { kind = k }, { Text(k.label) }, Modifier.heightIn(min = 48.dp))
                    }
                }
                OutlinedTextField(caption, { caption = it.take(120) }, label = { Text("Caption (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onSave(kind, caption) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Full view + open in another app, edit, delete (confirmed). */
@Composable
fun AttachmentViewer(a: Attachment, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.caption ?: a.kind.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Thumb(a, Modifier.fillMaxWidth().aspectRatio(if (a.isImage()) 0.75f else 2f), maxPx = 1600)
                Text("${a.kind.label} · added ${Format.date(a.createdAt)} · ${sizeText(a.byteSize)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ editing = true }, Modifier.heightIn(min = 48.dp)) { Text("Edit") }
                    TextButton({ confirmDelete = true }, Modifier.heightIn(min = 48.dp)) { Text("Delete…") }
                }
            }
        },
        confirmButton = {
            TextButton({
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(app.photos.shareUri(a.fileName), a.mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { context.startActivity(Intent.createChooser(intent, "Open with")) }
                catch (_: ActivityNotFoundException) { error = "No app on this phone can open this file." }
            }) { Text("Open") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Close") } },
    )
    if (editing) {
        DetailsDialog("Edit document", a.kind, a.caption.orEmpty(), onDismiss = { editing = false }) { kind, caption ->
            editing = false
            scope.launch { app.repository.updateAttachment(a.copy(kind = kind, caption = caption.trim().ifEmpty { null })) }
            onDismiss()
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this ${a.kind.label.lowercase()}?") },
            text = { Text("The file is removed from this phone. This can't be undone.") },
            confirmButton = {
                TextButton({ confirmDelete = false; scope.launch { app.repository.deleteAttachment(a) }; onDismiss() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

fun sizeText(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** Record detail: the record's receipts inline (§5.4 "record detail with attachments inline"). */
@Composable
fun RecordAttachments(vehicleId: String, recordId: String) {
    val app = LocalContext.current.app()
    val list by remember(recordId) { app.repository.observeRecordAttachments(recordId) }.collectAsStateWithLifecycle(emptyList())
    var open by remember { mutableStateOf<Attachment?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (list.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                list.take(3).forEach { a -> AttachmentTile(a, Modifier.width(110.dp)) { open = a } }
            }
            if (list.size > 3) Text("+${list.size - 3} more in Documents", style = MaterialTheme.typography.bodySmall)
        }
        AddAttachmentButton(vehicleId, recordId)
    }
    open?.let { AttachmentViewer(it, onDismiss = { open = null }) }
}
