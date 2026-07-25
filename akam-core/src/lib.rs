use rusqlite::Connection;
use serde::{Deserialize, Serialize};
use std::sync::Mutex;

uniffi::include_scaffolding!("akam");

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Note {
    pub id: i64,
    pub title: String,
    pub body: String, // plain text; styling lives in the spans column
    pub created_at: i64,
    pub updated_at: i64,
    pub is_deleted: bool,
    pub is_pinned: bool,
    pub trashed_at: Option<i64>,
    pub thumbnail: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Tag {
    pub id: i64,
    pub name: String,
    pub parent_id: Option<i64>,
    /// Live notes tagged with this tag or any descendant (Bear-style rollup).
    pub note_count: i64,
}

/// Character-range style. Offsets are UTF-16 code-unit indices as produced by
/// the Kotlin editor; the core stores them opaquely and never indexes with them.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct RichSpan {
    pub start: i32,
    pub end: i32,
    // opaque to the core: "bold" | "italic" | "underline" | "strike" | "code" |
    // "codeblock" | "heading"|"heading2"|"heading3" | "link:<url>". New styles need
    // no schema change — the column is untyped JSON.
    pub style: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RichText {
    pub text: String,
    pub spans: Vec<RichSpan>,
}

#[derive(Debug)]
pub enum AkamError {
    Database { msg: String },
}

impl std::fmt::Display for AkamError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            AkamError::Database { msg } => write!(f, "database error: {msg}"),
        }
    }
}

impl std::error::Error for AkamError {}

impl From<rusqlite::Error> for AkamError {
    fn from(e: rusqlite::Error) -> Self {
        AkamError::Database { msg: e.to_string() }
    }
}

impl From<serde_json::Error> for AkamError {
    fn from(e: serde_json::Error) -> Self {
        AkamError::Database { msg: e.to_string() }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NoteStats {
    pub word_count: i32,
    pub char_count: i32,
    pub line_count: i32,
    pub reading_time_minutes: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NoteRevision {
    pub id: i64,
    pub note_id: i64,
    pub title: String,
    pub body: String,
    pub created_at: i64,
}

const SCHEMA: &str = "
CREATE TABLE IF NOT EXISTS notes (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    spans TEXT NOT NULL DEFAULT '[]',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_pinned INTEGER NOT NULL DEFAULT 0,
    trashed_at INTEGER
);

CREATE TABLE IF NOT EXISTS tags (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    parent_id INTEGER REFERENCES tags(id)
);

CREATE TABLE IF NOT EXISTS note_tags (
    note_id INTEGER NOT NULL REFERENCES notes(id),
    tag_id INTEGER NOT NULL REFERENCES tags(id),
    PRIMARY KEY (note_id, tag_id)
);

CREATE TABLE IF NOT EXISTS note_links (
    source_id INTEGER NOT NULL REFERENCES notes(id),
    target_title TEXT NOT NULL,
    PRIMARY KEY (source_id, target_title)
);

CREATE TABLE IF NOT EXISTS attachments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    note_id INTEGER NOT NULL REFERENCES notes(id),
    filename TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS note_revisions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    note_id INTEGER NOT NULL REFERENCES notes(id),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(
    title, body, content='notes', content_rowid='id'
);

CREATE TRIGGER IF NOT EXISTS notes_ai AFTER INSERT ON notes BEGIN
    INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body);
END;

CREATE TRIGGER IF NOT EXISTS notes_ad AFTER DELETE ON notes BEGIN
    INSERT INTO notes_fts(notes_fts, rowid, title, body)
    VALUES ('delete', old.id, old.title, old.body);
END;

CREATE TRIGGER IF NOT EXISTS notes_au AFTER UPDATE OF title, body ON notes BEGIN
    INSERT INTO notes_fts(notes_fts, rowid, title, body)
    VALUES ('delete', old.id, old.title, old.body);
    INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body);
END;
";

const TRASH_RETENTION_MS: i64 = 30 * 24 * 60 * 60 * 1000;

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .expect("system clock before 1970")
        .as_millis() as i64
}

