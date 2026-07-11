package app.akam

import android.text.format.DateUtils
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
fun NotesScreen(db: AkamDb, onOpenNote: (Long) -> Unit, onNewNote: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.78f)) {
                SidebarScreen(db, active = filter ?: "all") { f ->
                    filter = f
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
                    highlight("$date  ${preview.ifEmpty { "No additional text" }}", query, highlightBg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = if (note.isPinned && !isTrash) {
                {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

// ---------- Rich text editor ----------

private fun lineStart(text: String, pos: Int) = text.lastIndexOf('\n', pos - 1) + 1
private fun lineEnd(text: String, pos: Int) =
    text.indexOf('\n', pos).let { if (it < 0) text.length else it }

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

    fun toggleHeading() {
        val ls = lineStart(value.text, value.selection.min)
        val le = lineEnd(value.text, value.selection.min)
        if (le > ls) {
            pushUndo()
            spans = toggleSpan(spans, RichStyles.HEADING, ls, le)
            dirty = true
        }
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
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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
            BasicTextField(
                value = value,
                readOnly = trashed,
                onValueChange = { new ->
                    if (new.text != value.text) {
                        pushUndo()
                        spans = remapSpans(spans, value.text, new.text)
                        dirty = true
                    }
                    value = new
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
                    // quick taps on a ☐/☑ glyph toggle it; observed without consuming so
                    // the field still handles cursor placement. Toggle is deferred past
                    // the field's own tap processing — mutating mid-gesture gets clobbered
                    // by the field's stale cursor update.
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
        // formatting toolbar: B I U H1 bullets checkbox #Tag (hidden in trash)
        if (!trashed) Surface(
            shape = CircleShape,
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
                IconButton(onClick = { toggleHeading() }) {
                    Text("H1", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = {
                    editLinePrefix { l -> if (l.startsWith("• ")) l.removePrefix("• ") else "• $l" }
                }) {
                    Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Bullet list")
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
            }
        }
    }
}
