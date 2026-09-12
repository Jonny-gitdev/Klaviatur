# Implementation Plan - Chord Sheet Feature

Add a comprehensive chord sheet feature with multiple sources (OLGA Archive, UG Import, Manual Entry) and WebView-based rendering using ChordSheetJS.

## User Review Required

> [!IMPORTANT]
> The implementation assumes `assets/js/chordsheetjs.min.js` will be provided by the user. I will create the necessary directory structure.

> [!NOTE]
> Navigation will be updated to include a new "Chord Sheets" tab in the bottom bar.

## Proposed Changes

### Data Layer

#### [NEW] [ChordModels.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/data/model/ChordModels.kt)
Define `OlgaSong` and `UserSong` entities.

#### [NEW] [ChordDaos.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/data/db/ChordDaos.kt)
Define `OlgaSongDao` and `UserSongDao`.

#### [NEW] [ChordDatabase.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/data/db/ChordDatabase.kt)
Define the new independent Room database.

#### [MODIFY] [DatabaseModule.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/di/DatabaseModule.kt)
Provide `ChordDatabase` and its DAOs via Hilt.

---

### Logic Layer

#### [NEW] [OlgaIndexer.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/data/repository/OlgaIndexer.kt)
Logic for traversing SAF folders, parsing filenames, and indexing songs into Room.

#### [NEW] [ChordRepository.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/data/repository/ChordRepository.kt)
Repository to handle both OLGA and User songs.

---

### UI Layer (Compose)

#### [NEW] [ChordSheetsScreen.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/ui/screens/chords/ChordSheetsScreen.kt)
The main list view with search and filters.

#### [NEW] [ChordSheetDetailScreen.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/ui/screens/chords/ChordSheetDetailScreen.kt)
WebView-based rendering screen.

#### [NEW] [UgImportScreen.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/ui/screens/chords/UgImportScreen.kt)
Screen for pasting and converting UG content.

#### [NEW] [ManualEntryScreen.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/ui/screens/chords/ManualEntryScreen.kt)
Simple editor for manual ChordPro entry.

#### [NEW] [ChordSheetWebView.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/ui/components/ChordSheetWebView.kt)
Reusable Composable for the WebView renderer.

---

### Assets

#### [NEW] [chordsheet_renderer.html](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/assets/chordsheet_renderer.html)
HTML template to load `ChordSheetJS` and render the song.

---

### Navigation & Integration

#### [MODIFY] [Screen.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/ui/Screen.kt)
Add new routes for chord features.

#### [MODIFY] [MainActivity.kt](file:///home/jonas/Downloads/KlaviatürApp/app/src/main/java/de/klaviatur/MainActivity.kt)
Update bottom navigation and NavHost.

## Verification Plan

### Automated Tests
- Unit tests for filename parsing logic in `OlgaIndexer`.
- Database integration tests for `ChordDatabase`.

### Manual Verification
1.  **Bottom Nav**: Verify "Chord Sheets" tab appears and navigates correctly.
2.  **OLGA Indexing**: Select a folder with `.pro` and `.crd` files. Verify they appear in the list with correct metadata.
3.  **UG Import**: Paste text from UG, verify conversion and saving.
4.  **Manual Entry**: Type ChordPro, verify saving.
5.  **Rendering**: Open different songs and verify they render correctly in the WebView.
6.  **Offline**: Verify WebView works with airplane mode (or `blockNetworkLoads = true` verified).
