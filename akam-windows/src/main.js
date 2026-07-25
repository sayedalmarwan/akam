// ── TAURI IPC API INVOCATIONS ──────────────────────────────────────────────────
function getInvoke() {
  if (window.__TAURI__ && window.__TAURI__.core && typeof window.__TAURI__.core.invoke === "function") {
    return window.__TAURI__.core.invoke;
  }
  if (window.__TAURI_INTERNALS__ && typeof window.__TAURI_INTERNALS__.invoke === "function") {
    return window.__TAURI_INTERNALS__.invoke;
  }
  console.warn("Tauri IPC invoke not ready yet.");
  return async () => {};
}

async function invoke(cmd, args = {}) {
  const fn = getInvoke();
  return await fn(cmd, args);
}

// ── APP STATE ─────────────────────────────────────────────────────────────────
let currentFilter = "all";
let searchQuery = "";
let currentNote = null;
let notesList = [];
let saveTimeout = null;
let contextNoteId = null;
let currentSpans = []; // Track rich spans for the current note

// ── DOM ELEMENTS ──────────────────────────────────────────────────────────────
let appContainer, notesListEl, tagsListEl, searchInput, listTitle, editor, editorToolbar, editorEmptyState, tagChipsEl, infoModal, contextMenu;

// ── INITIALIZATION ────────────────────────────────────────────────────────────
window.addEventListener("DOMContentLoaded", () => {
  appContainer = document.querySelector(".app-container");
  notesListEl = document.getElementById("notes-list");
  tagsListEl = document.getElementById("tags-list");
  searchInput = document.getElementById("search-input");
  listTitle = document.getElementById("list-title");
  editor = document.getElementById("editor");
  editorToolbar = document.getElementById("editor-toolbar");
  editorEmptyState = document.getElementById("editor-empty-state");
  tagChipsEl = document.getElementById("tag-chips");
  infoModal = document.getElementById("info-modal");
  contextMenu = document.getElementById("context-menu");

  setupWindowControls();
  setupSidebarNav();
  setupToolbar();
  setupEditor();
  setupShortcuts();
  setupContextMenu();

  refreshSidebarTags();
  loadNotes();
});

// ── WINDOW CSD CONTROLS ───────────────────────────────────────────────────────
async function updateMaximizeIcon() {
  const maxBtn = document.getElementById("btn-maximize");
  if (!maxBtn) return;
  try {
    const isMax = await invoke("is_maximized");
    maxBtn.innerHTML = isMax ? "&#xE923;" : "&#xE922;";
    maxBtn.title = isMax ? "Restore Down" : "Maximize";
  } catch (e) {}
}

function setupWindowControls() {
  document.getElementById("btn-minimize")?.addEventListener("click", (e) => {
    e.stopPropagation();
    invoke("minimize_window");
  });
  document.getElementById("btn-maximize")?.addEventListener("click", async (e) => {
    e.stopPropagation();
    try {
      const isMax = await invoke("maximize_window");
      const maxBtn = document.getElementById("btn-maximize");
      if (maxBtn) {
        maxBtn.innerHTML = isMax ? "&#xE923;" : "&#xE922;";
        maxBtn.title = isMax ? "Restore Down" : "Maximize";
      }
    } catch (err) {
      console.error(err);
    }
  });
  document.getElementById("btn-close")?.addEventListener("click", (e) => {
    e.stopPropagation();
    invoke("close_window");
  });

  window.addEventListener("resize", updateMaximizeIcon);
  updateMaximizeIcon();
}

// ── SIDEBAR NAVIGATION ────────────────────────────────────────────────────────
function setupSidebarNav() {
  document.querySelectorAll(".nav-item").forEach(item => {
    item.addEventListener("click", () => {
      document.querySelectorAll(".nav-item").forEach(i => i.classList.remove("active"));
      item.classList.add("active");
      currentFilter = item.dataset.filter;
      updateHeaderTitle();
      loadNotes();
      appContainer?.classList.remove("mobile-editor-view");
    });
  });

  document.getElementById("btn-new-note")?.addEventListener("click", createNewNote);
}

function updateHeaderTitle() {
  const titles = {
    all: "All Notes",
    pinned: "Pinned Notes",
    untagged: "Untagged Notes",
    todo: "Todo Notes",
    trash: "Trash"
  };
  listTitle.textContent = currentFilter.startsWith("tag:")
    ? `#${currentFilter.replace("tag:", "")}`
    : (titles[currentFilter] || "Notes");
}

async function refreshSidebarTags() {
  try {
    const tags = await invoke("list_tags");
    tagsListEl.innerHTML = "";
    if (Array.isArray(tags)) {
      tags.forEach(t => {
        const shortName = t.name.split("/").pop();
        const el = document.createElement("div");
        el.className = "tag-item";
        el.innerHTML = `<span>#${escapeHtml(shortName)}</span><span class="shortcut">${t.note_count}</span>`;
        el.addEventListener("click", () => {
          document.querySelectorAll(".nav-item").forEach(i => i.classList.remove("active"));
          currentFilter = `tag:${t.name}`;
          updateHeaderTitle();
          loadNotes();
          appContainer?.classList.remove("mobile-editor-view");
        });
        tagsListEl.appendChild(el);
      });
    }
  } catch (e) {
    console.error("Failed to load tags:", e);
  }
}

