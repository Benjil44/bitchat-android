# BitChat Android - VS Code Developer Guide

## 🎯 Quick Start

You now have the Android project open in VS Code! Here's everything you need to know.

---

## 📁 Project Structure Overview

```
bitchat-android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/bitchat/android/
│   │   │   │   ├── ui/                    # 🎨 UI Layer (Compose)
│   │   │   │   │   ├── ChatViewModel.kt   # Main view model
│   │   │   │   │   ├── ChatState.kt       # App state
│   │   │   │   │   ├── PrivateChatManager.kt  # 💬 Private messaging
│   │   │   │   │   ├── MessageManager.kt  # Message handling
│   │   │   │   │   └── DataManager.kt     # Settings/persistence
│   │   │   │   │
│   │   │   │   ├── model/                 # 📦 Data Models
│   │   │   │   │   ├── BitchatMessage.kt  # Message model
│   │   │   │   │   └── DeliveryStatus.kt  # Status tracking
│   │   │   │   │
│   │   │   │   ├── services/              # ⚙️ Background Services
│   │   │   │   │   ├── BluetoothMeshService.kt  # BLE mesh networking
│   │   │   │   │   └── MessageRouter.kt   # Message routing logic
│   │   │   │   │
│   │   │   │   ├── encryption/            # 🔐 Security
│   │   │   │   │   ├── NoiseEncryptionService.kt
│   │   │   │   │   └── NoiseSessionManager.kt
│   │   │   │   │
│   │   │   │   ├── nostr/                 # 🌐 Nostr Integration
│   │   │   │   │   ├── NostrTransport.kt
│   │   │   │   │   ├── NostrDirectMessageHandler.kt
│   │   │   │   │   └── NostrRelayManager.kt
│   │   │   │   │
│   │   │   │   └── data/                  # 💾 Database (NEW!)
│   │   │   │       ├── MessageDatabase.kt
│   │   │   │       ├── MessagePersistenceService.kt
│   │   │   │       ├── dao/
│   │   │   │       │   └── PrivateChatDao.kt
│   │   │   │       └── entity/
│   │   │   │           └── MessageEntity.kt
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   └── test/                          # 🧪 Unit Tests
│   │       └── java/com/bitchat/android/ui/
│   │           └── PrivateChatManagerTest.kt
│   │
│   └── build.gradle.kts                   # App dependencies
│
├── gradle/
│   └── libs.versions.toml                 # Dependency versions
│
└── build.gradle.kts                       # Root build config
```

---

## 🔍 Key Files You Just Modified

### ✅ Recent Changes (Today)

1. **PrivateChatManager.kt** (`app/src/main/java/com/bitchat/android/ui/`)
   - ✨ Added `consolidateMessages()` function
   - ✨ Added `sanitizeChat()` function
   - 📝 Lines 427-520: New consolidation logic

2. **PrivateChatManagerTest.kt** (`app/src/test/java/com/bitchat/android/ui/`)
   - ✨ NEW FILE - 15 comprehensive unit tests
   - 🧪 Tests consolidation and sanitization

3. **MessageEntity.kt** (`app/src/main/java/com/bitchat/android/data/entity/`)
   - ✨ NEW FILE - Room database entity
   - 💾 Stores messages persistently

4. **PrivateChatDao.kt** (`app/src/main/java/com/bitchat/android/data/dao/`)
   - ✨ NEW FILE - Database queries
   - 🔍 15+ methods including search

5. **MessageDatabase.kt** (`app/src/main/java/com/bitchat/android/data/`)
   - ✨ NEW FILE - Room database
   - 💾 Singleton pattern

6. **MessagePersistenceService.kt** (`app/src/main/java/com/bitchat/android/data/`)
   - ✨ NEW FILE - Persistence logic
   - ⚙️ Message cap, retention, search

7. **DataManager.kt** (`app/src/main/java/com/bitchat/android/ui/`)
   - ✨ Updated with persistence settings
   - 📝 Lines 251-285: New settings methods

---

## 🚀 VS Code Tips for This Project

### Navigation Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+P` | Quick file search (try typing "PrivateChat") |
| `Ctrl+Shift+F` | Search across entire project |
| `Ctrl+T` | Search for symbols (classes, functions) |
| `F12` | Go to definition |
| `Shift+F12` | Find all references |
| `Ctrl+Click` | Jump to definition |
| `Alt+←` / `Alt+→` | Navigate back/forward |

### Useful Commands

