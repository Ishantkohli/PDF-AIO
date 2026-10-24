# PDF AIO — In-App APK Auto-Update & Self-Cleaning System

A seamless, developer-friendly in-app update mechanism for **PDF AIO** that scans for new releases in the background, presents an update dialog with release notes, downloads the APK with live progress, launches the Android package installer, and automatically cleans up downloaded APK files once updated.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The user confirmed the following design and technical choices during Phase 1:
> - **Update Source**: **GitHub Releases repository** (`/releases/latest` REST endpoint parsing `tag_name`, release notes, and APK release asset).
> - **Update Detection**: Background check on app launch; if a newer version is detected, displays a modern update popup ("Download Now" vs. "Maybe Later") plus a manual "Check for Updates" option in Settings.
> - **Download UX**: An in-app progress card with real-time percentage (0–100%), downloaded MB / total MB count, and an immediate "Cancel" button.
> - **Installation & Auto-Cleanup**: Once downloaded, launches the Android system installer (`ACTION_VIEW` via `FileProvider`). Upon successful installation, a `MY_PACKAGE_REPLACED` broadcast receiver and startup routine immediately delete the downloaded APK to free device storage.

---

### 1. Overview & Core Concept

* **What It Does**: Keeps user devices automatically synchronized with the latest PDF AIO releases without requiring Google Play Store infrastructure.
* **Target Audience**: Users installing APKs directly (sideloaded, GitHub, or direct download) who want hassle-free one-tap updates.
* **Key Value**: Delivers silent background checking, transparent changelog previews, reliable downloads, and zero leftover storage waste by guaranteeing APK deletion after installation.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Silent Background Check Flow**:
   - On app launch, `UpdateManager` queries the GitHub Releases API asynchronously without freezing the UI.
   - If current version matches or exceeds remote version, the app continues silently without interruption.
2. **Update Dialog Flow**:
   - When a newer version is detected:
     - Displays a polished Acrobat Dark modal card titled **"New Version Available"**.
     - Shows current installed version vs. new version badge (e.g., `v1.0.0` → `v1.1.0`).
     - Renders the release notes / changelog with scrollable formatting.
     - Actions: **"Update Now"** (Crimson button) and **"Maybe Later"** (dismissible).
3. **In-App Download Flow**:
   - Tapping "Update Now" transforms the dialog or displays an elegant bottom floating card.
   - Shows live progress: progress bar, percentage indicator (`45%`), download metrics (`12.4 MB / 28.1 MB`), and a **Cancel** button.
   - If cancelled, deletes the partial download and closes the progress state.
4. **Installation & Prompt Flow**:
   - Once 100% downloaded, checks `canRequestPackageInstalls()`.
   - If permission needed (Android 8.0+ unknown sources), opens the exact system toggle page with guidance.
   - Immediately dispatches the system package installer intent using the secure `FileProvider` URI.
5. **Self-Cleaning / APK Removal Flow**:
   - When the user presses "Install" and the OS completes the update, Android broadcasts `android.intent.action.MY_PACKAGE_REPLACED`.
   - A registered `UpdateCleanReceiver` receives this signal and removes the cached APK file from internal storage.
   - A redundant check during `MainActivity` initialization ensures any lingering `.apk` files in the updates folder are permanently deleted.

#### Visual Theme & Styling Tokens
* **Aesthetic Direction**: Consistent with PDF AIO's executive *Acrobat Dark* styling.
* **Color Tokens**:
  - Modal Background: `#1A1A22` (Card surface) / `#262630` (Elevated)
  - Primary Accent: `#E11D48` (Acrobat Crimson)
  - New Version Pill: `#10B981` (Emerald Green)
  - Progress Track: `#2A2A38` / Progress Fill: `#E11D48`
  - Text Primary: `#F8FAFC` / Text Secondary: `#94A3B8`

---

### 3. Key Product Decisions & Trade-Offs

* **Update Provider: GitHub Releases API**:
  - *Chosen Approach*: Query GitHub's public REST endpoint (`https://api.github.com/repos/{owner}/{repo}/releases/latest`).
  - *Why*: Standard, free, requires zero backend servers or maintenance, and provides version tags, timestamps, markdown release notes, and direct APK download assets.
  - *Fallback / Configuration*: Allow entering a custom GitHub repo (or fallback default URL) in Settings so developers can test with their own repositories easily.
* **Installation Protocol**:
  - *Chosen Approach*: `FileProvider` content URI with `android.permission.REQUEST_INSTALL_PACKAGES` and `Intent.ACTION_VIEW` (`application/vnd.android.package-archive`).
  - *Why*: Fully standard across all modern Android versions (Android 8.0 through Android 15/16).
* **Storage Location & Self-Cleaning**:
  - *Chosen Approach*: Cache subdirectory `context.cacheDir.resolve("app_updates")`.
  - *Why*: Private to the application, granted through `FileProvider`, does not pollute user Downloads folder, and easily purged automatically by `UpdateCleanReceiver` and `MainActivity`.

---

### 4. Technical Architecture & Component Hierarchy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MainActivity (Startup Check)                    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      UpdateViewModel & UpdateManager                   │
│  - Checks GitHub Releases REST API                                     │
│  - Parses SemVer tag, release notes, and .apk asset URL                │
│  - Manages State: Idle -> UpdateAvailable -> Downloading -> Ready      │
└──────────────┬──────────────────────────────────────────┬──────────────┘
               │                                          │
               ▼                                          ▼
┌──────────────────────────────┐        ┌──────────────────────────────┐
│       UpdatePromptDialog     │        │     DownloadProgressCard     │
│  - Current vs New Version    │        │  - LinearProgressIndicator   │
│  - Release Notes Text        │        │  - Bytes read / Total bytes  │
│  - "Update Now" & "Later"    │        │  - "Cancel" Action           │
└──────────────┬───────────────┘        └──────────────┬───────────────┘
               │                                       │
               └───────────────────┬───────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     ApkInstaller & Clean Service                       │
│  1. Check / Request ACTION_MANAGE_UNKNOWN_APP_SOURCES if needed        │
│  2. Dispatch ACTION_VIEW Intent with FileProvider URI                  │
│  3. UpdateCleanReceiver listens for ACTION_MY_PACKAGE_REPLACED         │
│  4. Purges /cache/app_updates/*.apk on install or app restart          │
└────────────────────────────────────────────────────────────────────────┘
```

#### Core Components to Implement
1. **`UpdateDataModels.kt`**:
   - `UpdateInfo(versionName, versionCode, releaseNotes, downloadUrl, fileSize, publishedAt)`
   - `UpdateUiState(Idle, Checking, Available, Downloading, ReadyToInstall, Error)`
2. **`UpdateManager.kt`**:
   - Performs HTTP GET requests using OkHttp/HttpURLConnection with streaming buffers.
   - Calculates progress percentage and speed.
   - Cleans up stale APK files safely.
3. **`UpdatePromptDialog.kt` & `DownloadProgressCard.kt`**:
   - Compose components adhering to Material Design 3 and Acrobat Dark design tokens.
4. **`UpdateCleanReceiver.kt` & `AndroidManifest.xml`**:
   - Declare `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`.
   - Register receiver for `android.intent.action.MY_PACKAGE_REPLACED`.
5. **Settings Integration**:
   - Add a "Check for Updates" tile with last checked timestamp in the app settings/drawer.