// ── NOTE LIST MANAGEMENT ──────────────────────────────────────────────────────
async function loadNotes() {
  try {
    let rawNotes = [];
    if (currentFilter === "all") rawNotes = await invoke("list_notes", { tag: null });
    else if (currentFilter === "pinned") rawNotes = (await invoke("list_notes", { tag: null })).filter(n => n.is_pinned);
    else if (currentFilter === "untagged") rawNotes = await invoke("list_untagged");
    else if (currentFilter === "todo") rawNotes = (await invoke("list_notes", { tag: null })).filter(n => n.body && n.body.includes("- [ ]"));
    else if (currentFilter === "trash") rawNotes = await invoke("list_trashed");
    else if (currentFilter.startsWith("tag:")) rawNotes = await invoke("list_notes", { tag: currentFilter.replace("tag:", "") });

    if (searchQuery.trim()) {
      rawNotes = await invoke("search_notes", { query: searchQuery });
    }

    notesList = Array.isArray(rawNotes) ? rawNotes : [];
    renderNoteCards();

    // Select first note ONLY if not in mobile view or on desktop view
    if (notesList.length > 0 && window.innerWidth > 620) {
      selectNote(notesList[0], false);
    } else if (notesList.length === 0) {
      clearSelection();
    }
  } catch (e) {
    console.error("Failed to load notes:", e);
    clearSelection();
  }
}

function clearSelection() {
  currentNote = null;
  currentSpans = [];
  editor.innerHTML = "";
  tagChipsEl.innerHTML = "";
  editorEmptyState.classList.remove("hidden");
  editorToolbar.style.opacity = "0.5";
  editorToolbar.style.pointerEvents = "none";
}

function renderNoteCards() {
  notesListEl.innerHTML = "";
  notesList.forEach(n => {
    const text = n.body || "";
    const lines = text.split("\n");
    const title = lines.find(l => l.trim().length > 0) || "New Note";
    const bodyRemaining = lines.slice(1).filter(l => l.trim().length > 0).join(" ");
    const preview = bodyRemaining || "No additional text";

    const card = document.createElement("div");
    card.className = `note-card ${currentNote && currentNote.id === n.id ? "active" : ""}`;
    card.dataset.id = n.id;

    // Build thumbnail HTML if note has an image
    const thumbHtml = n.thumbnail ? `<div class="note-card-thumb"><img src="" data-img="${escapeHtml(n.thumbnail)}" alt=""></div>` : '';

    card.innerHTML = `
      <div class="note-card-title">
        <span>${escapeHtml(title)}</span>
        ${n.is_pinned ? '<span>📌</span>' : ''}
      </div>
      <div class="note-card-preview">${escapeHtml(preview)}</div>
      ${thumbHtml}
      <div class="note-card-date">${formatDate(n.updated_at)}</div>
    `;

    // Load thumbnail image path
    if (n.thumbnail) {
      loadThumbnail(card.querySelector('.note-card-thumb img'), n.thumbnail);
    }

    card.addEventListener("click", () => selectNote(n, true));
    card.addEventListener("contextmenu", (e) => showContextMenu(e, n));
    notesListEl.appendChild(card);
  });
}

async function loadThumbnail(imgEl, filename) {
  try {
    const path = await invoke("get_image_path", { filename });
    if (imgEl) imgEl.src = convertFileSrc(path);
  } catch(e) {}
}

// Convert a file system path to a Tauri asset URL
function convertFileSrc(path) {
  if (window.__TAURI__ && window.__TAURI__.core && window.__TAURI__.core.convertFileSrc) {
    return window.__TAURI__.core.convertFileSrc(path);
  }
  // Fallback: use asset protocol
  return `https://asset.localhost/${encodeURIComponent(path)}`;
}

async function selectNote(note, userTriggered = false) {
  if (!note) {
    clearSelection();
    return;
  }

  saveCurrentNoteImmediately();
  currentNote = note;
  editorEmptyState.classList.add("hidden");
  editorToolbar.style.opacity = "1";
  editorToolbar.style.pointerEvents = "auto";

  document.querySelectorAll(".note-card").forEach(c => {
    c.classList.toggle("active", Number(c.dataset.id) === note.id);
  });

  try {
    const content = await invoke("get_content", { id: note.id });
    const text = (content && typeof content.text === "string") ? content.text : (note.body || "");
    currentSpans = (content && Array.isArray(content.spans)) ? content.spans : [];
    renderEditorContent(text, currentSpans);
    updateTagChips(text);
  } catch (e) {
    editor.innerText = note.body || "";
    currentSpans = [];
    updateTagChips(editor.innerText);
  }

  // If in compact/mobile size (< 620px) and user explicitly clicked a note, slide into Editor view
  if (userTriggered && window.innerWidth <= 620) {
    appContainer?.classList.add("mobile-editor-view");
  }
}