Press `Ctrl+Shift+P` to open command palette, then:
- Type "Gradle" to run Gradle tasks
- Type "Kotlin" to access Kotlin-specific commands
- Type "Format" to format current file

### File Search Examples

Press `Ctrl+P` and try:
- Type `PrivateChatManager` - Jump to private chat manager
- Type `MessageEntity` - Open database entity
- Type `ChatViewModel` - Main view model
- Type `BitchatMessage` - Message model

### Code Search Examples

Press `Ctrl+Shift+F` and try:
- Search `consolidateMessages` - Find where it's called
- Search `Room` - Find all Room database usage
- Search `TODO` - Find todos/comments
- Search `Log.d` - Find debug logs

---

## 🏗️ Building the Project

### Option 1: Using VS Code Terminal

Open terminal in VS Code (`Ctrl+` ` or View → Terminal):

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Run specific test
./gradlew test --tests PrivateChatManagerTest

# Clean build
./gradlew clean build
```

### Option 2: Using Gradle Extension

1. Open Gradle sidebar (click Gradle icon in Activity Bar)
2. Expand `bitchat-android → app → Tasks`
3. Choose task:
   - `build/assembleDebug` - Build debug APK
   - `verification/test` - Run all tests
   - `other/clean` - Clean build

---

## 🧪 Running Tests

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
./gradlew test --tests PrivateChatManagerTest
```

### Run Single Test Method
```bash
./gradlew test --tests PrivateChatManagerTest.testConsolidateMessages_mergeTwoConversations
```

### View Test Results
After running tests, open:
```
app/build/reports/tests/testDebugUnitTest/index.html
```

---

## 📝 Editing Tips

### Kotlin-Specific Features

1. **Auto-import**: When you type a class name, VS Code will suggest importing it
2. **Code completion**: `Ctrl+Space` for suggestions
3. **Parameter hints**: `Ctrl+Shift+Space` while inside function parentheses
4. **Rename symbol**: `F2` on any variable/function/class
5. **Format document**: `Shift+Alt+F`

### Useful Code Snippets

Type these and press Tab:
- `fun` → Create function
- `class` → Create class
- `if` → If statement
- `for` → For loop
- `try` → Try-catch block

---

## 🔍 Exploring Recent Changes

### 1. Message Consolidation Feature

**File**: `app/src/main/java/com/bitchat/android/ui/PrivateChatManager.kt`

Press `Ctrl+G` and go to line **427** to see the new `consolidateMessages()` function.

**What it does**: Merges messages from multiple peer IDs with same nickname into one conversation.

**Test it**: Open `PrivateChatManagerTest.kt` and look at line **55** for test cases.

### 2. Persistent Storage Feature

**File**: `app/src/main/java/com/bitchat/android/data/MessagePersistenceService.kt`

This is brand new! Press `Ctrl+P`, type `MessagePersistence`, and open it.

**What it does**: Optionally saves messages to SQLite database using Room.

**Key methods** (press `Ctrl+F` to find):
- `saveMessage()` - Line ~52
- `loadMessages()` - Line ~92
- `searchMessages()` - Line ~256

### 3. Database Schema

**File**: `app/src/main/java/com/bitchat/android/data/entity/MessageEntity.kt`

Press `Ctrl+P`, type `MessageEntity`, and open.

**What it is**: Room database entity that represents a message in SQLite.

**Fields to notice**:
- `id` - Unique message ID
- `peerID` - Who the message is with
- `timestamp` - When it was sent
- `deliveryStatus` - Sending/Sent/Delivered/Read

---

## 🎨 Jetpack Compose UI Files

If you want to see the UI code:

### Main Chat Screen
**File**: `app/src/main/java/com/bitchat/android/ChatScreen.kt`

Press `Ctrl+P`, type `ChatScreen`

This is the main UI for the app (written in Jetpack Compose).

### Message Components
**File**: `app/src/main/java/com/bitchat/android/MessageComponents.kt`

Individual message bubbles and message list rendering.

---

## 🐛 Debugging

### View Logs
In terminal:
```bash
# View all logs
adb logcat

# Filter by tag
adb logcat PrivateChatManager:D *:S

# View only errors
adb logcat *:E
```

### Common Log Tags in This Project
- `PrivateChatManager` - Private messaging logs
- `MessageRouter` - Routing decisions
- `NostrDirectMessageHandler` - Nostr DM logs
- `MessagePersistence` - Database operations
- `BluetoothMeshService` - BLE mesh logs

---

## 📚 Understanding the Architecture

### Message Flow (Sending)

```
User types message in ChatScreen.kt
         ↓
ChatViewModel.sendPrivateMessage()
         ↓
PrivateChatManager.sendPrivateMessage()
         ↓
MessageRouter.sendPrivate()
         ↓
    ┌──────────┴──────────┐
    ↓                      ↓
BluetoothMeshService   NostrTransport
(if peer connected)    (if offline)
    ↓                      ↓
  [Encrypted with Noise Protocol]
         ↓
MessagePersistenceService.saveMessage()
(if persistence enabled)
```

### Message Flow (Receiving)

```
BLE/Nostr receives encrypted message
         ↓
NoiseEncryptionService.decrypt()
         ↓
MessageHandler.handlePrivateMessage()
         ↓
PrivateChatManager.handleIncomingPrivateMessage()
         ↓
PrivateChatManager.sanitizeChat()
(removes duplicates)
         ↓
ChatState updated (via MessageManager)
         ↓
UI automatically recomposes (Jetpack Compose)
         ↓
MessagePersistenceService.saveMessage()
(if persistence enabled)
```

---

## 🔧 Gradle Tasks Reference

### Build Tasks
- `assembleDebug` - Build debug APK
- `assembleRelease` - Build release APK (minified)
- `clean` - Delete build outputs
- `build` - Build everything

### Test Tasks
- `test` - Run unit tests
- `connectedAndroidTest` - Run instrumented tests (requires device/emulator)
- `testDebugUnitTest` - Run debug unit tests
- `testReleaseUnitTest` - Run release unit tests

### Other Useful Tasks
- `dependencies` - Show dependency tree
- `tasks` - List all available tasks
- `lintDebug` - Run Android Lint

---

## 📱 Running on Device/Emulator

### Prerequisites
1. Install Android Studio (for SDK and emulator)
2. Enable USB debugging on your Android device, OR
3. Set up Android emulator

### Run App
```bash
# Install debug APK
./gradlew installDebug

# Or use ADB directly
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Launch App
```bash
adb shell am start -n com.bitchat.droid/.MainActivity
```

---

## 🎯 Quick Reference: Recent Implementations

### 1. Message Consolidation
**Purpose**: Merge conversations when peer reconnects with new ID
**File**: `PrivateChatManager.kt:427`
**Test**: `PrivateChatManagerTest.kt:55`
**Usage**: Called automatically in `startPrivateChat()`

### 2. Chat Sanitization
**Purpose**: Remove duplicate messages by ID
**File**: `PrivateChatManager.kt:497`
**Test**: `PrivateChatManagerTest.kt:145`
**Usage**: Called after receiving messages

### 3. Persistent Storage
**Purpose**: Save messages across app restarts (opt-in)
**Files**:
- Service: `MessagePersistenceService.kt`
- DAO: `PrivateChatDao.kt`
- Entity: `MessageEntity.kt`
- Database: `MessageDatabase.kt`
**Settings**: `DataManager.kt:257`
**Default**: Disabled (privacy-first)

---

## 🆘 Troubleshooting

### "Unresolved reference" errors
1. Open terminal: `Ctrl+` `
2. Run: `./gradlew build`
3. Wait for build to complete
4. Reload VS Code window: `Ctrl+Shift+P` → "Reload Window"

### Kotlin extension not working
1. `Ctrl+Shift+P`
2. Type "Kotlin Language Server: Restart"
3. Wait for indexing to complete (bottom right status)

### Gradle sync issues
1. Close VS Code
2. Delete `.gradle` folder in project root
3. Reopen VS Code
4. Run `./gradlew clean build`

---

## 📖 Additional Resources

### Documentation
- **Architecture**: See `PRIVATE_MESSAGING_CHECKLIST.md` in parent folder
- **Persistent Storage**: See `PERSISTENT_STORAGE_IMPLEMENTATION.md`
- **AI Guide**: See `agent.md` for detailed architecture
- **History**: See `claude.md` for implementation decisions

### Official Docs
- [Kotlin](https://kotlinlang.org/docs/home.html)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

---

## 🎉 You're All Set!

The project is now open in VS Code. Try these next:

1. **Explore the code**: `Ctrl+P` → type "PrivateChatManager"
2. **Search for a feature**: `Ctrl+Shift+F` → search "consolidate"
3. **Run tests**: Open terminal → `./gradlew test`
4. **View recent changes**: Check git log → `git log --oneline -10`

Happy coding! 🚀
