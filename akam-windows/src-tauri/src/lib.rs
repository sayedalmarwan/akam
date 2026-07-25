use akam_core::{AkamDb, Note, RichSpan, RichText, Tag};
use std::sync::Mutex;
use tauri::{State, Manager, Window};
use window_vibrancy::apply_mica;
use base64::Engine as _;
use uuid::Uuid;

struct AppState {
    db: Mutex<Option<AkamDb>>,
    images_dir: Mutex<std::path::PathBuf>,
}

// ── Window Control Commands (CSD) ──────────────────────────────────────────────

#[tauri::command]
fn minimize_window(window: Window) {
    let _ = window.minimize();
}

#[tauri::command]
fn maximize_window(window: Window) -> Result<bool, String> {
    if window.is_maximized().unwrap_or(false) {
        window.unmaximize().map_err(|e| e.to_string())?;
        Ok(false)
    } else {
        window.maximize().map_err(|e| e.to_string())?;
        Ok(true)
    }
}

#[tauri::command]
fn is_maximized(window: Window) -> bool {
    window.is_maximized().unwrap_or(false)
}

#[tauri::command]
fn close_window(window: Window) {
    let _ = window.close();
}

// ── Database IPC Commands ──────────────────────────────────────────────────────

#[tauri::command]
fn create_note(state: State<'_, AppState>) -> Result<Note, String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    let note = db.create_note().map_err(|e| e.to_string())?;
    let _ = db.set_content(note.id, RichText { text: String::new(), spans: Vec::new() });
    Ok(note)
}

#[tauri::command]
fn get_content(id: i64, state: State<'_, AppState>) -> Result<RichText, String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    match db.get_content(id) {
        Ok(Some(rt)) => Ok(rt),
        _ => Ok(RichText { text: String::new(), spans: Vec::new() })
    }
}

#[tauri::command]
fn set_content(id: i64, text: String, spans_json: String, state: State<'_, AppState>) -> Result<(), String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    let spans: Vec<RichSpan> = serde_json::from_str(&spans_json).unwrap_or_default();
    db.set_content(id, RichText { text, spans }).map_err(|e| e.to_string())
}

#[tauri::command]
fn list_notes(tag: Option<String>, state: State<'_, AppState>) -> Result<Vec<Note>, String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    db.list_notes(tag).map_err(|e| e.to_string())
}

#[tauri::command]
fn list_untagged(state: State<'_, AppState>) -> Result<Vec<Note>, String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    db.list_untagged().map_err(|e| e.to_string())
}

#[tauri::command]
fn list_trashed(state: State<'_, AppState>) -> Result<Vec<Note>, String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    db.get_trashed_notes().map_err(|e| e.to_string())
}

#[tauri::command]
fn search_notes(query: String, state: State<'_, AppState>) -> Result<Vec<Note>, String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    db.search_notes(query).map_err(|e| e.to_string())
}

#[tauri::command]
fn set_pinned(id: i64, pinned: bool, state: State<'_, AppState>) -> Result<(), String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    db.set_pinned(id, pinned).map_err(|e| e.to_string())
}

#[tauri::command]
fn move_to_trash(id: i64, state: State<'_, AppState>) -> Result<(), String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    db.move_to_trash(id).map_err(|e| e.to_string())
}

#[tauri::command]
fn restore_from_trash(id: i64, state: State<'_, AppState>) -> Result<(), String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    db.restore_from_trash(id).map_err(|e| e.to_string())
}

#[tauri::command]
fn delete_permanently(id: i64, state: State<'_, AppState>) -> Result<(), String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    db.delete_permanently(id).map_err(|e| e.to_string())
}

#[tauri::command]
fn list_tags(state: State<'_, AppState>) -> Result<Vec<Tag>, String> {
    let guard = state.db.lock().unwrap();
    let db = guard.as_ref().ok_or("Database not initialized")?;
    db.list_tags().map_err(|e| e.to_string())
}

// ── Image Storage IPC Commands ────────────────────────────────────────────────

/// Save a base64-encoded image to the images directory. Returns the filename.
#[tauri::command]
fn save_image(image_base64: String, extension: String, state: State<'_, AppState>) -> Result<String, String> {
    let images_dir = state.images_dir.lock().unwrap();
    let filename = format!("{}.{}", Uuid::new_v4(), extension);
    let filepath = images_dir.join(&filename);

    let bytes = base64::engine::general_purpose::STANDARD
        .decode(&image_base64)
        .map_err(|e| format!("Invalid base64: {}", e))?;

    std::fs::write(&filepath, &bytes)
        .map_err(|e| format!("Failed to write image: {}", e))?;

    Ok(filename)
}

/// Get the absolute file system path for an image filename.
#[tauri::command]
fn get_image_path(filename: String, state: State<'_, AppState>) -> Result<String, String> {
    let images_dir = state.images_dir.lock().unwrap();
    let filepath = images_dir.join(&filename);
    if filepath.exists() {
        Ok(filepath.to_string_lossy().to_string())
    } else {
        Err(format!("Image not found: {}", filename))
    }
}

/// Open a native file dialog to pick an image, copy it to images dir, return filename.
#[tauri::command]
fn pick_and_save_image(state: State<'_, AppState>) -> Result<Option<String>, String> {
    let images_dir = state.images_dir.lock().unwrap();

    // Use rfd (rusty file dialogs) for a native Windows file picker
    let dialog = rfd::FileDialog::new()
        .add_filter("Images", &["png", "jpg", "jpeg", "webp", "gif", "bmp"])
        .set_title("Insert Image");

    if let Some(path) = dialog.pick_file() {
        let ext = path.extension()
            .and_then(|e| e.to_str())
            .unwrap_or("jpg")
            .to_lowercase();
        let filename = format!("{}.{}", Uuid::new_v4(), ext);
        let dest = images_dir.join(&filename);

        std::fs::copy(&path, &dest)
            .map_err(|e| format!("Failed to copy image: {}", e))?;

        Ok(Some(filename))
    } else {
        Ok(None) // User cancelled
    }
}

// ── Application Entry Point ───────────────────────────────────────────────────

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(AppState {
            db: Mutex::new(None),
            images_dir: Mutex::new(std::path::PathBuf::new()),
        })
        .setup(|app| {
            let window = app.get_webview_window("main").unwrap();

            // Apply Windows 11 Dynamic System Mica
            #[cfg(target_os = "windows")]
            let _ = apply_mica(&window, None);

            let app_data_dir = app.path().app_local_data_dir().expect("Failed to get local data dir");
            std::fs::create_dir_all(&app_data_dir).expect("Failed to create app data dir");

            // Create images directory
            let images_dir = app_data_dir.join("images");
            std::fs::create_dir_all(&images_dir).expect("Failed to create images dir");

            let db_path = app_data_dir.join("akam.db").to_string_lossy().to_string();

            let db = AkamDb::new(db_path).expect("Failed to initialize AkamDb");
            let state: State<AppState> = app.state();
            *state.db.lock().unwrap() = Some(db);
            *state.images_dir.lock().unwrap() = images_dir;

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            minimize_window,
            maximize_window,
            is_maximized,
            close_window,
            create_note,
            get_content,
            set_content,
            list_notes,
            list_untagged,
            list_trashed,
            search_notes,
            set_pinned,
            move_to_trash,
            restore_from_trash,
            delete_permanently,
            list_tags,
            save_image,
            get_image_path,
            pick_and_save_image,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