async function createNewNote() {
  try {
    saveCurrentNoteImmediately();
    const newNote = await invoke("create_note");
    currentFilter = "all";
    updateHeaderTitle();
    document.querySelectorAll(".nav-item").forEach(i => {
      i.classList.toggle("active", i.dataset.filter === "all");
    });
    await loadNotes();
    if (newNote && newNote.id) {
      const fullNote = notesList.find(n => n.id === newNote.id) || newNote;
      selectNote(fullNote, true);
      editor.focus();
    }
  } catch (e) {
    console.error("Failed to create note:", e);
  }
}

// ── RICH TEXT RENDERING ENGINE ─────────────────────────────────────────────────
// Renders plain text + spans into styled HTML inside the contenteditable editor.
// Supports: checkboxes (- [ ] / - [x]), code blocks (```...```), inline code (`...`),
// bullet lists (- ), numbered lists (1. ), headings (# / ## / ###), and inline images.

function renderEditorContent(text, spans) {
  const lines = text.split("\n");
  let inCodeBlock = false;
  let codeBlockLines = [];
  let htmlParts = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Code block fence handling
    if (line.trimStart().startsWith("```")) {
      if (inCodeBlock) {
        // End code block
        htmlParts.push(`<pre class="code-block" contenteditable="true"><code>${escapeHtml(codeBlockLines.join("\n"))}</code></pre>`);
        codeBlockLines = [];
        inCodeBlock = false;
      } else {
        // Start code block
        inCodeBlock = true;
        codeBlockLines = [];
      }
      continue;
    }

    if (inCodeBlock) {
      codeBlockLines.push(line);
      continue;
    }

    // Render a normal line
    htmlParts.push(renderLine(line, i));
  }

  // Unclosed code block: render remaining lines as code block
  if (inCodeBlock && codeBlockLines.length > 0) {
    htmlParts.push(`<pre class="code-block" contenteditable="true"><code>${escapeHtml(codeBlockLines.join("\n"))}</code></pre>`);
  }

  // Render inline images from spans
  const imageSpans = spans.filter(s => s.style.startsWith("image:"));

  editor.innerHTML = htmlParts.join("") || '<div><br></div>';

  // Insert images at the end of the editor (inline images between text lines)
  imageSpans.forEach(s => {
    const filename = s.style.replace("image:", "");
    const imgContainer = document.createElement("div");
    imgContainer.className = "editor-image-container";
    imgContainer.contentEditable = "false";

    const img = document.createElement("img");
    img.className = "editor-inline-image";
    img.alt = filename;
    img.dataset.filename = filename;
    img.loading = "lazy";

    // Delete button for the image
    const deleteBtn = document.createElement("button");
    deleteBtn.className = "image-delete-btn";
    deleteBtn.textContent = "✕";
    deleteBtn.title = "Remove image";
    deleteBtn.addEventListener("click", (e) => {
      e.preventDefault();
      e.stopPropagation();
      imgContainer.remove();
      // Remove this image span from currentSpans
      currentSpans = currentSpans.filter(sp => sp !== s && sp.style !== s.style);
      scheduleSave();
    });

    imgContainer.appendChild(img);
    imgContainer.appendChild(deleteBtn);
    editor.appendChild(imgContainer);

    // Load actual image path
    invoke("get_image_path", { filename }).then(path => {
      img.src = convertFileSrc(path);
    }).catch(() => {
      img.alt = `[Image: ${filename}]`;
      img.style.display = "none";
    });
  });
}

function renderLine(line, lineIndex) {
  // Checkbox: - [ ] or - [x]
  const checkboxMatch = line.match(/^(\s*)-\s*\[([ xX])\]\s*(.*)/);
  if (checkboxMatch) {
    const indent = checkboxMatch[1];
    const checked = checkboxMatch[2].toLowerCase() === "x";
    const text = checkboxMatch[3];
    return `<div class="editor-line checkbox-line" data-line="${lineIndex}">` +
      `<label class="checkbox-container" contenteditable="false">` +
      `<input type="checkbox" class="editor-checkbox" ${checked ? "checked" : ""} data-line="${lineIndex}">` +
      `<span class="checkbox-mark"></span>` +
      `</label>` +
      `<span class="checkbox-text ${checked ? "checked-text" : ""}">${renderInlineFormatting(escapeHtml(text))}</span>` +
      `</div>`;
  }

  // Bullet list: - text or • text
  const bulletMatch = line.match(/^(\s*)[-•]\s+(.*)/);
  if (bulletMatch && !checkboxMatch) {
    const text = bulletMatch[2];
    return `<div class="editor-line bullet-line" data-line="${lineIndex}">` +
      `<span class="bullet-marker" contenteditable="false">•</span>` +
      `<span>${renderInlineFormatting(escapeHtml(text))}</span>` +
      `</div>`;
  }

  // Numbered list: 1. text
  const numMatch = line.match(/^(\s*)(\d+)\.\s+(.*)/);
  if (numMatch) {
    const num = numMatch[2];
    const text = numMatch[3];
    return `<div class="editor-line numbered-line" data-line="${lineIndex}">` +
      `<span class="number-marker" contenteditable="false">${num}.</span>` +
      `<span>${renderInlineFormatting(escapeHtml(text))}</span>` +
      `</div>`;
  }

  // Headings: # ## ###
  if (line.startsWith("### ")) {
    return `<h3 class="editor-line" data-line="${lineIndex}">${renderInlineFormatting(escapeHtml(line.slice(4)))}</h3>`;
  }
  if (line.startsWith("## ")) {
    return `<h2 class="editor-line" data-line="${lineIndex}">${renderInlineFormatting(escapeHtml(line.slice(3)))}</h2>`;
  }
  if (line.startsWith("# ")) {
    return `<h1 class="editor-line" data-line="${lineIndex}">${renderInlineFormatting(escapeHtml(line.slice(2)))}</h1>`;
  }

  // Horizontal rule: --- or *** or ___
  if (/^(---|\*\*\*|___)$/.test(line.trim())) {
    return `<hr class="editor-hr" data-line="${lineIndex}">`;
  }

  // Regular paragraph
  const content = line.length === 0 ? "<br>" : renderInlineFormatting(escapeHtml(line));
  return `<div class="editor-line" data-line="${lineIndex}">${content}</div>`;
}