/// Columns added after the first release; ALTER TABLE has no IF NOT EXISTS,
/// so consult the live schema before adding each one.
fn migrate(conn: &Connection) -> rusqlite::Result<()> {
    let cols: Vec<String> = conn
        .prepare("PRAGMA table_info(notes)")?
        .query_map([], |r| r.get::<_, String>(1))?
        .collect::<Result<_, _>>()?;
    if !cols.iter().any(|c| c == "is_pinned") {
        conn.execute(
            "ALTER TABLE notes ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0",
            [],
        )?;
    }
    if !cols.iter().any(|c| c == "trashed_at") {
        conn.execute("ALTER TABLE notes ADD COLUMN trashed_at INTEGER", [])?;
    }
    Ok(())
}

/// Hard-delete notes that have sat in the trash past retention. The FTS
/// delete trigger cleans the index; note_tags rows went away at trash time.
fn purge_old_trash(conn: &Connection, now: i64) -> rusqlite::Result<()> {
    conn.execute(
        "DELETE FROM notes WHERE is_deleted = 1 AND trashed_at IS NOT NULL AND trashed_at < ?1",
        [now - TRASH_RETENTION_MS],
    )?;
    Ok(())
}

const NOTE_COLS: &str = "id, title, body, created_at, updated_at, is_deleted, is_pinned, trashed_at, spans";

fn row_to_note(row: &rusqlite::Row) -> rusqlite::Result<Note> {
    let spans_json: String = row.get(8)?;
    let spans: Vec<RichSpan> = serde_json::from_str(&spans_json).unwrap_or_default();
    let thumbnail = spans
        .iter()
        .find(|s| s.style.starts_with("image:"))
        .and_then(|s| s.style.strip_prefix("image:").map(|p| p.to_string()));
    Ok(Note {
        id: row.get(0)?,
        title: row.get(1)?,
        body: row.get(2)?,
        created_at: row.get(3)?,
        updated_at: row.get(4)?,
        is_deleted: row.get(5)?,
        is_pinned: row.get(6)?,
        trashed_at: row.get(7)?,
        thumbnail,
    })
}

/// Bear-style hashtags: `#word`, `#nested/tag`, `#multi word tag#`. A `#`
/// opens a tag only at the start of text or after whitespace, so `a#b` and
/// mid-word hashes don't match. A multi-word tag needs a closing `#` directly
/// after a word character; otherwise the tag ends at the first non-word char.
fn extract_tags(text: &str) -> Vec<String> {
    fn is_word(ch: char) -> bool {
        ch.is_alphanumeric() || ch == '_' || ch == '-' || ch == '/'
    }
    let mut tags = std::collections::BTreeSet::new();
    let mut prev: Option<char> = None;
    for (i, c) in text.char_indices() {
        if c == '#' && prev.is_none_or(|p| p.is_whitespace()) {
            let rest = &text[i + c.len_utf8()..];
            // look ahead for a closing `#`: only word chars and spaces may sit
            // between, and the closer must directly follow a word char
            let mut close: Option<usize> = None;
            for (j, ch) in rest.char_indices() {
                if ch == '#' {
                    if rest[..j].chars().last().is_some_and(is_word) {
                        close = Some(j);
                    }
                    break;
                }
                if !(is_word(ch) || ch == ' ') {
                    break;
                }
            }
            let tag = match close {
                Some(j) => rest[..j].trim(),
                None => {
                    let end = rest.find(|ch: char| !is_word(ch)).unwrap_or(rest.len());
                    &rest[..end]
                }
            }
            .trim_matches('/');
            if !tag.is_empty() && tag.starts_with(is_word) {
                tags.insert(tag.to_string());
            }
        }
        prev = Some(c);
    }
    tags.into_iter().collect()
}

/// Insert every path prefix of `path` ("a/b/c" -> a, a/b, a/b/c), return leaf id.
fn ensure_tag(conn: &Connection, path: &str) -> rusqlite::Result<i64> {
    let mut parent: Option<i64> = None;
    let mut acc = String::new();
    for seg in path.split('/').filter(|s| !s.is_empty()) {
        if !acc.is_empty() {
            acc.push('/');
        }
        acc.push_str(seg);
        conn.execute(
            "INSERT OR IGNORE INTO tags (name, parent_id) VALUES (?1, ?2)",
            (&acc, parent),
        )?;
        parent = Some(conn.query_row("SELECT id FROM tags WHERE name = ?1", [&acc], |r| r.get(0))?);
    }
    Ok(parent.expect("empty tag path"))
}

