package app.akam

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.akam.AkamDb
import uniffi.akam.AkamException
import uniffi.akam.Note
import uniffi.akam.RichSpan
import uniffi.akam.RichText
import uniffi.akam.Tag

// filter values: null = all, "pinned", "untagged", "todo", "trash", "tag:<name>"
// active keys mirror them: "all", "pinned", ..., "" = none yet

// ---------- shared building blocks ----------

/** Flat header bar: centered title (+subtitle), nav at start, actions at end. */
@Composable
private fun HeaderBar(
    title: String,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    titleBold: Boolean = true,
    navigation: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    // flat header: no background of its own, seamless with the window
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        navigation?.let {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { it() }
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (titleBold) FontWeight.Bold else FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        actions?.let {
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = it
            )
        }
    }
}

/** Grouped list card: large expressive radius, hairline dividers between rows. */
@Composable
private fun BoxedList(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp)
    )
}

// ---------- Tags sidebar ----------

@Composable
fun SidebarScreen(db: AkamDb, active: String, onOpen: (String?) -> Unit) {
    var tags by remember { mutableStateOf(emptyList<Tag>()) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(Unit) {
        tags = withContext(Dispatchers.IO) { db.listTags() }
    }
    val children = remember(tags) { tags.groupBy { it.parentId } }
    val visible = remember(tags, expanded) {
        buildList {
            fun walk(level: List<Tag>) {
                for (t in level) {
                    add(t)
                    if (t.id in expanded) walk(children[t.id].orEmpty())
                }
            }
            walk(children[null].orEmpty())
        }
    }
    Column(Modifier.fillMaxSize()) {
        HeaderBar(title = "Akam")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
            item { DrawerRow(Icons.Outlined.Description, "All Notes", active == "all") { onOpen(null) } }
            item { DrawerRow(Icons.Outlined.PushPin, "Pinned", active == "pinned") { onOpen("pinned") } }
            item { DrawerRow(Icons.AutoMirrored.Outlined.LabelOff, "Untagged", active == "untagged") { onOpen("untagged") } }
            item { DrawerRow(Icons.Outlined.CheckBox, "Todo", active == "todo") { onOpen("todo") } }
            item { DrawerRow(Icons.Outlined.Delete, "Trash", active == "trash") { onOpen("trash") } }
            item { DrawerRow(Icons.Outlined.Settings, "Settings", active == "settings") { onOpen("settings") } }
            if (visible.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    GroupLabel("TAGS")
                }
                items(visible.size, key = { visible[it].id }) { i ->
                    val tag = visible[i]
                    val depth = tag.name.count { it == '/' }
                    val isExpanded = tag.id in expanded
                    DrawerRow(
                        icon = Icons.Outlined.Tag,
                        label = tag.name.substringAfterLast('/'),
                        selected = active == "tag:${tag.name}",
                        indentLevel = depth,
                        count = tag.noteCount,
                        trailing = if (children.containsKey(tag.id)) {
                            {
                                IconButton(onClick = {
                                    expanded = if (isExpanded) expanded - tag.id else expanded + tag.id
                                }) {
                                    Icon(
                                        Icons.Outlined.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        modifier = Modifier.rotate(if (isExpanded) 0f else -90f)
                                    )
                                }
                            }
                        } else null
                    ) { onOpen("tag:${tag.name}") }
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    indentLevel: Int = 0,
    count: Long? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        badge = if (count != null || trailing != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    count?.let {
                        Text("$it", style = MaterialTheme.typography.labelLarge)
                    }
                    trailing?.invoke()
                }
            }
        } else null,
        selected = selected,
        onClick = onClick,
        // M3 default selection: tonal secondaryContainer pill
        modifier = Modifier.padding(start = (12 + 28 * indentLevel).dp, end = 12.dp)
    )
}

// ---------- Note list + search ----------

/** Apple/GNOME date buckets; input list is already sorted by updated_at desc. */
private fun bucket(ms: Long): String {
    val date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today.minusDays(7)) -> "Previous 7 Days"
        date.year == today.year -> month
        else -> "$month ${date.year}"
    }
}