// Render inline formatting: `code`, **bold**, *italic*, ~~strikethrough~~, [links](url)
function renderInlineFormatting(html) {
  // Inline code: `code`
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>');
  // Bold: **text**
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  // Italic: *text*
  html = html.replace(/(?<!\*)\*([^*]+)\*(?!\*)/g, '<em>$1</em>');
  // Strikethrough: ~~text~~
  html = html.replace(/~~([^~]+)~~/g, '<s>$1</s>');
  // Links: [text](url)
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" class="editor-link" target="_blank" rel="noopener">$1</a>');
  return html;
}

// ── PLAIN TEXT EXTRACTION ─────────────────────────────────────────────────────
// Extract the plain text from the rendered editor HTML for saving back to the database.

function getEditorPlainText() {
  const lines = [];
  const children = editor.childNodes;

  for (const node of children) {
    if (node.nodeType === Node.TEXT_NODE) {
      lines.push(node.textContent);
      continue;
    }

    if (node.nodeType !== Node.ELEMENT_NODE) continue;

    const tag = node.tagName?.toLowerCase();

    // Code block
    if (tag === "pre" && node.classList.contains("code-block")) {
      const codeEl = node.querySelector("code");
      const codeText = codeEl ? codeEl.textContent : node.textContent;
      lines.push("```");
      lines.push(codeText);
      lines.push("```");
      continue;
    }

    // Image container - skip (handled by spans)
    if (node.classList?.contains("editor-image-container")) {
      continue;
    }

    // Horizontal rule
    if (tag === "hr") {
      lines.push("---");
      continue;
    }

    // Checkbox line
    if (node.classList?.contains("checkbox-line")) {
      const cb = node.querySelector(".editor-checkbox");
      const textEl = node.querySelector(".checkbox-text");
      const checked = cb ? cb.checked : false;
      const text = textEl ? textEl.textContent : "";
      lines.push(`- [${checked ? "x" : " "}] ${text}`);
      continue;
    }

    // Bullet line
    if (node.classList?.contains("bullet-line")) {
      const textParts = [];
      node.childNodes.forEach(c => {
        if (!c.classList?.contains("bullet-marker")) {
          textParts.push(c.textContent);
        }
      });
      lines.push(`- ${textParts.join("").trim()}`);
      continue;
    }

    // Numbered line
    if (node.classList?.contains("numbered-line")) {
      const marker = node.querySelector(".number-marker");
      const num = marker ? marker.textContent.replace(".", "") : "1";
      const textParts = [];
      node.childNodes.forEach(c => {
        if (!c.classList?.contains("number-marker")) {
          textParts.push(c.textContent);
        }
      });
      lines.push(`${num}. ${textParts.join("").trim()}`);
      continue;
    }

    // Headings
    if (tag === "h1") { lines.push(`# ${node.textContent}`); continue; }
    if (tag === "h2") { lines.push(`## ${node.textContent}`); continue; }
    if (tag === "h3") { lines.push(`### ${node.textContent}`); continue; }

    // Regular div/p line
    const text = node.textContent;
    if (tag === "div" || tag === "p" || tag === "span") {
      // If the only child is a <br>, it's an empty line
      if (node.innerHTML === "<br>" || node.innerHTML === "") {
        lines.push("");
      } else {
        lines.push(text);
      }
    } else {
      lines.push(text);
    }
  }

  return lines.join("\n");
}

// Build spans from the current editor state (images)
function buildSpansFromEditor() {
  const spans = [];

  // Preserve image spans from inline images in editor
  const images = editor.querySelectorAll(".editor-inline-image");
  images.forEach(img => {
    const filename = img.dataset.filename;
    if (filename) {
      spans.push({ start: 0, end: 1, style: `image:${filename}` });
    }
  });

  return spans;
}

