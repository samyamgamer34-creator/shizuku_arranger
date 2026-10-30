package com.shizuku.arranger.ui

import android.content.Intent
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shizuku.arranger.data.Direction
import com.shizuku.arranger.data.Recording
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(vm: RecordingsViewModel = viewModel()) {
    val recordings by vm.visible.collectAsStateWithLifecycle()
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val focus by vm.numberFocus.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val durations by vm.durationMap.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()

    var showSort by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Recording?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Call Recordings", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (recordings.size == total) "$total recordings" else "${recordings.size} of $total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    Box {
                        IconButton(onClick = { showSort = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                        DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(order.label, fontWeight = if (order == sort) FontWeight.Bold else null)
                                    },
                                    onClick = { vm.sort.value = order; showSort = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showFolderDialog = true }) { Icon(Icons.Default.Settings, "Folder") }
                },
            )
        },
        bottomBar = {
            if (player.recording != null) {
                PlayerBar(player, vm)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search number, date (2024-01-31, jan, monday)…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = filter == DirectionFilter.ALL,
                    onClick = { vm.filter.value = DirectionFilter.ALL },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = filter == DirectionFilter.INCOMING,
                    onClick = { vm.filter.value = DirectionFilter.INCOMING },
                    label = { Text("Incoming") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallReceived, null, Modifier.size(18.dp), tint = IncomingColor) },
                )
                FilterChip(
                    selected = filter == DirectionFilter.OUTGOING,
                    onClick = { vm.filter.value = DirectionFilter.OUTGOING },
                    label = { Text("Outgoing") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMade, null, Modifier.size(18.dp), tint = OutgoingColor) },
                )
            }
            focus?.let { number ->
                InputChip(
                    selected = true,
                    onClick = { vm.numberFocus.value = null },
                    label = { Text("Only ${number.ifEmpty { "unknown numbers" }}") },
                    trailingIcon = { Icon(Icons.Default.Close, "Show all", Modifier.size(18.dp)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (recordings.isEmpty() && !loading) {
                    EmptyState(
                        if (total == 0) "No recordings found in\n$folder" else "Nothing matches your search",
                    )
                } else {
                    RecordingList(
                        recordings = recordings,
                        durations = durations,
                        showDateHeaders = sort == SortOrder.NEWEST || sort == SortOrder.OLDEST,
                        player = player,
                        onPlay = vm::playOrToggle,
                        onFocusNumber = { vm.numberFocus.value = it.phoneNumber },
                        onDelete = { pendingDelete = it },
                    )
                }
            }
        }
    }

    if (showFolderDialog) {
        FolderDialog(
            current = folder,
            onDismiss = { showFolderDialog = false },
            onSave = { vm.setFolder(it); showFolderDialog = false },
        )
    }

    pendingDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete recording?") },
            text = { Text("${rec.displayNumber}\n${formatDateTime(rec.timestamp)}\n\nThis can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(rec); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingList(
    recordings: List<Recording>,
    durations: Map<String, Long>,
    showDateHeaders: Boolean,
    player: PlayerState,
    onPlay: (Recording) -> Unit,
    onFocusNumber: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
) {
    val grouped = remember(recordings, showDateHeaders) {
        if (showDateHeaders) recordings.groupBy { dayLabel(it.timestamp) } else mapOf("" to recordings)
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        grouped.forEach { (label, items) ->
            if (label.isNotEmpty()) {
                stickyHeader(key = "h_$label") {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(vertical = 6.dp),
                    )
                }
            }
            items(items, key = { it.file.path }) { rec ->
                RecordingRow(
                    rec = rec,
                    durationMs = durations[rec.file.path],
                    isCurrent = player.recording?.file == rec.file,
                    isPlaying = player.recording?.file == rec.file && player.isPlaying,
                    onPlay = { onPlay(rec) },
                    onFocusNumber = { onFocusNumber(rec) },
                    onDelete = { onDelete(rec) },
                )
            }
        }
    }
}

@Composable
private fun RecordingRow(
    rec: Recording,
    durationMs: Long?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onFocusNumber: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val (icon, tint, dirLabel) = when (rec.direction) {
        Direction.INCOMING -> Triple(Icons.AutoMirrored.Filled.CallReceived, IncomingColor, "Incoming")
        Direction.OUTGOING -> Triple(Icons.AutoMirrored.Filled.CallMade, OutgoingColor, "Outgoing")
        Direction.UNKNOWN -> Triple(Icons.Default.QuestionMark, MaterialTheme.colorScheme.outline, "Call")
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
        colors = if (isCurrent) {
            androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            androidx.compose.material3.CardDefaults.elevatedCardColors()
        },
    ) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(tint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isPlaying) Icons.Default.GraphicEq else icon, dirLabel, tint = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    rec.displayNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        dirLabel,
                        formatTime(rec.timestamp),
                        durationMs?.let { formatDuration(it) },
                        Formatter.formatShortFileSize(context, rec.sizeBytes),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlay) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("All calls with this number") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        onClick = { menu = false; onFocusNumber() },
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            menu = false
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", rec.file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share recording"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBar(state: PlayerState, vm: RecordingsViewModel) {
    val rec = state.recording ?: return
    var dragging by remember { mutableStateOf<Float?>(null) }
    Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rec.displayNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        formatDateTime(rec.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = vm::stop) { Icon(Icons.Default.Close, "Close player") }
            }
            if (state.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                return@Column
            }
            val duration = state.durationMs.coerceAtLeast(1)
            Slider(
                value = dragging ?: state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { vm.seekTo(it.toInt()) }; dragging = null },
                valueRange = 0f..duration.toFloat(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration((dragging?.toLong() ?: state.positionMs.toLong())),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.skip(-10_000) }) { Icon(Icons.Default.Replay10, "Back 10 seconds") }
                FilledIconButton(onClick = vm::togglePause, modifier = Modifier.size(52.dp)) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
                }
                IconButton(onClick = { vm.skip(10_000) }) { Icon(Icons.Default.Forward10, "Forward 10 seconds") }
                Spacer(Modifier.weight(1f))
                Text(formatDuration(state.durationMs.toLong()), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun FolderDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recordings folder") },
        text = {
            Column {
                Text(
                    "Sub-folders are included too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = false)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = {
            TextButton(onClick = { text = com.shizuku.arranger.data.RecordingRepository.DEFAULT_FOLDER }) { Text("Reset") }
        },
    )
}

@Composable
private fun EmptyState(message: String) {
    // Wrapped in a LazyColumn so pull-to-refresh still works on an empty screen.
    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.padding(top = 96.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.GraphicEq, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

// ---- formatting helpers ----

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))

private fun formatDateTime(ts: Long): String =
    SimpleDateFormat("EEE, d MMM yyyy · h:mm a", Locale.getDefault()).format(Date(ts))

private fun dayLabel(ts: Long): String = when {
    DateUtils.isToday(ts) -> "Today"
    DateUtils.isToday(ts + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.YEAR) ==
        Calendar.getInstance().get(Calendar.YEAR) ->
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(ts))
    else -> SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(ts))
}