fn extract_wiki_links(text: &str) -> Vec<String> {
    let mut links = std::collections::BTreeSet::new();
    let mut rest = text;
    while let Some(start) = rest.find("[[") {
        let after_start = &rest[start + 2..];
        if let Some(end) = after_start.find("]]") {
            let target = &after_start[..end];
            let title = target.split('|').next().unwrap_or(target).trim();
            if !title.is_empty() {
                links.insert(title.to_string());
            }
            rest = &after_start[end + 2..];
        } else {
            break;
        }
    }
    links.into_iter().collect()
}

fn sync_note_links(conn: &Connection, note_id: i64, text: &str) -> rusqlite::Result<()> {
    conn.execute("DELETE FROM note_links WHERE source_id = ?1", [note_id])?;
    for target in extract_wiki_links(text) {
        conn.execute(
            "INSERT OR IGNORE INTO note_links (source_id, target_title) VALUES (?1, ?2)",
            (note_id, &target),
        )?;
    }
    Ok(())
}

fn sync_note_tags(conn: &Connection, note_id: i64, text: &str) -> rusqlite::Result<()> {
    conn.execute("DELETE FROM note_tags WHERE note_id = ?1", [note_id])?;
    for tag in extract_tags(text) {
        let tag_id = ensure_tag(conn, &tag)?;
        conn.execute(
            "INSERT OR IGNORE INTO note_tags (note_id, tag_id) VALUES (?1, ?2)",
            (note_id, tag_id),
        )?;
    }
    prune_orphan_tags(conn)
}

/// Tags exist only through notes (Bear semantics): drop tags with no notes and
/// no children; loop so emptied parents cascade.
fn prune_orphan_tags(conn: &Connection) -> rusqlite::Result<()> {
    loop {
        let n = conn.execute(
            "DELETE FROM tags WHERE id NOT IN (SELECT tag_id FROM note_tags)
             AND id NOT IN (SELECT parent_id FROM tags WHERE parent_id IS NOT NULL)",
            [],
        )?;
        if n == 0 {
            return Ok(());
        }
    }
}

// ponytail: single connection behind a mutex; connection pool if concurrency matters.
pub struct AkamDb {
    conn: Mutex<Connection>,
}

impl AkamDb {
    pub fn new(path: String) -> Result<Self, AkamError> {
        let conn = Connection::open(path)?;
        conn.execute_batch(SCHEMA)?;
        migrate(&conn)?;
        purge_old_trash(&conn, now_ms())?;
        Ok(Self {
            conn: Mutex::new(conn),
        })
    }

    pub fn create_note(&self) -> Result<Note, AkamError> {
        let conn = self.conn.lock().unwrap();
        let now = now_ms();
        conn.execute(
            "INSERT INTO notes (title, body, created_at, updated_at) VALUES ('', '', ?1, ?1)",
            [now],
        )?;
        Ok(Note {
            id: conn.last_insert_rowid(),
            title: String::new(),
            body: String::new(),
            created_at: now,
            updated_at: now,
            is_deleted: false,
            is_pinned: false,
            trashed_at: None,
            thumbnail: None,
        })
    }