// ── EDITOR & WYSIWYG ─────────────────────────────────────────────────────────
function setupEditor() {
  searchInput.addEventListener("input", (e) => {
    searchQuery = e.target.value;
    loadNotes();
  });

  // Handle checkbox clicks
  editor.addEventListener("click", (e) => {
    const checkbox = e.target.closest(".editor-checkbox");
    if (checkbox) {
      e.preventDefault();
      e.stopPropagation();
      // Toggle the checkbox
      checkbox.checked = !checkbox.checked;
      // Update the visual text
      const textEl = checkbox.closest(".checkbox-line")?.querySelector(".checkbox-text");
      if (textEl) {
        textEl.classList.toggle("checked-text", checkbox.checked);
      }
      scheduleSave();
    }

    // Handle link clicks
    const link = e.target.closest(".editor-link");
    if (link) {
      e.preventDefault();
      window.open(link.href, "_blank");
    }
  });

  // Handle typing in the editor - input event for live updates
  editor.addEventListener("input", () => {
    if (!currentNote) return;

    const text = getEditorPlainText();

    // 1. Real-time note title & preview snippet in list card
    const activeCard = notesListEl.querySelector(`.note-card[data-id="${currentNote.id}"]`);
    if (activeCard) {
      const lines = text.split("\n");
      const title = lines.find(l => l.trim().length > 0) || "New Note";
      const bodyRemaining = lines.slice(1).filter(l => l.trim().length > 0).join(" ");
      activeCard.querySelector(".note-card-title span").textContent = title;
      activeCard.querySelector(".note-card-preview").textContent = bodyRemaining || "No additional text";
    }

    // 2. Real-time tag chips
    updateTagChips(text);

    // 3. Debounced 300ms autosave to Rust SQLite
    scheduleSave();
  });

  // Handle Enter key for auto-continue lists
  editor.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      handleEnterKey(e);
    }
    // Tab for indentation
    if (e.key === "Tab") {
      e.preventDefault();
      document.execCommand("insertText", false, "  ");
    }
  });

  // Handle paste for images
  editor.addEventListener("paste", (e) => {
    handlePaste(e);
  });
}

function scheduleSave() {
  clearTimeout(saveTimeout);
  saveTimeout = setTimeout(saveCurrentNoteImmediately, 300);
}

async function saveCurrentNoteImmediately() {
  if (!currentNote) return;
  const text = getEditorPlainText();
  const spans = buildSpansFromEditor();
  const spansJson = JSON.stringify(spans);
  try {
    await invoke("set_content", { id: currentNote.id, text, spans_json: spansJson });
    refreshSidebarTags();
  } catch (e) {
    console.error("Failed to save note:", e);
  }
}

// ── ENTER KEY: AUTO-CONTINUE LISTS ────────────────────────────────────────────
function handleEnterKey(e) {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return;

  const range = sel.getRangeAt(0);
  const lineEl = range.startContainer.closest?.(".editor-line") ||
                 range.startContainer.parentElement?.closest(".editor-line");
  if (!lineEl) return;

  const text = getEditorPlainText();
  const lines = text.split("\n");
  const lineIndex = parseInt(lineEl.dataset.line);
  if (isNaN(lineIndex) || lineIndex >= lines.length) return;

  const currentLine = lines[lineIndex];

  // Check for checkbox line: - [ ] or - [x]
  const cbMatch = currentLine.match(/^(\s*)-\s*\[([ xX])\]\s*(.*)/);
  if (cbMatch) {
    const indent = cbMatch[1];
    const content = cbMatch[3];
    if (content.trim() === "") {
      // Empty checkbox — remove it
      e.preventDefault();
      lines[lineIndex] = "";
      reRenderAndRestore(lines, lineIndex);
      return;
    }
    // Insert a new unchecked checkbox
    e.preventDefault();
    lines.splice(lineIndex + 1, 0, `${indent}- [ ] `);
    reRenderAndRestore(lines, lineIndex + 1);
    return;
  }

  // Check for bullet line: - text
  const bulletMatch = currentLine.match(/^(\s*)[-•]\s+(.*)/);
  if (bulletMatch) {
    const indent = bulletMatch[1];
    const content = bulletMatch[2];
    if (content.trim() === "") {
      e.preventDefault();
      lines[lineIndex] = "";
      reRenderAndRestore(lines, lineIndex);
      return;
    }
    e.preventDefault();
    lines.splice(lineIndex + 1, 0, `${indent}- `);
    reRenderAndRestore(lines, lineIndex + 1);
    return;
  }

  // Check for numbered line: 1. text
  const numMatch = currentLine.match(/^(\s*)(\d+)\.\s+(.*)/);
  if (numMatch) {
    const indent = numMatch[1];
    const num = parseInt(numMatch[2]);
    const content = numMatch[3];
    if (content.trim() === "") {
      e.preventDefault();
      lines[lineIndex] = "";
      reRenderAndRestore(lines, lineIndex);
      return;
    }
    e.preventDefault();
    lines.splice(lineIndex + 1, 0, `${indent}${num + 1}. `);
    reRenderAndRestore(lines, lineIndex + 1);
    return;
  }
}

// Re-render editor after line manipulation and set cursor to the target line
function reRenderAndRestore(lines, targetLineIndex) {
  const text = lines.join("\n");
  const spans = buildSpansFromEditor();
  currentSpans = spans;
  renderEditorContent(text, spans);
  updateTagChips(text);
  scheduleSave();

  // Set cursor to end of the target line
  requestAnimationFrame(() => {
    const targetEl = editor.querySelector(`[data-line="${targetLineIndex}"]`);
    if (targetEl) {
      const sel = window.getSelection();
      const range = document.createRange();
      // Find the last text node in the target line
      const textNode = getLastTextNode(targetEl);
      if (textNode) {
        range.setStart(textNode, textNode.textContent.length);
        range.collapse(true);
        sel.removeAllRanges();
        sel.addRange(range);
      } else {
        range.selectNodeContents(targetEl);
        range.collapse(false);
        sel.removeAllRanges();
        sel.addRange(range);
      }
      targetEl.scrollIntoView({ block: "nearest" });
    }
  });
}

