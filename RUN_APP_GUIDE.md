# How to Run BitChat Android App from VS Code

## 🎯 Quick Start

### **Option 1: Using Command Line (Recommended for VS Code)**

#### Step 1: Start Emulator
```bash
# In VS Code terminal (Ctrl + `)
"$ANDROID_HOME/emulator/emulator" -avd Medium_Phone_API_36.0 &

# Or on Windows Git Bash:
"D:\Program Files\Android sdk\Sdk\emulator\emulator" -avd Medium_Phone_API_36.0 &
```

#### Step 2: Wait for Emulator to Boot
```bash
# Check if device is ready
adb devices

# Should show something like:
# emulator-5554   device
```

#### Step 3: Build & Install App
```bash
# Build debug APK
./gradlew assembleDebug

# Install on emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or do both in one command:
./gradlew installDebug
```

#### Step 4: Launch App
```bash
# Launch the app
adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity

# Or click the app icon in the emulator
```

---

## 📱 **Option 2: Using Physical Device**

### Prerequisites
1. Enable **Developer Options** on your Android phone
2. Enable **USB Debugging**
3. Connect phone via USB

### Steps
```bash
# 1. Check device is connected
adb devices

# 2. Build and install
./gradlew installDebug

# 3. Launch app
adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity
```

---

## 🔄 **Quick Commands Reference**

### Build Commands
```bash
# Debug build (with debug info)
./gradlew assembleDebug

# Release build (optimized, minified)
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug
```

### Install Commands
```bash
# Install debug APK
./gradlew installDebug

# Install and run
./gradlew installDebug && adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity

# Uninstall
adb uninstall com.bitchat.droid
```

### Device Management
```bash
# List devices
adb devices

# List emulators
emulator -list-avds

# Start specific emulator
emulator -avd <emulator_name>

# Kill emulator
adb -e emu kill
```

### Debugging
```bash
# View logs (all)
adb logcat

# Filter by tag
adb logcat PrivateChatManager:D *:S

# Clear logs
adb logcat -c

# View only errors
adb logcat *:E
```

---

## 🎨 **Option 3: Viewing UI Without Running**

Since this is a Jetpack Compose app, you have limited options in VS Code:

### **A. View Compose Code**
Press `Ctrl+P` and type:
- `ChatScreen` - Main chat UI
- `MessageComponents` - Message bubbles
- `PrivateChatScreen` - Private chat UI
- `SidebarComponents` - Sidebar UI

### **B. Read UI Code with Structure**
```kotlin
// Example: ChatScreen.kt structure
@Composable
fun ChatScreen() {
    Scaffold {
        Column {
            ChatHeader()
            MessagesList()
            MessageInput()
        }
    }
}
```

### **C. Install Android Studio for Preview**
If you want visual Compose preview:
1. Install Android Studio
2. Open project in Android Studio
3. Click "Split" or "Design" view
4. See live preview of `@Preview` composables

**Note**: VS Code doesn't support Compose preview natively.

---

## ⚡ **Fast Development Workflow**

### **Initial Setup** (do once)
```bash
# 1. Start emulator
emulator -avd Medium_Phone_API_36.0 &

# 2. Wait for boot (check with 'adb devices')

# 3. Install app
./gradlew installDebug
```

### **During Development** (repeat)
```bash
# Make code changes in VS Code

# Reinstall app (keeps data)
./gradlew installDebug

# Relaunch app
adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity
```

### **Even Faster** (one command)
```bash
# Build, install, and launch in one line
./gradlew installDebug && adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity
```

---

## 🐛 **Troubleshooting**

### **Emulator won't start**
```bash
# Check if emulator exists
emulator -list-avds

# Start with verbose logging
emulator -avd Medium_Phone_API_36.0 -verbose

# Or use Android Studio's AVD Manager
```

### **App won't install**
```bash
# Check device connection
adb devices

# Uninstall old version
adb uninstall com.bitchat.droid

# Try installing again
./gradlew installDebug
```

### **Build fails**
```bash
# Clean and rebuild
./gradlew clean

# Build with stack trace
./gradlew assembleDebug --stacktrace

# Check for errors in output
```

### **App crashes on launch**
```bash
# View crash logs
adb logcat | grep -i "error\|exception\|crash"