    pub fn get_note(&self, id: i64) -> Result<Option<Note>, AkamError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt =
            conn.prepare(&format!("SELECT {NOTE_COLS} FROM notes WHERE id = ?1"))?;
        Ok(stmt.query_row([id], row_to_note).map(Some).or_else(|e| {
            if e == rusqlite::Error::QueryReturnedNoRows {
                Ok(None)
            } else {
                Err(e)
            }
        })?)
    }

    pub fn get_content(&self, id: i64) -> Result<Option<RichText>, AkamError> {
        let conn = self.conn.lock().unwrap();
        let row: Option<(String, String)> = conn
            .query_row("SELECT body, spans FROM notes WHERE id = ?1", [id], |r| {
                Ok((r.get(0)?, r.get(1)?))
            })
            .map(Some)
            .or_else(|e| {
                if e == rusqlite::Error::QueryReturnedNoRows {
                    Ok(None)
                } else {
                    Err(e)
                }
            })?;
        Ok(row.map(|(text, spans_json)| RichText {
            text,
            spans: serde_json::from_str(&spans_json).unwrap_or_default(),
        }))
    }

    /// Store the full editor state. The note's title is the first line of the
    /// text; tags are re-extracted from the text.
    pub fn set_content(&self, id: i64, content: RichText) -> Result<(), AkamError> {
        let title = content.text.lines().next().unwrap_or("").trim().to_string();
        let spans_json = serde_json::to_string(&content.spans)?;
        let mut conn = self.conn.lock().unwrap();

        // Check if revision snapshot should be recorded (if text is non-empty and last revision > 5 min old)
        if !content.text.trim().is_empty() {
            let last_rev_time: Option<i64> = conn
                .query_row(
                    "SELECT created_at FROM note_revisions WHERE note_id = ?1 ORDER BY created_at DESC LIMIT 1",
                    [id],
                    |r| r.get(0),
                )
                .ok();
            let now = now_ms();
            if last_rev_time.map_or(true, |t| now - t > 300_000) {
                let _ = conn.execute(
                    "INSERT INTO note_revisions (note_id, title, body, created_at) VALUES (?1, ?2, ?3, ?4)",
                    (id, &title, &content.text, now),
                );
            }
        }

        let tx = conn.transaction()?;
        tx.execute(
            "UPDATE notes SET title = ?1, body = ?2, spans = ?3, updated_at = ?4 WHERE id = ?5",
            (&title, &content.text, &spans_json, now_ms(), id),
        )?;
        sync_note_tags(&tx, id, &content.text)?;
        sync_note_links(&tx, id, &content.text)?;
        tx.commit()?;
        Ok(())
    }

    /// `tag` filters by tag name; a parent tag matches its children too
    /// (Bear behavior: #work shows notes tagged #work/projects).
    pub fn list_notes(&self, tag: Option<String>) -> Result<Vec<Note>, AkamError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(&format!(
            "SELECT {NOTE_COLS} FROM notes n
             WHERE is_deleted = 0 AND (?1 IS NULL OR EXISTS (
                 SELECT 1 FROM note_tags nt JOIN tags t ON t.id = nt.tag_id
                 WHERE nt.note_id = n.id AND (t.name = ?1 OR t.name LIKE ?1 || '/%')))
             ORDER BY updated_at DESC"
        ))?;
        let notes = stmt
            .query_map([tag], row_to_note)?
            .collect::<Result<_, _>>()?;
        Ok(notes)
    }

    /// Pinning doesn't touch updated_at, so it never reorders the list.
    pub fn set_pinned(&self, id: i64, pinned: bool) -> Result<(), AkamError> {
        let conn = self.conn.lock().unwrap();
        conn.execute("UPDATE notes SET is_pinned = ?1 WHERE id = ?2", (pinned, id))?;
        Ok(())
    }

    pub fn move_to_trash(&self, id: i64) -> Result<(), AkamError> {
        let mut conn = self.conn.lock().unwrap();
        let tx = conn.transaction()?;
        tx.execute(
            "UPDATE notes SET is_deleted = 1, trashed_at = ?1, updated_at = ?1 WHERE id = ?2",
            (now_ms(), id),
        )?;
        tx.execute("DELETE FROM note_tags WHERE note_id = ?1", [id])?;
        prune_orphan_tags(&tx)?;
        tx.commit()?;
        Ok(())
    }

    pub fn delete_permanently(&self, id: i64) -> Result<(), AkamError> {
        let mut conn = self.conn.lock().unwrap();
        let tx = conn.transaction()?;
        tx.execute("DELETE FROM note_tags WHERE note_id = ?1", [id])?;
        tx.execute("DELETE FROM note_links WHERE source_id = ?1", [id])?;
        tx.execute("DELETE FROM attachments WHERE note_id = ?1", [id])?;
        tx.execute("DELETE FROM note_revisions WHERE note_id = ?1", [id])?;
        tx.execute("DELETE FROM notes WHERE id = ?1", [id])?; // FTS trigger cleans the index
        prune_orphan_tags(&tx)?;
        tx.commit()?;
        Ok(())
    }

    /// Un-trash a note; its tags are re-linked from the stored text.
    pub fn restore_from_trash(&self, id: i64) -> Result<(), AkamError> {
        let mut conn = self.conn.lock().unwrap();
        let tx = conn.transaction()?;
        tx.execute(
            "UPDATE notes SET is_deleted = 0, trashed_at = NULL, updated_at = ?1 WHERE id = ?2",
            (now_ms(), id),
        )?;
        let text: Option<String> = tx
            .query_row("SELECT body FROM notes WHERE id = ?1", [id], |r| r.get(0))
            .map(Some)
            .or_else(|e| {
                if e == rusqlite::Error::QueryReturnedNoRows {
                    Ok(None)
                } else {
                    Err(e)
                }
            })?;
        if let Some(text) = text {
            sync_note_tags(&tx, id, &text)?;
        }
        tx.commit()?;
        Ok(())
    }

    pub fn list_untagged(&self) -> Result<Vec<Note>, AkamError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(&format!(
            "SELECT {NOTE_COLS} FROM notes
             WHERE is_deleted = 0 AND id NOT IN (SELECT note_id FROM note_tags)
             ORDER BY updated_at DESC"
        ))?;
        let notes = stmt.query_map([], row_to_note)?.collect::<Result<_, _>>()?;
        Ok(notes)
    }

    pub fn get_trashed_notes(&self) -> Result<Vec<Note>, AkamError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(&format!(
            "SELECT {NOTE_COLS} FROM notes
             WHERE is_deleted = 1 ORDER BY trashed_at DESC"
        ))?;
        let notes = stmt.query_map([], row_to_note)?.collect::<Result<_, _>>()?;
        Ok(notes)
    }

    /// Sanitized FTS5 search: every token is quoted (embedded quotes doubled)
    /// so user input can't inject MATCH syntax or crash on specials, and the
    /// last token gets a `*` prefix operator so short/partial queries match
    /// as-you-type ("q" finds "quantum").
    pub fn search_notes(&self, query: String) -> Result<Vec<Note>, AkamError> {
        let tokens: Vec<String> = query
            .split_whitespace()
            .map(|t| format!("\"{}\"", t.replace('"', "\"\"")))
            .collect();
        if tokens.is_empty() {
            return Ok(Vec::new());
        }
        let fts = tokens.join(" ") + "*";
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT n.id, n.title, n.body, n.created_at, n.updated_at, n.is_deleted,
                    n.is_pinned, n.trashed_at, n.spans
             FROM notes_fts f JOIN notes n ON n.id = f.rowid
             WHERE notes_fts MATCH ?1 AND n.is_deleted = 0 ORDER BY rank",
        )?;
        let notes = stmt
            .query_map([fts], row_to_note)?
            .collect::<Result<_, _>>()?;
        Ok(notes)
    }

    pub fn list_tags(&self) -> Result<Vec<Tag>, AkamError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT t.id, t.name, t.parent_id,
                (SELECT COUNT(DISTINCT nt.note_id)
                 FROM note_tags nt
                 JOIN tags c ON c.id = nt.tag_id
                 JOIN notes n ON n.id = nt.note_id AND n.is_deleted = 0
                 WHERE c.name = t.name OR c.name LIKE t.name || '/%')
             FROM tags t ORDER BY t.name",
        )?;
        let tags = stmt
            .query_map([], |row| {
                Ok(Tag {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    parent_id: row.get(2)?,
                    note_count: row.get(3)?,
                })
            })?
            .collect::<Result<_, _>>()?;
        Ok(tags)
    }

    /// Return notes that reference the given note via `[[Target Title]]` wiki-links.
    pub fn get_backlinks(&self, note_id: i64) -> Result<Vec<Note>, AkamError> {
        let conn = self.conn.lock().unwrap();
        let target_title: Option<String> = conn
            .query_row("SELECT title FROM notes WHERE id = ?1", [note_id], |r| r.get(0))
            .map(Some)
            .or_else(|e| {
                if e == rusqlite::Error::QueryReturnedNoRows {
                    Ok(None)
                } else {
                    Err(e)
                }
            })?;
        let title = match target_title {
            Some(t) if !t.trim().is_empty() => t,
            _ => return Ok(Vec::new()),
        };

        let mut stmt = conn.prepare(&format!(
            "SELECT {NOTE_COLS} FROM notes n
             JOIN note_links nl ON nl.source_id = n.id
             WHERE nl.target_title = ?1 AND n.is_deleted = 0
             ORDER BY n.updated_at DESC"
        ))?;
        let notes = stmt.query_map([title], row_to_note)?.collect::<Result<_, _>>()?;
        Ok(notes)
    }

    /// Return word count, character count, line count, and estimated reading time.
    pub fn get_note_stats(&self, note_id: i64) -> Result<Option<NoteStats>, AkamError> {
        let conn = self.conn.lock().unwrap();
        let text: Option<String> = conn
            .query_row("SELECT body FROM notes WHERE id = ?1", [note_id], |r| r.get(0))
            .map(Some)
            .or_else(|e| {
                if e == rusqlite::Error::QueryReturnedNoRows {
                    Ok(None)
                } else {
                    Err(e)
                }
            })?;
        let body = match text {
            Some(b) => b,
            None => return Ok(None),
        };

        let word_count = body.split_whitespace().count() as i32;
        let char_count = body.chars().count() as i32;
        let line_count = body.lines().count() as i32;
        let reading_time_minutes = (word_count as f32 / 200.0).ceil() as i32;

        Ok(Some(NoteStats {
            word_count,
            char_count,
            line_count,
            reading_time_minutes: if reading_time_minutes < 1 && word_count > 0 { 1 } else { reading_time_minutes },
        }))
    }

    /// Export note to formatted Markdown string.
    pub fn export_note_to_markdown(&self, note_id: i64) -> Result<Option<String>, AkamError> {
        let conn = self.conn.lock().unwrap();
        let row: Option<(String, String)> = conn
            .query_row("SELECT title, body FROM notes WHERE id = ?1", [note_id], |r| {
                Ok((r.get(0)?, r.get(1)?))
            })
            .map(Some)
            .or_else(|e| {
                if e == rusqlite::Error::QueryReturnedNoRows {
                    Ok(None)
                } else {
                    Err(e)
                }
            })?;
        Ok(row.map(|(title, body)| {
            if body.trim().starts_with(&format!("# {}", title.trim())) {
                body
            } else {
                format!("# {}\n\n{}", title.trim(), body)
            }
        }))
    }

    /// List revision history for a note, newest first.
    pub fn get_note_revisions(&self, note_id: i64) -> Result<Vec<NoteRevision>, AkamError> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, note_id, title, body, created_at
             FROM note_revisions WHERE note_id = ?1
             ORDER BY created_at DESC"
        )?;
        let revs = stmt
            .query_map([note_id], |row| {
                Ok(NoteRevision {
                    id: row.get(0)?,
                    note_id: row.get(1)?,
                    title: row.get(2)?,
                    body: row.get(3)?,
                    created_at: row.get(4)?,
                })
            })?
            .collect::<Result<_, _>>()?;
        Ok(revs)
    }

    /// Restore note content to a previous revision.
    pub fn restore_note_revision(&self, revision_id: i64) -> Result<(), AkamError> {
        let mut conn = self.conn.lock().unwrap();
        let rev: Option<(i64, String, String)> = conn
            .query_row(
                "SELECT note_id, title, body FROM note_revisions WHERE id = ?1",
                [revision_id],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
            )
            .map(Some)
            .or_else(|e| {
                if e == rusqlite::Error::QueryReturnedNoRows {
                    Ok(None)
                } else {
                    Err(e)
                }
            })?;

        if let Some((note_id, title, body)) = rev {
            let tx = conn.transaction()?;
            tx.execute(
                "UPDATE notes SET title = ?1, body = ?2, updated_at = ?3 WHERE id = ?4",
                (&title, &body, now_ms(), note_id),
            )?;
            sync_note_tags(&tx, note_id, &body)?;
            sync_note_links(&tx, note_id, &body)?;
            tx.commit()?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rich(text: &str, spans: Vec<RichSpan>) -> RichText {
        RichText {
            text: text.into(),
            spans,
        }
    }

    #[test]
    fn extract_tags_rules() {
        assert_eq!(
            extract_tags("Trip #travel/asia and #todo, not a#b or mid#word"),
            vec!["todo", "travel/asia"]
        );
        assert!(extract_tags("no tags here").is_empty());

        // position: start of line, mid-sentence, end of line / end of text
        assert_eq!(extract_tags("#start of line"), vec!["start"]);
        assert_eq!(extract_tags("line one\n#second line"), vec!["second"]);
        assert_eq!(extract_tags("ends with #end"), vec!["end"]);

        // punctuation adjacent: tag stops at punctuation; `(#x` has no
        // whitespace before # so it doesn't open
        assert_eq!(
            extract_tags("see #todo, #wip. (#nope) [#also-no]"),
            vec!["todo", "wip"]
        );

        // nested tags keep the full path; stray slashes are trimmed
        assert_eq!(
            extract_tags("#a/b/c and #trailing/ and #/leading"),
            vec!["a/b/c", "leading", "trailing"]
        );

        // multi-word with closing hash
        assert_eq!(extract_tags("note #multi word tag# end"), vec!["multi word tag"]);
        assert_eq!(extract_tags("#x# tiny"), vec!["x"]);
        // closing # must follow a word char: `#red and #blue` is two tags
        assert_eq!(extract_tags("#red and #blue colors"), vec!["blue", "red"]);
        // punctuation inside kills the multi-word form, falls back to one word
        assert_eq!(extract_tags("#not, a multi#"), vec!["not"]);
        // newline never joins a multi-word tag
        assert_eq!(extract_tags("#one\ntwo#"), vec!["one"]);

        // headings and empties are not tags
        assert!(extract_tags("## heading and # alone").is_empty());
    }

    #[test]
    fn rich_content_roundtrip_and_title() {
        let db = AkamDb::new(":memory:".into()).unwrap();
        let note = db.create_note().unwrap();
        assert_eq!(note.title, "");

        // styles are opaque strings: new kinds (code, heading3, packed link url)
        // round-trip with no schema change
        let spans = vec![
            RichSpan { start: 0, end: 4, style: "bold".into() },
            RichSpan { start: 5, end: 9, style: "heading3".into() },
            RichSpan { start: 10, end: 14, style: "link:https://example.com".into() },
        ];
        db.set_content(note.id, rich("Trip plan\nPack for #travel/asia", spans.clone()))
            .unwrap();

        let content = db.get_content(note.id).unwrap().unwrap();
        assert_eq!(content.text, "Trip plan\nPack for #travel/asia");
        assert_eq!(content.spans, spans);
        assert!(db.get_content(9999).unwrap().is_none());

        // title derived from first line, plain body searchable
        let n = db.get_note(note.id).unwrap().unwrap();
        assert_eq!(n.title, "Trip plan");
        assert_eq!(db.search_notes("pack".into()).unwrap().len(), 1);
    }

    #[test]
    fn tags_lifecycle() {
        let db = AkamDb::new(":memory:".into()).unwrap();
        let note = db.create_note().unwrap();
        db.set_content(note.id, rich("Trip\n#travel/asia #todo", vec![]))
            .unwrap();

        let tags = db.list_tags().unwrap();
        let names: Vec<_> = tags.iter().map(|t| t.name.as_str()).collect();
        assert_eq!(names, vec!["todo", "travel", "travel/asia"]);
        let travel = tags.iter().find(|t| t.name == "travel").unwrap();
        let asia = tags.iter().find(|t| t.name == "travel/asia").unwrap();
        assert_eq!(asia.parent_id, Some(travel.id));
        // parent rolls up child-tagged notes in its count
        assert_eq!(travel.note_count, 1);
        assert_eq!(asia.note_count, 1);

        // parent tag filter matches child-tagged notes
        assert_eq!(db.list_notes(Some("travel".into())).unwrap().len(), 1);
        assert!(db.list_notes(Some("nope".into())).unwrap().is_empty());

        // removing a tag from the text prunes it
        db.set_content(note.id, rich("Trip\n#travel/asia", vec![])).unwrap();
        let names: Vec<_> = db.list_tags().unwrap().iter().map(|t| t.name.clone()).collect();
        assert_eq!(names, vec!["travel", "travel/asia"]);

        // untagged
        let plain = db.create_note().unwrap();
        let untagged = db.list_untagged().unwrap();
        assert_eq!(untagged.len(), 1);
        assert_eq!(untagged[0].id, plain.id);

        // trash: excluded from queries, search, and tag counts
        db.move_to_trash(note.id).unwrap();
        assert!(db.list_tags().unwrap().is_empty());
        assert!(db.list_notes(None).unwrap().iter().all(|n| n.id != note.id));
        assert!(db.search_notes("trip".into()).unwrap().is_empty());
        let trash = db.get_trashed_notes().unwrap();
        assert_eq!(trash.len(), 1);
        assert!(trash[0].trashed_at.is_some());

        // restore re-links tags and clears trashed_at
        db.restore_from_trash(note.id).unwrap();
        assert!(db.get_trashed_notes().unwrap().is_empty());
        assert_eq!(db.list_tags().unwrap().len(), 2);
        assert!(db.get_note(note.id).unwrap().unwrap().trashed_at.is_none());
    }

    #[test]
    fn search_finds_only_matching_note() {
        let db = AkamDb::new(":memory:".into()).unwrap();
        for (a, b) in [
            ("Groceries", "milk and eggs"),
            ("Quantum notes", "entanglement and superposition"),
            ("Trip", "flights to Tokyo"),
        ] {
            let n = db.create_note().unwrap();
            db.set_content(n.id, rich(&format!("{a}\n{b}"), vec![])).unwrap();
        }
        // term unique to one note
        assert_eq!(db.search_notes("entanglement".into()).unwrap().len(), 1);
        // prefix / partial matches as-you-type
        assert_eq!(db.search_notes("quant".into()).unwrap().len(), 1);
        // tags (stored in body as #text) are searchable
        let tagged = db.create_note().unwrap();
        db.set_content(tagged.id, rich("Tagged\n#physics", vec![])).unwrap();
        assert_eq!(db.search_notes("physics".into()).unwrap().len(), 1);
        // special characters don't crash FTS
        assert!(db.search_notes("\"quote (paren".into()).unwrap().is_empty());
        assert!(db.search_notes("".into()).unwrap().is_empty());
    }

    #[test]
    fn pin_persists() {
        let db = AkamDb::new(":memory:".into()).unwrap();
        let note = db.create_note().unwrap();
        assert!(!note.is_pinned);
        db.set_pinned(note.id, true).unwrap();
        assert!(db.get_note(note.id).unwrap().unwrap().is_pinned);
        assert!(db.list_notes(None).unwrap()[0].is_pinned);
        db.set_pinned(note.id, false).unwrap();
        assert!(!db.get_note(note.id).unwrap().unwrap().is_pinned);
    }

    #[test]
    fn delete_permanently_removes_everything() {
        let db = AkamDb::new(":memory:".into()).unwrap();
        let note = db.create_note().unwrap();
        db.set_content(note.id, rich("Gone\n#tmp", vec![])).unwrap();
        db.move_to_trash(note.id).unwrap();
        db.delete_permanently(note.id).unwrap();
        assert!(db.get_note(note.id).unwrap().is_none());
        assert!(db.get_trashed_notes().unwrap().is_empty());
        assert!(db.search_notes("gone".into()).unwrap().is_empty());
        assert!(db.list_tags().unwrap().is_empty());
    }

    #[test]
    fn auto_purge_only_removes_old_trash() {
        let db = AkamDb::new(":memory:".into()).unwrap();
        let old = db.create_note().unwrap();
        let recent = db.create_note().unwrap();
        db.move_to_trash(old.id).unwrap();
        db.move_to_trash(recent.id).unwrap();
        {
            let conn = db.conn.lock().unwrap();
            // backdate one note past retention, leave the other a day old
            conn.execute(
                "UPDATE notes SET trashed_at = ?1 WHERE id = ?2",
                (now_ms() - TRASH_RETENTION_MS - 1, old.id),
            )
            .unwrap();
            conn.execute(
                "UPDATE notes SET trashed_at = ?1 WHERE id = ?2",
                (now_ms() - 24 * 60 * 60 * 1000, recent.id),
            )
            .unwrap();
            purge_old_trash(&conn, now_ms()).unwrap();
        }
        assert!(db.get_note(old.id).unwrap().is_none());
        let trash = db.get_trashed_notes().unwrap();
        assert_eq!(trash.len(), 1);
        assert_eq!(trash[0].id, recent.id);
    }
}