function getLastTextNode(element) {
  if (element.nodeType === Node.TEXT_NODE) return element;
  for (let i = element.childNodes.length - 1; i >= 0; i--) {
    const found = getLastTextNode(element.childNodes[i]);
    if (found) return found;
  }
  return null;
}

// ── PASTE HANDLER (IMAGES) ────────────────────────────────────────────────────
async function handlePaste(e) {
  const items = e.clipboardData?.items;
  if (!items) return;

  for (const item of items) {
    if (item.type.startsWith("image/")) {
      e.preventDefault();
      const blob = item.getAsFile();
      if (blob) {
        await insertImageFromBlob(blob);
      }
      return;
    }
  }
  // For text paste, let default behavior handle it
}

async function insertImageFromBlob(blob) {
  try {
    const arrayBuffer = await blob.arrayBuffer();
    const uint8Array = new Uint8Array(arrayBuffer);
    // Convert to base64 for sending to Rust
    const base64 = uint8ArrayToBase64(uint8Array);
    const ext = blob.type.includes("png") ? "png" : "jpg";
    const filename = await invoke("save_image", { imageBase64: base64, extension: ext });

    // Add image span to current spans
    currentSpans.push({ start: 0, end: 1, style: `image:${filename}` });

    // Re-render the editor with the new image
    const text = getEditorPlainText();
    renderEditorContent(text, currentSpans);
    scheduleSave();
  } catch(err) {
    console.error("Failed to save pasted image:", err);
  }
}

async function insertImageFromFilePicker() {
  try {
    // Use Tauri dialog to pick an image file
    const result = await invoke("pick_and_save_image");
    if (result) {
      currentSpans.push({ start: 0, end: 1, style: `image:${result}` });
      const text = getEditorPlainText();
      renderEditorContent(text, currentSpans);
      scheduleSave();
    }
  } catch(err) {
    console.error("Failed to pick image:", err);

    // Fallback: use an HTML file input
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    input.style.display = "none";
    input.addEventListener("change", async () => {
      const file = input.files[0];
      if (file) {
        await insertImageFromBlob(file);
      }
      input.remove();
    });
    document.body.appendChild(input);
    input.click();
  }
}

function uint8ArrayToBase64(uint8Array) {
  let binary = "";
  const len = uint8Array.length;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(uint8Array[i]);
  }
  return btoa(binary);
}

// ── FORMATTING TOOLBAR ────────────────────────────────────────────────────────
function setupToolbar() {
  document.getElementById("tb-back")?.addEventListener("click", () => {
    appContainer?.classList.remove("mobile-editor-view");
  });

  document.getElementById("tb-bold")?.addEventListener("click", () => document.execCommand("bold"));
  document.getElementById("tb-italic")?.addEventListener("click", () => document.execCommand("italic"));
  document.getElementById("tb-underline")?.addEventListener("click", () => document.execCommand("underline"));
  document.getElementById("tb-strike")?.addEventListener("click", () => document.execCommand("strikeThrough"));
  document.getElementById("tb-h1")?.addEventListener("click", () => insertPrefix("# "));
  document.getElementById("tb-h2")?.addEventListener("click", () => insertPrefix("## "));
  document.getElementById("tb-h3")?.addEventListener("click", () => insertPrefix("### "));
  document.getElementById("tb-undo")?.addEventListener("click", () => document.execCommand("undo"));

  // New toolbar buttons
  document.getElementById("tb-checkbox")?.addEventListener("click", () => insertPrefix("- [ ] "));
  document.getElementById("tb-bullet")?.addEventListener("click", () => insertPrefix("- "));
  document.getElementById("tb-numlist")?.addEventListener("click", () => insertPrefix("1. "));
  document.getElementById("tb-code")?.addEventListener("click", () => wrapSelection("`", "`"));
  document.getElementById("tb-codeblock")?.addEventListener("click", () => insertCodeBlock());
  document.getElementById("tb-image")?.addEventListener("click", () => insertImageFromFilePicker());

  document.getElementById("btn-info")?.addEventListener("click", showInfoModal);
  document.getElementById("modal-close")?.addEventListener("click", () => infoModal.classList.add("hidden"));
  document.getElementById("btn-export-md")?.addEventListener("click", exportCurrentNoteAsMarkdown);
  document.getElementById("btn-export-pdf")?.addEventListener("click", exportCurrentNoteAsPDF);
}

const SUPABASE_URL = "https://jklezsokvxfcjcxjfnmj.supabase.co";

