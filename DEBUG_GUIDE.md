# Debugging Settings & Question Counter Crashes

## Quick ADB Commands

### 1. **Capture Real-time Logcat (Most Important)**
```bash
# Clear existing logs and capture fresh ones
adb logcat -c

# Run your app and click the problematic button
# Then capture logs in real-time
adb logcat | grep -E "(FATAL|Exception|Error|Crash|QuizBottomBar|SettingsDialog)"

# Or with timestamps
adb logcat -v threadtime | grep -E "(FATAL|Exception|Error|Crash|QuizBottomBar|SettingsDialog)"
```

### 2. **Save Logs to File**
```bash
# Run this before clicking the button
adb logcat -c

# Click the button and let it crash

# Capture logs to file
adb logcat > crash_logs.txt

# Search for errors
grep -i "exception\|error\|fatal" crash_logs.txt
```

### 3. **Real-time Monitoring with Filtering**
```bash
# Monitor all logs with Java stack traces
adb logcat -v brief

# Monitor specific app process only
adb logcat --pid=$(adb shell pidof com.medicalquiz.app)

# Filter by log level (FATAL=F, ERROR=E, WARNING=W, DEBUG=D)
adb logcat *:E  # Only errors and above

# Combine: errors from your app
adb logcat com.medicalquiz.app:E *:S  # Show E/F from app, silence others
```

### 4. **Get Full Stack Traces**
```bash
# Verbose output with stack traces
adb logcat -v long

# Save and view full crash details
adb logcat > full_crash.log &
# Click button to crash
# Kill with: jobs and kill %1
# View with: less full_crash.log
```

---

## Potential Issues & How to Debug Them

### **Issue 1: Null Pointer Exception in Settings Dialog**
**Signs:** Crash when clicking settings (top bar or drawer)
**Possible causes:**
- `settingsRepository` not initialized
- `viewModel` is null
- Compose state not properly collected

**Debug steps:**
```bash
# Look for these in logcat:
adb logcat | grep -E "SettingsDialog|settingsRepository|NullPointerException"

# Check if repository was initialized:
adb logcat | grep "SettingsRepository"
```

### **Issue 2: ViewModelStore Cleared (Common Compose Issue)**
**Signs:** "Cannot access members of ViewModel after onCleared"
**Possible causes:**
- Activity recreating while dialog is open
- ViewModel scope issue with Compose

**Debug steps:**
```bash
# Watch for activity lifecycle
adb logcat | grep -E "onCreate|onDestroy|QuizActivity"

# Check if ViewModel is being cleared
adb logcat | grep "onCleared"
```

### **Issue 3: Dialog Composable Not Displaying Properly**
**Signs:** Click works but crash occurs when rendering
**Possible causes:**
- Missing theme context
- Composable recomposition issues
- State collection problems

**Debug steps:**
```bash
# Monitor Compose recomposition
adb logcat | grep -E "Recomposing|Skipping"

# Check theme initialization
adb logcat | grep "MedicalQuizTheme"
```

---

## Complete Debug Workflow

### **Step 1: Run App with Debug Logging**
```bash
# Open terminal 1
adb logcat -c
adb logcat -v threadtime | tee debug_output.txt

# This keeps logs running in background
```

### **Step 2: Reproduce the Crash**
```bash
# In your phone/emulator:
# 1. Open the app
# 2. Click the settings icon (gear in top bar or drawer)
# 3. OR click the question counter (e.g., "5 / 100")
# 4. Observe crash
```

### **Step 3: Analyze Logs**
```bash
# In terminal, look for:
# - Stack trace ending with the crash
# - Line mentioning QuizBottomBar or SettingsDialog
# - Exception type (NPE, ClassCastException, etc.)

# Key lines to search for:
grep "FATAL\|Exception in" debug_output.txt
grep "at com.medicalquiz" debug_output.txt
```

### **Step 4: Get Specific Error Context**
```bash
# Find the exact exception type
adb logcat | grep -i "exception" | head -5

# Get full stack trace (use -v long for multiline)
adb logcat -v long | grep -A 20 "Exception"
```

---

## Code Issues to Check (Based on Your Code)

### **In `QuizBottomBar.kt`:**
```kotlin
// Line: onJumpTo callback
// Issue: If onJumpTo is null or causes crash in parent
Row(
    modifier = Modifier
        .clickable(
            onClick = onJumpTo,  // ⚠️ Could crash here if null
            indication = ripple(),
            interactionSource = remember { MutableInteractionSource() }
        )
```

**Fix:** Add null safety check
```kotlin
Row(
    modifier = Modifier
        .clickable(
            onClick = { onJumpTo?.invoke() },  // Safe call
            indication = ripple(),
            interactionSource = remember { MutableInteractionSource() }
        )
```

### **In `QuizActivity.kt` - `showSettingsDialog()` method:**
```kotlin
// Line: settingsRepository might not be initialized
val loggingEnabled by settingsRepository.isLoggingEnabled.collectAsStateWithLifecycle()
// ⚠️ Check if settingsRepository is initialized
```

**Debug:**
```bash
# Check initialization order
adb logcat | grep "initializeComponents\|settingsRepository"
```

### **In `SettingsDialog.kt`:**
```kotlin
// The composable itself looks fine, but check if:
// - onLoggingChanged is called with null context
// - settingsRepository methods throw exceptions
```

---

## Advanced: Crash Symbolication

If you see obfuscated stack traces:

```bash
# Get the full stack trace
adb logcat > crash_with_stack.txt

# Use mapping file to deobfuscate (if proguard enabled)
# mapping.txt is in: app/build/outputs/mapping/release/mapping.txt

# Use retrace tool
./retrace.sh -verbose mapping.txt crash_with_stack.txt
```

---

## Recommended Quick Test

```bash
#!/bin/bash
# Save as test_crash.sh and run: bash test_crash.sh

adb logcat -c
echo "Waiting 2 seconds before starting logcat capture..."
sleep 2
adb logcat -v threadtime -d > crash_$(date +%s).log &
echo "Click settings or question counter now (30 second window)..."
sleep 30
echo "Crash logs saved to crash_*.log"
grep -i "exception\|error" crash_*.log | head -20
```

---

## Use This Command Immediately When Testing

```bash
adb shell am logcat -c && adb logcat *:E com.medicalquiz:D | grep -E "(Exception|Error|QuizBottomBar|SettingsDialog)"
```

This will:
1. Clear logcat
2. Show all errors and debug from your app
3. Filter for relevant lines