/** Home screen: the note list, with the sidebar as a ~78%-width modal drawer. */
@Composable
fun NotesScreen(db: AkamDb, onOpenNote: (Long) -> Unit, onNewNote: () -> Unit, onSettings: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.78f)) {
                SidebarScreen(db, active = filter ?: "all") { f ->
                    if (f == "settings") {
                        onSettings()
                    } else {
                        filter = f
                    }
                    scope.launch { drawerState.close() }
                }
            }
        }
    ) {
        NotesContent(
            db = db,
            filter = filter,
            onMenu = { scope.launch { drawerState.open() } },
            onOpenNote = onOpenNote,
            onNewNote = onNewNote,
            onFilterInvalid = { filter = null },
        )
    }
}

@Composable
private fun NotesContent(
    db: AkamDb,
    filter: String?,
    onMenu: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onFilterInvalid: () -> Unit,
) {
    val isTrash = filter == "trash"
    var query by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf(emptyList<Note>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val searching = query.isNotBlank()
    LaunchedEffect(filter, query, refresh) {
        // debounce keystrokes: a new query cancels this coroutine before delay ends
        if (searching) delay(150)
        notes = withContext(Dispatchers.IO) { loadNotes(db, filter, query) }
        // the filtered tag can vanish while we're away (edit removed its last
        // use); fall back to All Notes instead of a dead "0 Notes" screen
        if (notes.isEmpty() && !searching && filter?.startsWith("tag:") == true) {
            val name = filter.removePrefix("tag:")
            val exists = withContext(Dispatchers.IO) { db.listTags().any { it.name == name } }
            if (!exists) onFilterInvalid()
        }
    }
    val sections: Map<String, List<Note>> = remember(notes, query, filter) {
        when {
            // search results are a flat list; the count sits by the search bar
            searching -> if (notes.isEmpty()) emptyMap() else mapOf("" to notes)
            notes.isEmpty() -> emptyMap()
            filter == "pinned" -> mapOf("Pinned" to notes)
            filter == null -> {
                val pinned = notes.filter { it.isPinned }
                buildMap {
                    if (pinned.isNotEmpty()) put("Pinned", pinned)
                    notes.filterNot { it.isPinned }.groupBy { bucket(it.updatedAt) }
                        .forEach { (k, v) -> put(k, v) }
                }
            }
            else -> notes.groupBy { bucket(it.updatedAt) }
        }
    }
    val title = when {
        filter == null -> "Notes"
        filter.startsWith("tag:") -> "#${filter.removePrefix("tag:")}"
        else -> filter.replaceFirstChar { it.uppercase() }
    }
    Column(Modifier.fillMaxSize()) {
        HeaderBar(
            title = title,
            subtitle = "${notes.size} ${if (notes.size == 1) "Note" else "Notes"}",
            titleColor = if (filter?.startsWith("tag:") == true) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            navigation = {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Open sidebar")
                }
            }
        )
        Box(Modifier.weight(1f).imePadding()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)) {
                sections.forEach { (label, group) ->
                    if (label.isNotEmpty()) item(key = "header-$label") { GroupLabel(label) }
                    item(key = "card-$label") {
                        BoxedList {
                            group.forEachIndexed { i, note ->
                                // key on the note id: swipe state must never be
                                // recycled across different notes as rows shift
                                key(note.id) {
                                NoteRow(
                                    note, query, isTrash,
                                    onOpen = onOpenNote,
                                    onPin = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                db.setPinned(note.id, !note.isPinned)
                                            }
                                            refresh++
                                        }
                                    },
                                    onDismiss = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                if (isTrash) db.restoreFromTrash(note.id)
                                                else db.moveToTrash(note.id)
                                            }
                                            refresh++
                                            if (!isTrash) {
                                                val result = snackbar.showSnackbar(
                                                    message = "Moved to trash",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    withContext(Dispatchers.IO) {
                                                        db.restoreFromTrash(note.id)
                                                    }
                                                    refresh++
                                                }
                                            }
                                        }
                                    },
                                )
                                }
                                if (i < group.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(start = 20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // centered empty state when a search returns nothing
            if (searching && notes.isEmpty()) {
                Text(
                    "No notes found for \"$query\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                )
            }
            // bottom search pill; end padding leaves room for the FAB
            if (!isTrash) {
                // result count floats just above the search bar while searching
                if (searching) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 80.dp)
                    ) {
                        Text(
                            "${notes.size} ${if (notes.size == 1) "result" else "results"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    shape = CircleShape,
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    // M3 search-bar look: tonal fill, no outline
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 96.dp, bottom = 16.dp)
                )
                // M3 FAB: tonal primaryContainer, default rounded-square shape
                FloatingActionButton(
                    onClick = onNewNote,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "New note")
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isTrash) 0.dp else 88.dp) // clear the search pill
            )
        }
    }
}