# Or view all logs
adb logcat

# Clear logs and try again
adb logcat -c
adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity
adb logcat
```

---

## 📺 **Viewing Emulator Screen in VS Code**

### **Option A: Emulator Window**
- The emulator opens in its own window
- You can resize and position it next to VS Code

### **Option B: scrcpy (Screen Mirroring)**
Install scrcpy for better screen mirroring:

```bash
# Install scrcpy (Windows with Chocolatey)
choco install scrcpy

# Or download from: https://github.com/Genymobile/scrcpy

# Run scrcpy
scrcpy

# Lower quality for performance
scrcpy -m 1024
```

**Benefits of scrcpy:**
- Lower latency than emulator
- Better performance
- Works with physical devices too
- Can mirror to any screen position

---

## 🎯 **Complete Example Workflow**

### **First Time Setup**
```bash
# Open VS Code terminal
# Ctrl + ` (backtick)

# 1. Start emulator
"$ANDROID_HOME/emulator/emulator" -avd Medium_Phone_API_36.0 &

# 2. Wait ~30 seconds, then check
adb devices
# Should show: emulator-5554   device

# 3. Build and install
./gradlew installDebug

# 4. Launch app
adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity
```

### **After Making Code Changes**
```bash
# Quick reinstall and relaunch
./gradlew installDebug && adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity

# Or separate commands:
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity
```

---

## 🔍 **Viewing Specific UI Components**

Since you asked about UI preview, here's where to find each screen:

### **Main Screens** (`Ctrl+P` to open)
| File | UI Component |
|------|--------------|
| `ChatScreen.kt` | Main chat interface |
| `PrivateChatScreen.kt` | Private message view |
| `MessageComponents.kt` | Message bubbles |
| `ChatHeader.kt` | Top navigation bar |
| `SidebarComponents.kt` | Channels/DMs sidebar |
| `MessageInput.kt` | Text input field |
| `LocationChannelsSheet.kt` | Geohash location UI |

### **How to Understand UI from Code**
```kotlin
// Example: MessageComponents.kt
@Composable
fun MessageBubble(message: BitchatMessage) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (message.isPrivate)
                Color.Blue else Color.Gray
        )
    ) {
        Text(message.content)
        Text(message.sender)
    }
}
```

This creates a card (like a chat bubble) with:
- Blue background for private messages
- Gray background for public messages
- Shows message content and sender

---

## 💡 **Pro Tips**

### **1. Keep Emulator Running**
Don't close the emulator between builds - just reinstall:
```bash
./gradlew installDebug
```

### **2. Use Gradle Daemon**
Gradle daemon speeds up builds. It's enabled by default.

### **3. Incremental Builds**
Don't use `clean` unless necessary - incremental builds are faster:
```bash
# Faster (incremental)
./gradlew assembleDebug

# Slower (full rebuild)
./gradlew clean assembleDebug
```

### **4. View Logs in Real-Time**
Keep a terminal open with logcat:
```bash
adb logcat PrivateChatManager:D MessageRouter:D ChatViewModel:D *:S
```

### **5. Hot Reload (Doesn't Exist for Android)**
Unlike web development, Android doesn't have hot reload in VS Code.
You must rebuild and reinstall after each change.

---

## 🚀 **VS Code Extensions for Android**

### **Optional (but helpful)**
```bash
# In VS Code, press Ctrl+P and type:
> ext install vscjava.vscode-java-pack
> ext install mathiasfrohlich.kotlin
```

### **For Better Logcat Viewing**
- Extension: "Android Logcat Viewer"
- Or use terminal: `adb logcat`

---

## 📱 **Your Current Setup**

```
✅ Android SDK: D:\Program Files\Android sdk\Sdk
✅ ADB: Version 1.0.41
✅ Emulator: Medium_Phone_API_36.0
✅ VS Code: Open and ready
```

**You're all set to run the app!** 🎉

---

## 🎬 **Next Steps**

1. **Wait for emulator to boot** (check with `adb devices`)
2. **Build the app**: `./gradlew assembleDebug`
3. **Install**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. **Launch**: `adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity`
5. **See your UI!** 📱

---

**Pro Tip**: Bookmark this file for quick reference! 📌

**File Location**: `D:\bitchat\bitchat-android\RUN_APP_GUIDE.md`