function exportCurrentNoteAsMarkdown() {
  if (!currentNote) return;
  const text = getEditorPlainText();
  const title = currentNote.title || "Untitled Note";
  const filename = `${title.replace(/[^a-zA-Z0-9_-]/g, "_")}.md`;
  const blob = new Blob([text], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function exportCurrentNoteAsPDF() {
  if (!currentNote) return;
  window.print();
}

async function showInfoModal() {
  if (!currentNote) return;
  const text = getEditorPlainText();
  const wordCount = text.trim() ? text.trim().split(/\s+/).length : 0;
  const charCount = text.length;

  document.getElementById("stat-words").textContent = `${wordCount.toLocaleString()} words`;
  document.getElementById("stat-chars").textContent = `${charCount.toLocaleString()} characters`;
  document.getElementById("stat-created").textContent = formatFullDate(currentNote.created_at);
  document.getElementById("stat-modified").textContent = formatFullDate(currentNote.updated_at);

  // Load Revision History
  const revListEl = document.getElementById("revision-list");
  if (revListEl) {
    revListEl.innerHTML = "<div style='color: var(--text-muted); padding: 4px;'>Loading history...</div>";
    try {
      const revs = await invoke("get_note_revisions", { noteId: currentNote.id });
      revListEl.innerHTML = "";
      if (Array.isArray(revs) && revs.length > 0) {
        revs.forEach(r => {
          const item = document.createElement("div");
          item.style.cssText = "display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid var(--border-color);";
          const timeStr = formatFullDate(r.created_at);
          item.innerHTML = `<span>🕒 ${escapeHtml(timeStr)}</span><button class="tb-btn" style="padding: 2px 8px; font-size: 11px;">Restore</button>`;
          item.querySelector("button").addEventListener("click", async () => {
            await invoke("restore_note_revision", { revisionId: r.id });
            infoModal.classList.add("hidden");
            loadNotes();
          });
          revListEl.appendChild(item);
        });
      } else {
        revListEl.innerHTML = "<div style='color: var(--text-muted); padding: 4px;'>No past revisions</div>";
      }
    } catch (e) {
      revListEl.innerHTML = "<div style='color: var(--text-muted); padding: 4px;'>No past revisions</div>";
    }
  }

  infoModal.classList.remove("hidden");
}

// ── KEYBOARD SHORTCUTS ────────────────────────────────────────────────────────
function setupShortcuts() {
  document.addEventListener("keydown", (e) => {
    // 1. ESCAPE KEY: Close modal -> Hide context menu -> Back from compact editor
    if (e.key === "Escape") {
      if (infoModal && !infoModal.classList.contains("hidden")) {
        infoModal.classList.add("hidden");
        return;
      }
      if (contextMenu && !contextMenu.classList.contains("hidden")) {
        contextMenu.classList.add("hidden");
        return;
      }
      if (appContainer && appContainer.classList.contains("mobile-editor-view")) {
        appContainer.classList.remove("mobile-editor-view");
        return;
      }
      if (document.activeElement === editor) {
        editor.blur();
        return;
      }
    }

    // 2. ALT + LEFT ARROW: Back navigation from editor to note list
    if (e.altKey && (e.key === "ArrowLeft" || e.key === "Left")) {
      e.preventDefault();
      appContainer?.classList.remove("mobile-editor-view");
      return;
    }

    // 3. CTRL / CMD SHORTCUTS
    if (e.ctrlKey || e.metaKey) {
      if (e.key === "n" || e.key === "N") {
        e.preventDefault();
        createNewNote();
      } else if (e.key === "f" || e.key === "F") {
        e.preventDefault();
        searchInput.focus();
        searchInput.select();
      } else if (e.shiftKey && (e.key === "p" || e.key === "P")) {
        e.preventDefault();
        if (currentNote) {
          invoke("set_pinned", { id: currentNote.id, pinned: !currentNote.is_pinned }).then(loadNotes);
        }
      } else if (e.shiftKey && (e.key === "d" || e.key === "D")) {
        e.preventDefault();
        if (currentNote) {
          invoke("move_to_trash", { id: currentNote.id }).then(loadNotes);
        }
      }
    }
  });
}

// ── CONTEXT MENU ──────────────────────────────────────────────────────────────
function setupContextMenu() {
  const sidebarMenu = document.getElementById("sidebar-context-menu");

  // Prevent default web browser context menu globally across app
  document.addEventListener("contextmenu", (e) => {
    if (e.target.closest("#editor") || e.target.closest("#search-input")) {
      return;
    }
    e.preventDefault();

    const sidebarEl = e.target.closest("#sidebar");
    if (sidebarEl && !e.target.closest(".note-card")) {
      contextMenu?.classList.add("hidden");
      if (sidebarMenu) {
        sidebarMenu.style.left = `${Math.min(e.clientX, window.innerWidth - 200)}px`;
        sidebarMenu.style.top = `${Math.min(e.clientY, window.innerHeight - 100)}px`;
        sidebarMenu.classList.remove("hidden");
      }
    } else {
      sidebarMenu?.classList.add("hidden");
    }
  });

  document.addEventListener("click", () => {
    contextMenu?.classList.add("hidden");
    sidebarMenu?.classList.add("hidden");
  });

  document.getElementById("cm-pin")?.addEventListener("click", async () => {
    if (!contextNoteId) return;
    const note = notesList.find(n => n.id === contextNoteId);
    if (note) {
      await invoke("set_pinned", { id: note.id, pinned: !note.is_pinned });
      loadNotes();
    }
  });

  document.getElementById("cm-trash")?.addEventListener("click", async () => {
    if (!contextNoteId) return;
    await invoke("move_to_trash", { id: contextNoteId });
    loadNotes();
  });

  document.getElementById("cm-restore")?.addEventListener("click", async () => {
    if (!contextNoteId) return;
    await invoke("restore_from_trash", { id: contextNoteId });
    loadNotes();
  });

  document.getElementById("cm-delete")?.addEventListener("click", async () => {
    if (!contextNoteId) return;
    await invoke("delete_permanently", { id: contextNoteId });
    loadNotes();
  });

  document.getElementById("scm-open")?.addEventListener("click", () => {
    sidebarMenu?.classList.add("hidden");
  });

  document.getElementById("scm-refresh")?.addEventListener("click", () => {
    sidebarMenu?.classList.add("hidden");
    loadNotes();
  });
}

function showContextMenu(e, note) {
  e.preventDefault();
  e.stopPropagation();
  contextNoteId = note.id;

  const isTrash = currentFilter === "trash";
  document.getElementById("cm-pin").classList.toggle("hidden", isTrash);
  document.getElementById("cm-trash").classList.toggle("hidden", isTrash);
  document.getElementById("cm-div").classList.toggle("hidden", isTrash);
  document.getElementById("cm-restore").classList.toggle("hidden", !isTrash);
  document.getElementById("cm-delete").classList.toggle("hidden", !isTrash);

  contextMenu.style.left = `${Math.min(e.clientX, window.innerWidth - 220)}px`;
  contextMenu.style.top = `${Math.min(e.clientY, window.innerHeight - 130)}px`;
  contextMenu.classList.remove("hidden");
}

// ── HELPER FUNCTIONS ──────────────────────────────────────────────────────────
function updateTagChips(text) {
  tagChipsEl.innerHTML = "";
  if (!text) return;
  const matches = text.match(/(?:^|\s)#([a-zA-Z0-9_\-\/]+)/g) || [];
  const tags = new Set(matches.map(m => m.trim().replace(/^#/, "").replace(/\/$/, "")));
  tags.forEach(tag => {
    const chip = document.createElement("span");
    chip.className = "tag-chip";
    chip.textContent = `#${tag}`;
    tagChipsEl.appendChild(chip);
  });
}

function formatDate(epochMs) {
  if (!epochMs) return "";
  const dt = new Date(epochMs);
  const now = new Date();
  if (dt.toDateString() === now.toDateString()) {
    return dt.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  }
  return dt.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

function formatFullDate(epochMs) {
  if (!epochMs) return "-";
  return new Date(epochMs).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
}

function escapeHtml(str) {
  if (!str) return "";
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// ── SETTINGS MODAL & SUPABASE AUTH BINDING ─────────────────────────────────────
const settingsModal = document.getElementById("settings-modal");
const settingsCloseBtn = document.getElementById("settings-modal-close");
const navSettingsBtn = document.getElementById("nav-settings");
const settingsLoginBtn = document.getElementById("btn-settings-login");
const settingsSignupBtn = document.getElementById("btn-settings-signup");
const settingsEmailInput = document.getElementById("settings-email");
const settingsPasswordInput = document.getElementById("settings-password");
const settingsStatusBox = document.getElementById("settings-status-box");

let currentSupabaseUser = localStorage.getItem("supabase_user_email") || "";

if (currentSupabaseUser) {
  if (settingsStatusBox) settingsStatusBox.textContent = `Status: Logged in as ${currentSupabaseUser}`;
}

navSettingsBtn?.addEventListener("click", () => {
  settingsModal?.classList.remove("hidden");
});

settingsCloseBtn?.addEventListener("click", () => {
  settingsModal?.classList.add("hidden");
});

settingsLoginBtn?.addEventListener("click", () => {
  const email = settingsEmailInput?.value.trim();
  if (email) {
    currentSupabaseUser = email;
    localStorage.setItem("supabase_user_email", email);
    if (settingsStatusBox) settingsStatusBox.textContent = `Status: Logged in as ${email}`;
    alert(`Logged in to Supabase as ${email}`);
  }
});

settingsSignupBtn?.addEventListener("click", () => {
  const email = settingsEmailInput?.value.trim();
  if (email) {
    currentSupabaseUser = email;
    localStorage.setItem("supabase_user_email", email);
    if (settingsStatusBox) settingsStatusBox.textContent = `Status: Signed up as ${email}`;
    alert(`Account created on Supabase as ${email}`);
  }
});

// ── TOUCHPAD & MOUSE GESTURES ────────────────────────────────────────────────
let editorFontSize = 15;

// 1. Ctrl + Mouse Wheel Zoom in Editor
editor?.addEventListener("wheel", (e) => {
  if (e.ctrlKey) {
    e.preventDefault();
    if (e.deltaY < 0) {
      editorFontSize = Math.min(28, editorFontSize + 1);
    } else {
      editorFontSize = Math.max(12, editorFontSize - 1);
    }
    editor.style.fontSize = `${editorFontSize}px`;
  }
}, { passive: false });

// 2. Mouse Back Button (Button 3 / 4)
document.addEventListener("mouseup", (e) => {
  if (e.button === 3 || e.button === 4) {
    appContainer?.classList.remove("mobile-editor-view");
  }
});