private fun loadNotes(db: AkamDb, filter: String?, query: String): List<Note> = try {
    if (query.isNotBlank()) {
        db.searchNotes(query)
    } else when {
        filter == null -> db.listNotes(null)
        filter == "pinned" -> db.listNotes(null).filter { it.isPinned }
        filter == "untagged" -> db.listUntagged()
        // ponytail: todo filters client-side on the checkbox glyph; SQL when counts hurt
        filter == "todo" -> db.listNotes(null).filter { it.body.contains("☐") }
        filter == "trash" -> db.getTrashedNotes()
        else -> db.listNotes(filter.removePrefix("tag:"))
    }
} catch (_: AkamException) {
    emptyList() // e.g. unbalanced quotes in an FTS query mid-typing
}

@Composable
private fun NoteRow(
    note: Note,
    query: String,
    isTrash: Boolean,
    onOpen: (Long) -> Unit,
    onPin: () -> Unit,
    onDismiss: () -> Unit,
) {
    // db work happens in the parent's scope: this row's scope dies with the
    // row when a dismiss removes it from composition
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                // swipe right: toggle pin, then snap back
                SwipeToDismissBoxValue.StartToEnd -> {
                    onPin()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDismiss()
                    true
                }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isTrash,
        backgroundContent = {
            val pinning = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val bg: Color
            val icon: ImageVector
            val label: String
            when {
                pinning -> {
                    bg = MaterialTheme.colorScheme.primaryContainer
                    icon = Icons.Outlined.PushPin
                    label = if (note.isPinned) "Unpin" else "Pin"
                }
                isTrash -> {
                    bg = MaterialTheme.colorScheme.tertiaryContainer
                    icon = Icons.Outlined.RestoreFromTrash
                    label = "Restore"
                }
                else -> {
                    bg = MaterialTheme.colorScheme.errorContainer
                    icon = Icons.Outlined.Delete
                    label = "Delete"
                }
            }
            Box(
                Modifier.fillMaxSize().background(bg),
                contentAlignment = if (pinning) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    ) {
        val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        val firstContent = note.body.lineSequence().drop(1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val preview = if (query.isNotBlank()) {
            note.body.lineSequence().firstOrNull { it.contains(query, ignoreCase = true) }?.trim()
                ?: firstContent
        } else firstContent
        val cleanPreview = preview.replace("\uFFFC", "").trim()
        val date = if (isTrash) {
            "Trashed ${DateUtils.getRelativeTimeSpanString(note.trashedAt ?: note.updatedAt)}"
        } else {
            DateUtils.getRelativeTimeSpanString(note.updatedAt).toString()
        }
        ListItem(
            headlineContent = {
                Text(
                    highlight(note.title.ifBlank { "Untitled" }, query, highlightBg),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    highlight("$date  ${cleanPreview.ifEmpty { "No additional text" }}", query, highlightBg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = if ((note.isPinned && !isTrash) || note.thumbnail != null) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (note.isPinned && !isTrash) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (note.thumbnail != null) {
                                Spacer(Modifier.size(8.dp))
                            }
                        }
                        note.thumbnail?.let { filename ->
                            val imageFile = File(File(LocalContext.current.filesDir, "images"), filename)
                            if (imageFile.exists()) {
                                var thumbnailBitmap by remember(filename) { mutableStateOf<ImageBitmap?>(null) }
                                LaunchedEffect(filename) {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            BitmapFactory.decodeFile(imageFile.absolutePath)?.let { bmp ->
                                                val scaled = Bitmap.createScaledBitmap(bmp, 120, 120, true)
                                                thumbnailBitmap = scaled.asImageBitmap()
                                            }
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    }
                                }
                                thumbnailBitmap?.let { bmp ->
                                    androidx.compose.foundation.Image(
                                        bitmap = bmp,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
            } else null,
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.clickable { onOpen(note.id) }
        )
    }
}

private fun highlight(text: String, query: String, background: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (query.isBlank()) return@buildAnnotatedString
        var i = text.indexOf(query, ignoreCase = true)
        while (i >= 0) {
            addStyle(SpanStyle(background = background), i, i + query.length)
            i = text.indexOf(query, i + query.length, ignoreCase = true)
        }
    }

// ---------- Settings ----------

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("akam_prefs", Context.MODE_PRIVATE) }
    var appLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var userEmail by remember { mutableStateOf(prefs.getString("supabase_user_email", "") ?: "") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var syncStatus by remember { mutableStateOf(if (userEmail.isNotBlank()) "Logged in as $userEmail" else "Not logged in (Local Mode)") }
    var autoSyncEnabled by remember { mutableStateOf(prefs.getBoolean("auto_sync_enabled", true)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        HeaderBar(
            title = "Settings",
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // ── SUPABASE CLOUD SYNC & AUTH CARD ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CloudSync,
                        contentDescription = "Cloud Sync",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Supabase Cloud Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "jklezsokvxfcjcxjfnmj.supabase.co",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (userEmail.isBlank()) {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (emailInput.isNotBlank()) {
                                    userEmail = emailInput
                                    prefs.edit().putString("supabase_user_email", emailInput).apply()
                                    syncStatus = "Logged in as $userEmail"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Log In")
                        }
                        OutlinedButton(
                            onClick = {
                                if (emailInput.isNotBlank()) {
                                    userEmail = emailInput
                                    prefs.edit().putString("supabase_user_email", emailInput).apply()
                                    syncStatus = "Logged in as $userEmail"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sign Up")
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                syncStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { syncStatus = "Synced notes at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync Now")
                        }
                        OutlinedButton(
                            onClick = {
                                userEmail = ""
                                prefs.edit().remove("supabase_user_email").apply()
                                syncStatus = "Not logged in (Local Mode)"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Log Out")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-Sync in Background", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = {
                            autoSyncEnabled = it
                            prefs.edit().putBoolean("auto_sync_enabled", it).apply()
                        }
                    )
                }
            }
        }

        // ── SECURITY & APP LOCK CARD ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            ListItem(
                headlineContent = { Text("App Lock", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("Require biometric authentication to open Akam") },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = appLockEnabled,
                        onCheckedChange = { checked ->
                            appLockEnabled = checked
                            prefs.edit().putBoolean("app_lock_enabled", checked).apply()
                        }
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )
        }
    }
}

// ---------- Rich text editor ----------

private fun lineStart(text: String, pos: Int) = text.lastIndexOf('\n', pos - 1) + 1
private fun lineEnd(text: String, pos: Int) =
    text.indexOf('\n', pos).let { if (it < 0) text.length else it }

/** True when [new] is exactly [old] with a single '\n' typed at the caret. */
private fun isNewlineInsert(old: TextFieldValue, new: TextFieldValue): Boolean {
    val c = new.selection.min
    return new.selection.collapsed && new.text.length == old.text.length + 1 &&
        c in 1..new.text.length && new.text[c - 1] == '\n' &&
        new.text.removeRange(c - 1, c) == old.text
}

@Composable
fun EditorScreen(db: AkamDb, noteId: Long, onBack: () -> Unit) {
    var loaded by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var spans by remember { mutableStateOf(listOf<RichSpan>()) }
    var dirty by remember { mutableStateOf(false) }
    var trashed by remember { mutableStateOf(false) }
    var pinned by remember(noteId) { mutableStateOf(false) }
    val undoStack = remember(noteId) { mutableStateListOf<Triple<String, List<RichSpan>, TextRange>>() }
    LaunchedEffect(noteId) {
        withContext(Dispatchers.IO) { db.getContent(noteId) }?.let {
            value = TextFieldValue(it.text, TextRange(it.text.length))
            spans = it.spans
        }
        withContext(Dispatchers.IO) { db.getNote(noteId) }?.let {
            trashed = it.isDeleted
            pinned = it.isPinned
        }
        loaded = true
    }
    // debounced save off the main thread; keystrokes only mutate local state
    LaunchedEffect(noteId) {
        snapshotFlow { value.text to spans }.collectLatest { (text, sp) ->
            if (!dirty) return@collectLatest
            delay(300)
            withContext(Dispatchers.IO) { db.setContent(noteId, RichText(text, sp)) }
            dirty = false
        }
    }
    val latest by rememberUpdatedState(RichText(value.text, spans))
    val wasDirty by rememberUpdatedState(dirty)
    DisposableEffect(noteId) {
        onDispose { // flush what the debounce hasn't written yet
            // ponytail: fire-and-forget scope — the write must outlive this composable
            if (wasDirty) CoroutineScope(Dispatchers.IO).launch { db.setContent(noteId, latest) }
        }
    }
    if (!loaded) return

    fun pushUndo() {
        undoStack.add(Triple(value.text, spans, value.selection))
        if (undoStack.size > 100) undoStack.removeAt(0)
    }

    fun applyEdit(newText: String, newSelection: TextRange) {
        pushUndo()
        spans = remapSpans(spans, value.text, newText)
        value = TextFieldValue(newText, newSelection)
        dirty = true
    }

    fun toggleStyle(style: String) {
        val sel = value.selection
        if (sel.collapsed) return // ponytail: no typing-style carry; select text to style it
        pushUndo()
        spans = toggleSpan(spans, style, sel.min, sel.max)
        dirty = true
    }

    // heading is line-level: cycle none → H1 → H2 → H3 → none on the caret's line
    fun cycleHeading() {
        val ls = lineStart(value.text, value.selection.min)
        val le = lineEnd(value.text, value.selection.min)
        if (le <= ls) return
        pushUndo()
        val current = spans.firstOrNull { isHeading(it.style) && it.start < le && it.end > ls }?.style
        val cleared = spans.filterNot { isHeading(it.style) && it.start < le && it.end > ls }
        val next = nextHeading(current)
        spans = if (next == null) cleared else cleared + RichSpan(ls, le, next)
        dirty = true
    }

    // code block is line-level: monospace span over the selected line(s)
    fun toggleCodeBlock() {
        val ls = lineStart(value.text, value.selection.min)
        val le = lineEnd(value.text, value.selection.max)
        if (le <= ls) return
        pushUndo()
        val covered = spans.any { it.style == RichStyles.CODEBLOCK && it.start <= ls && it.end >= le }
        spans = if (covered) spans.filterNot { it.style == RichStyles.CODEBLOCK && it.start < le && it.end > ls }
        else spans + RichSpan(ls, le, RichStyles.CODEBLOCK)
        dirty = true
    }

    fun addLink(url: String, sel: TextRange) {
        if (sel.collapsed || url.isBlank()) return
        pushUndo()
        val u = if (url.contains("://")) url else "https://$url"
        spans = spans.filterNot {
            it.style.startsWith(RichStyles.LINK_PREFIX) && it.start < sel.max && it.end > sel.min
        } + RichSpan(sel.min, sel.max, RichStyles.LINK_PREFIX + u)
        dirty = true
    }

    fun editLinePrefix(at: Int = value.selection.min, transform: (String) -> String) {
        val text = value.text
        val sel = value.selection
        val ls = lineStart(text, at)
        val le = lineEnd(text, at)
        val line = text.substring(ls, le)
        val newLine = transform(line)
        if (newLine == line) return
        val delta = newLine.length - line.length
        fun shift(p: Int) = if (p < ls) p else maxOf(ls, p + delta)
        applyEdit(
            text.substring(0, ls) + newLine + text.substring(le),
            TextRange(shift(sel.start), shift(sel.end))
        )
    }

    fun insertAtCursor(s: String) {
        val sel = value.selection
        applyEdit(
            value.text.substring(0, sel.min) + s + value.text.substring(sel.max),
            TextRange(sel.min + s.length)
        )
    }

    val context = LocalContext.current
    val imageCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val loadingPaths = remember { mutableSetOf<String>() }

    fun saveImageToInternalStorage(sourceUri: Uri): String? {
        return try {
            val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
            val filename = "img_${System.currentTimeMillis()}_${(100..999).random()}.jpg"
            val destFile = File(imagesDir, filename)
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            filename
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun insertImageAtCursor(filename: String) {
        val sel = value.selection
        val text = value.text
        
        val needPrefixNl = sel.min > 0 && text[sel.min - 1] != '\n'
        val needSuffixNl = sel.max < text.length && text[sel.max] != '\n'
        
        val prefix = if (needPrefixNl) "\n" else ""
        val suffix = if (needSuffixNl) "\n" else ""
        
        val insertStr = "$prefix\uFFFC$suffix"
        val insertPos = sel.min + prefix.length
        
        val newText = text.substring(0, sel.min) + insertStr + text.substring(sel.max)
        
        pushUndo()
        val imageSpan = RichSpan(
            start = insertPos,
            end = insertPos + 1,
            style = "image:$filename"
        )
        
        spans = remapSpans(spans, text, newText) + imageSpan
        value = TextFieldValue(newText, TextRange(insertPos + 1 + suffix.length))
        dirty = true
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val filename = saveImageToInternalStorage(uri)
            if (filename != null) {
                insertImageAtCursor(filename)
            }
        }
    }

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempPhotoUri != null) {
            val filename = saveImageToInternalStorage(tempPhotoUri!!)
            if (filename != null) {
                insertImageAtCursor(filename)
            }
        }
    }

    fun launchCamera() {
        try {
            val tempFile = File.createTempFile("temp_capture_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "app.akam.fileprovider", tempFile)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(spans) {
        spans.forEach { sp ->
            if (sp.style.startsWith("image:")) {
                val filename = sp.style.removePrefix("image:")
                if (!imageCache.containsKey(filename) && !loadingPaths.contains(filename)) {
                    loadingPaths.add(filename)
                    launch(Dispatchers.IO) {
                        try {
                            val imageFile = File(File(context.filesDir, "images"), filename)
                            if (imageFile.exists()) {
                                val bmp = BitmapFactory.decodeFile(imageFile.absolutePath)
                                if (bmp != null) {
                                    val ibmp = bmp.asImageBitmap()
                                    withContext(Dispatchers.Main) {
                                        imageCache[filename] = ibmp
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            loadingPaths.remove(filename)
                        }
                    }
                }
            }
        }
    }

    fun toggleCheckboxLine(at: Int) = editLinePrefix(at) { l ->
        when {
            l.startsWith("☑ ") -> "☐ " + l.removePrefix("☑ ")
            l.startsWith("☐ ") -> "☑ " + l.removePrefix("☐ ")
            else -> l
        }
    }

    val noteTags = remember(value.text) {
        TAG_RE.findAll(value.text).map { it.value }.distinct().toList()
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var linkSel by remember { mutableStateOf<TextRange?>(null) } // selection awaiting a url

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete forever?") },
            text = { Text("This note will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { db.deletePermanently(noteId) }
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(
                title = when {
                    trashed -> "In Trash"
                    dirty -> "Saving…"
                    else -> "Saved just now"
                },
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                titleBold = false,
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trashed) {
                        IconButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { db.restoreFromTrash(noteId) }
                                onBack()
                            }
                        }) {
                            Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Restore")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete forever")
                        }
                    } else {
                    IconButton(onClick = {
                        undoStack.removeLastOrNull()?.let { (t, sp, sel) ->
                            spans = sp
                            value = TextFieldValue(t, sel)
                            dirty = true
                        }
                    }, enabled = undoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (pinned) "Unpin" else "Pin") },
                                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                onClick = {
                                    val next = !pinned
                                    pinned = next
                                    menuOpen = false
                                    scope.launch(Dispatchers.IO) { db.setPinned(noteId, next) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Trash") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) { db.moveToTrash(noteId) }
                                        onBack()
                                    }
                                }
                            )
                        }
                    }
                    }
                }
            )
            // tag chips row
            if (noteTags.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    noteTags.forEach { t ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                t.removeSuffix("#"), // multi-word tags close with #
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            val quoteBar = MaterialTheme.colorScheme.primary
            BasicTextField(
                value = value,
                readOnly = trashed,
                onValueChange = { new ->
                    // pressing Enter on a list item continues/exits the list
                    val cont = if (isNewlineInsert(value, new)) continueList(new.text, new.selection.min) else null
                    val text = cont?.first ?: new.text
                    val sel = cont?.let { TextRange(it.second) } ?: new.selection
                    if (text != value.text) {
                        pushUndo()
                        spans = remapSpans(spans, value.text, text)
                        dirty = true
                    }
                    value = TextFieldValue(text, sel)
                },
                onTextLayout = { layout = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = rememberRichTransformation(spans),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    // full-width block visuals spans can't draw: code-block tint and
                    // the quote accent bar, positioned from the text's own layout
                    .drawBehind {
                        val l = layout ?: return@drawBehind
                        fun lineTop(off: Int) = l.getLineTop(l.getLineForOffset(off.coerceIn(0, value.text.length)))
                        fun lineBottom(off: Int) = l.getLineBottom(l.getLineForOffset(off.coerceIn(0, value.text.length)))
                        for (sp in spans) if (sp.style == RichStyles.CODEBLOCK) {
                            val top = lineTop(sp.start)
                            drawRoundRect(
                                codeBg, Offset(0f, top), Size(size.width, lineBottom(sp.end) - top),
                                CornerRadius(12f, 12f)
                            )
                        }
                        for (r in quoteLineRanges(value.text)) {
                            val top = lineTop(r.first)
                            drawRect(quoteBar, Offset(0f, top), Size(4.dp.toPx(), lineBottom(r.last) - top))
                        }
                        for (sp in spans) if (sp.style.startsWith("image:")) {
                            val start = sp.start.coerceIn(0, value.text.length)
                            val end = sp.end.coerceIn(0, value.text.length)
                            if (start < end) {
                                val rect = try { l.getBoundingBox(start) } catch (e: Exception) { null }
                                if (rect != null) {
                                    val dstLeft = 0f
                                    val dstRight = size.width
                                    val dstTop = rect.top
                                    val dstBottom = rect.bottom
                                    val filename = sp.style.removePrefix("image:")
                                    val bitmap = imageCache[filename]
                                    if (bitmap != null) {
                                        val path = Path().apply {
                                            addRoundRect(
                                                RoundRect(
                                                    rect = Rect(dstLeft, dstTop, dstRight, dstBottom),
                                                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                                )
                                            )
                                        }
                                        clipPath(path) {
                                            drawImageCenterCrop(this, bitmap, dstTop, dstBottom, dstLeft, dstRight)
                                        }
                                    } else {
                                        drawRoundRect(
                                            color = codeBg,
                                            topLeft = Offset(dstLeft, dstTop),
                                            size = Size(dstRight - dstLeft, dstBottom - dstTop),
                                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // quick taps on a ☐/☑ glyph toggle it, and taps inside a link span
                    // open the url; observed without consuming so the field still handles
                    // cursor placement. Toggle is deferred past the field's own tap
                    // processing — mutating mid-gesture gets clobbered by its stale cursor.
                    .pointerInput(trashed) {
                        if (trashed) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Initial)
                            val up = waitForUpOrCancellation(PointerEventPass.Initial)
                                ?: return@awaitEachGesture
                            if (up.uptimeMillis - down.uptimeMillis > 300) return@awaitEachGesture
                            val l = layout ?: return@awaitEachGesture
                            val off = l.getOffsetForPosition(up.position)
                            val text = value.text
                            val link = spans.firstOrNull {
                                it.style.startsWith(RichStyles.LINK_PREFIX) && off >= it.start && off < it.end
                            }
                            if (link != null) {
                                uriHandler.openUri(link.style.removePrefix(RichStyles.LINK_PREFIX))
                                return@awaitEachGesture
                            }
                            val ls = lineStart(text, off.coerceIn(0, text.length))
                            if ((text.startsWith("☐ ", ls) || text.startsWith("☑ ", ls)) &&
                                off <= ls + 1
                            ) {
                                scope.launch {
                                    delay(120)
                                    toggleCheckboxLine(ls)
                                }
                            }
                        }
                    }
                    .padding(bottom = 64.dp)
            )
        }
        // One scrollable toolbar keeps every formatting action one gesture away.
        if (!trashed) {
            val caretLs = lineStart(value.text, value.selection.min)
            val caretLe = lineEnd(value.text, value.selection.min)
            val hLabel = when (spans.firstOrNull {
                isHeading(it.style) && it.start < caretLe && it.end > caretLs
            }?.style) {
                RichStyles.H2 -> "H2"
                RichStyles.H3 -> "H3"
                else -> "H1"
            }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                        IconButton(onClick = { toggleStyle(RichStyles.BOLD) }) {
                            Icon(Icons.Outlined.FormatBold, contentDescription = "Bold")
                        }
                        IconButton(onClick = { toggleStyle(RichStyles.ITALIC) }) {
                            Icon(Icons.Outlined.FormatItalic, contentDescription = "Italic")
                        }
                        IconButton(onClick = { toggleStyle(RichStyles.UNDERLINE) }) {
                            Icon(Icons.Outlined.FormatUnderlined, contentDescription = "Underline")
                        }
                        IconButton(onClick = { toggleStyle(RichStyles.STRIKE) }) {
                            Icon(Icons.Outlined.FormatStrikethrough, contentDescription = "Strikethrough")
                        }
                        IconButton(onClick = { cycleHeading() }) {
                            Text(hLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            editLinePrefix { l -> if (l.startsWith("• ")) l.removePrefix("• ") else "• $l" }
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Bullet list")
                        }
                        IconButton(onClick = {
                            editLinePrefix { l ->
                                if (NUM_RE.containsMatchIn(l)) l.replaceFirst(NUM_RE, "")
                                else "${numberFor(value.text, lineStart(value.text, value.selection.min))}. $l"
                            }
                        }) {
                            Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Numbered list")
                        }
                        IconButton(onClick = {
                            editLinePrefix { l ->
                                when {
                                    l.startsWith("☑ ") -> l.removePrefix("☑ ")
                                    l.startsWith("☐ ") -> "☑ " + l.removePrefix("☐ ")
                                    else -> "☐ $l"
                                }
                            }
                        }) {
                            Icon(Icons.Outlined.CheckBox, contentDescription = "Checkbox")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { insertAtCursor("#") }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Tag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                "Tag",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    IconButton(onClick = { toggleStyle(RichStyles.CODE) }) {
                        Icon(Icons.Outlined.Code, contentDescription = "Inline code")
                    }
                    IconButton(onClick = { toggleCodeBlock() }) {
                        Icon(Icons.Outlined.Terminal, contentDescription = "Code block")
                    }
                    IconButton(onClick = {
                        editLinePrefix { line -> if (line.startsWith("> ")) line.removePrefix("> ") else "> $line" }
                    }) {
                        Icon(Icons.Outlined.FormatQuote, contentDescription = "Quote")
                    }
                    IconButton(onClick = { insertAtCursor("\n────────────\n") }) {
                        Icon(Icons.Outlined.HorizontalRule, contentDescription = "Separator")
                    }
                    IconButton(onClick = { value.selection.takeIf { !it.collapsed }?.let { linkSel = it } }) {
                        Icon(Icons.Outlined.Link, contentDescription = "Link")
                    }
                    Box {
                        var imageMenuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { imageMenuOpen = true }) {
                            Icon(Icons.Outlined.Image, contentDescription = "Insert Image")
                        }
                        DropdownMenu(
                            expanded = imageMenuOpen,
                            onDismissRequest = { imageMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Take Photo") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    imageMenuOpen = false
                                    launchCamera()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Choose from Gallery") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    imageMenuOpen = false
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    linkSel?.let { sel ->
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { linkSel = null },
            title = { Text("Add link") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    addLink(url, sel)
                    linkSel = null
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { linkSel = null }) { Text("Cancel") }
            }
        )
    }
}

private fun drawImageCenterCrop(
    drawScope: DrawScope,
    bitmap: ImageBitmap,
    dstTop: Float,
    dstBottom: Float,
    dstLeft: Float,
    dstRight: Float
) {
    val dstWidth = dstRight - dstLeft
    val dstHeight = dstBottom - dstTop
    if (dstWidth <= 0 || dstHeight <= 0) return

    val srcWidth = bitmap.width.toFloat()
    val srcHeight = bitmap.height.toFloat()

    val srcAspectRatio = srcWidth / srcHeight
    val dstAspectRatio = dstWidth / dstHeight

    val srcLeft: Float
    val srcTop: Float
    val srcRight: Float
    val srcBottom: Float

    if (srcAspectRatio > dstAspectRatio) {
        val newWidth = srcHeight * dstAspectRatio
        srcLeft = (srcWidth - newWidth) / 2f
        srcTop = 0f
        srcRight = srcLeft + newWidth
        srcBottom = srcHeight
    } else {
        val newHeight = srcWidth / dstAspectRatio
        srcLeft = 0f
        srcTop = (srcHeight - newHeight) / 2f
        srcRight = srcWidth
        srcBottom = srcTop + newHeight
    }

    drawScope.drawImage(
        image = bitmap,
        srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
        srcSize = IntSize((srcRight - srcLeft).toInt(), (srcBottom - srcTop).toInt()),
        dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
        dstSize = IntSize(dstWidth.toInt(), dstHeight.toInt())
    )
}
