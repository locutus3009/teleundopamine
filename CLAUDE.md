# Telegram No-Search - Anti-Dopamine Client

A modified version of the official Telegram Android client designed to reduce dopamine loop addiction by limiting discovery features and enabling focused communication.

## Project Overview

This is a fork of the official Telegram Android app with specific modifications to help users maintain healthy digital habits by:

1. **Blocking search in specific chats** - Prevent yourself from searching in distracting conversations
2. **Removing public channel discovery** - Hide unsubscribed public channels from all search results
3. **Filtering global search** - Blocked chats are excluded from all search contexts

**Current Base Version**: Telegram Android 12.0.1 (build 6166)
**Package Name**: `org.telegram.messenger.detox`
**Custom Features**: Chat blocklist, search filtering, channel discovery removal, auto-update disabled

## Key Modifications

### 1. Chat Blocklist System

**Configuration File**: `blocked_chats.txt` (in project root)

**Implementation**: `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`

- **Lines 101-149**: Added blocklist loading from assets and `isChatBlocked()` method
- Text file-based configuration (easy to edit, no Java knowledge required)
- Case-insensitive substring matching for chat names
- Works with user names, group titles, and channel names
- Loaded from assets at runtime

**Configuration File Locations**:
- **Edit**: `blocked_chats.txt` (project root - easy to find)
- **Deployed**: `TMessagesProj/src/main/assets/blocked_chats.txt` (bundled in APK)

**How to use**:
1. Edit `blocked_chats.txt` in the project root
2. Add one chat name per line
3. Lines starting with `#` are comments
4. Copy to assets before building:
   ```bash
   cp blocked_chats.txt TMessagesProj/src/main/assets/blocked_chats.txt
   ```
5. Rebuild the app

**Example** (`blocked_chats.txt`):
```
# My blocked chats
Distracting Friend
Time Waster Group
News Channel
```

### 2. Global Search Filtering

**File**: `TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java`

- **Lines 286-298**: Modified `filter()` method to exclude blocked chats from search results
- **Lines 659-676**: Added filtering for message search results to skip blocked chats
- **Line 36**: Added `BuildVars` import

Blocked chats will not appear in:
- Dialog/chat search
- User search
- Message search across all chats

### 3. In-Chat Search Blocking with User Feedback

**File**: `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`

- **Lines 34428-34437**: Modified `openSearchWithText()` to prevent search in blocked chats
- When you try to open search in a blocked chat, a notification popup appears
- Uses `BulletinFactory` to show: "Search is not available for this chat"
- Provides clear user feedback without being intrusive

### 4. Public Channel Discovery Removal

**Files Modified**:

#### `TMessagesProj/src/main/java/org/telegram/ui/Components/DialogsChannelsAdapter.java`
- **Lines 99-101**: Inverted logic in recommended channels to only show subscribed channels
- **Lines 119-133**: Modified search results to only show subscribed channels

#### `TMessagesProj/src/main/java/org/telegram/ui/Adapters/SearchAdapterHelper.java`
- **Lines 226-228**: Always filter out `ChatObject.isNotInChat()` channels from global search
- Removed the `allowGlobalResults` condition that previously allowed unsubscribed channels

**Result**: You can only discover channels you're already subscribed to. Public channel discovery via search is completely disabled.

### 5. Additional Features

#### Auto-Update Disabled
**File**: `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`
- `CHECK_UPDATES = false` - Prevents Telegram's built-in update mechanism from prompting updates
- Ensures you stay on your custom build without update notifications

#### Custom Edition Branding
**File**: `TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`
- Settings screen displays: "Nikolay Nerovny edition (detox)" below version info
- Helps distinguish custom build from official Telegram

#### Secure API Credentials
**Files**: `TMessagesProj/build.gradle`, `BuildVars.java`
- API credentials loaded from `local.properties` (gitignored)
- No hardcoded credentials in source code
- Easy to configure per developer without committing secrets

## Installation & Deployment

### Building the APK

1. **Set up API credentials** (one-time):
   ```bash
   cp local.properties.example local.properties
   # Edit local.properties and add your API credentials from https://my.telegram.org/apps
   ```

2. **Configure blocklist** (optional):
   ```bash
   cp blocked_chats.txt.example blocked_chats.txt
   # Edit blocked_chats.txt with chat names to block
   cp blocked_chats.txt TMessagesProj/src/main/assets/blocked_chats.txt
   ```

3. **Build** in Android Studio:
   - Open project
   - Select build variant: `afatRelease` for TMessagesProj_App
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Build time: ~30-40 minutes for release, ~5-10 minutes for debug

4. **Find APK**:
   ```
   TMessagesProj_App/build/outputs/apk/afat/release/app.apk
   ```

### Installing on Device

#### Via USB
```bash
adb install path/to/app.apk
```

#### Via Wireless ADB (Recommended)
```bash
# One-time setup on phone:
# Settings → Developer Options → Wireless Debugging → Enable

# On computer:
adb pair IP:PORT  # Use pairing code from phone
adb connect IP:PORT

# Verify connection
adb devices

# Install
adb install /path/to/app.apk

# Reinstall (update existing app)
adb install -r /path/to/app.apk
```

#### Manual Installation
1. Copy APK to phone
2. Open file manager and tap APK
3. Allow "Install from unknown sources" if prompted
4. Disable Google Play Protect if it blocks installation:
   - Open Play Store → Profile → Play Protect → Settings → Turn off

### First Run

1. Launch "Telegram (detox)" app
2. Enter phone number and verification code
3. Login should work with your configured API credentials
4. Verify custom features:
   - Settings → Check for "Nikolay Nerovny edition (detox)" at bottom
   - Try searching for a blocked chat (should not appear)
   - Try in-chat search on blocked chat (notification appears)

## Troubleshooting

### Build Issues

**NDK Not Installed**
```
Error: NDK is not installed
```
**Fix**: Tools → SDK Manager → SDK Tools → Check "NDK (Side by side)" → Install NDK 21.4.7075529

**NDK Corrupted**
```
Error: NDK did not have a source.properties file
```
**Fix**:
- SDK Manager → Uncheck NDK → Apply
- Check NDK again → Apply (reinstall)

**CMake Build Errors**
```
Error: ninja: fatal: chdir to CMakeFiles/CMakeTmp
```
**Fix**:
- Build → Clean Project
- File → Invalidate Caches → Invalidate and Restart

**Project in Safe Mode**
```
Error: Configuration files not loaded (untrusted project)
```
**Fix**: File → Settings → Trusted Projects → Add project path

**Google Services Package Mismatch** (Should not occur anymore)
```
Error: No matching client found for package name
```
**Fix**: Already disabled in build.gradle files

### Installation Issues

**Play Protect Blocks Install**
```
Google Play Protect prevented this install
```
**Fix**: Play Store → Profile → Play Protect → Settings → Turn off temporarily

**Installation Failed**
```
INSTALL_FAILED_UPDATE_INCOMPATIBLE
```
**Fix**: Uninstall existing Telegram (detox) first, then reinstall

### Runtime Issues

**Cannot Login / Phone Code Not Sent**
```
Phone number entered but no SMS code received
```
**Fix**:
1. Verify API credentials in `local.properties` are correct
2. Check credentials at https://my.telegram.org/apps
3. Rebuild app after updating credentials
4. Ensure internet connection is stable

**Blocklist Not Working**
```
Blocked chats still appear in search
```
**Fix**:
1. Verify `blocked_chats.txt` was copied to `TMessagesProj/src/main/assets/` before build
2. Check chat name spelling (case-insensitive substring match)
3. Rebuild app after modifying blocklist
4. Clear app data and re-login

**Search Notification Not Showing**
```
No popup when trying to search blocked chat
```
**Verify**:
- Chat name matches entry in blocklist
- You're trying in-chat search (search icon in chat toolbar)
- App was rebuilt after adding to blocklist

## Quick Reference

### Common Tasks

**Update Blocklist**
```bash
nano blocked_chats.txt  # Edit blocklist
cp blocked_chats.txt TMessagesProj/src/main/assets/blocked_chats.txt
# Rebuild in Android Studio
adb install -r TMessagesProj_App/build/outputs/apk/afat/release/app.apk
```

**Rebuild & Reinstall (Quick)**
```bash
# In Android Studio: Build → Build APK(s) (Ctrl+Shift+F9)
# Wait for build to complete
adb install -r TMessagesProj_App/build/outputs/apk/afat/release/app.apk
```

**Find All Custom Modifications**
```bash
grep -r "// CUSTOM:" TMessagesProj/src/ --include="*.java" -n
```

**Check Current Version**
```bash
grep APP_VERSION gradle.properties
# On phone: Settings → Scroll to bottom → See version and edition name
```

**Update from Upstream**
```bash
git fetch upstream --tags
git tag -l | grep -E '^release-[0-9]+\.[0-9]+\.0$' | tail -5
git merge release-X.Y.0  # Replace with desired version
# Resolve conflicts, test, rebuild
```

**Clean Build (When Things Break)**
```bash
./gradlew clean
# In Android Studio: Build → Clean Project → Rebuild Project
```

### Build Variants

- **afatDebug**: Fast build (~5-10 min), debuggable, package: `org.telegram.messenger.detox.beta`
- **afatRelease**: Optimized build (~30-40 min), production-ready, package: `org.telegram.messenger.detox`
- **afatStandalone**: Alternative release variant

### File Locations

- **Blocklist (edit)**: `blocked_chats.txt` (project root)
- **Blocklist (deployed)**: `TMessagesProj/src/main/assets/blocked_chats.txt`
- **API credentials**: `local.properties` (gitignored)
- **Release APK**: `TMessagesProj_App/build/outputs/apk/afat/release/app.apk`
- **Debug APK**: `TMessagesProj_App/build/outputs/apk/afat/debug/app.apk`
- **Package name config**: `gradle.properties` (APP_PACKAGE)
- **App name**: `TMessagesProj/src/main/res/values/strings.xml`

## Technical Details

### Architecture

The modifications work at multiple levels:

1. **Filter Layer** (`filter()` methods): Blocks items at the adapter level before they're displayed
2. **Search Prevention** (`openSearchWithText()`): Prevents search UI from opening in blocked chats
3. **Discovery Layer** (channel adapters): Filters out unsubscribed public channels at source

### Chat Identification

Chats are identified by name using:
- `UserObject.getUserName(user)` for private chats (users)
- `chat.title` for groups and channels
- `DialogObject` utilities for dialog type detection

### Blocklist Loading

- Blocklist is loaded from `assets/blocked_chats.txt` on first use
- Lazy loading via `loadBlockedChats()` method
- Cached in memory after first load
- File format: one chat name per line, `#` for comments

### Search Flow

```
App Start → (user searches) → BuildVars.isChatBlocked() → loadBlockedChats() → Read assets/blocked_chats.txt
                                           ↓
User Input → DialogsSearchAdapter → filter() → (check blocklist) → Display Results
                                  ↓
                          SearchAdapterHelper → globalSearch → (filter unsubscribed)
                                  ↓
                          ChatActivity → openSearchWithText() → (check blocklist) → Allow/Block
```

## Building the Project

### Prerequisites

1. **Android Studio** with NDK 21.4.7075529 installed
2. **Telegram API credentials** from https://my.telegram.org/apps

### Setup Steps

1. **Clone this repository**

2. **Configure API credentials** in `local.properties`:
   ```bash
   cp local.properties.example local.properties
   ```

   Edit `local.properties` and add your Telegram API credentials:
   ```properties
   TELEGRAM_APP_ID=YOUR_API_ID
   TELEGRAM_APP_HASH=YOUR_API_HASH
   ```

   Get these from https://my.telegram.org/apps (login → API development tools → Create application)

   **Note**: `local.properties` is gitignored and will never be committed to version control.

3. **Set up blocklist** (optional):
   ```bash
   cp blocked_chats.txt.example blocked_chats.txt
   # Edit blocked_chats.txt and add chat names to block
   cp blocked_chats.txt TMessagesProj/src/main/assets/blocked_chats.txt
   ```

4. **Open project in Android Studio**

5. **Build the APK**:
   - For debug: Build → Build APK(s) with `afatDebug` variant
   - For release: Build → Build APK(s) with `afatRelease` variant

**Note**: Google Services and Firebase are disabled for custom package names in this fork.

## Customization Guide

### Adding Blocked Chats

**First time setup:**
```bash
# Copy template files to create your personal blocklist
cp blocked_chats.txt.example blocked_chats.txt
cp TMessagesProj/src/main/assets/blocked_chats.txt.example TMessagesProj/src/main/assets/blocked_chats.txt
```

**To add/modify blocked chats:**

1. **Edit the blocklist file** in the project root:
   ```bash
   nano blocked_chats.txt  # or use any text editor
   ```

2. **Add chat names** (one per line):
   ```
   Distracting Friend
   Time Waster Group
   News Channel Name
   ```

3. **Copy to assets**:
   ```bash
   cp blocked_chats.txt TMessagesProj/src/main/assets/blocked_chats.txt
   ```

4. **Rebuild the app**

**Notes**:
- Matching is case-insensitive
- Substring matching (e.g., "John" matches "John Doe")
- Lines starting with `#` are comments
- Empty lines are ignored
- No runtime configuration - changes require rebuild (by design)
- Keep root and assets files in sync before building
- Personal `blocked_chats.txt` files are gitignored for privacy
- Use `blocked_chats.txt.example` as template

### Reverting Changes

All custom modifications are marked with `// CUSTOM:` comments. Search for this string to find all modifications:

```bash
grep -r "// CUSTOM:" TMessagesProj/src/
```

## Philosophy

This client is designed with these principles:

1. **Intentional Communication**: You should know who you want to talk to
2. **No Discovery**: Prevent algorithmic or search-based discovery of new content
3. **Source Code Configuration**: No fancy UI for restrictions - editing code creates friction (which is the point)
4. **Subtle Enforcement**: Silent blocking rather than error messages

## Privacy & Security

- No telemetry added
- No data sent to third parties beyond standard Telegram protocol
- All modifications are client-side only
- Uses official Telegram API with your own credentials

## Upstream & Fork Maintenance

### Repository Structure

- **Upstream Origin**: https://github.com/DrKLO/Telegram (Official Telegram Android)
- **Private Fork**: git@github.com:locutus3009/teleundopamine.git
- **Custom Branch**: `nerovny/detox`

### Maintaining Your Fork

To keep your fork updated with upstream Telegram releases without heavy effort:

#### Initial Setup

```bash
# Add upstream remote (one-time setup)
cd /hdd/locutus/dev/telegram-nosearch
git remote add upstream https://github.com/DrKLO/Telegram.git
git fetch upstream

# Verify remotes
git remote -v
# Should show:
# private   git@github.com:locutus3009/teleundopamine.git (your fork)
# upstream  https://github.com/DrKLO/Telegram.git (official)
```

#### Update Strategy: Track Stable Minor Versions

**Recommended approach**: Update only on **minor version increments** (e.g., 12.0.x → 12.1.0) to avoid frequent breaking changes.

**Check for new stable releases:**
```bash
# List upstream tags (stable releases)
git fetch upstream --tags
git tag -l | grep -E '^release-[0-9]+\.[0-9]+\.0$' | tail -10

# Current version in your fork
grep APP_VERSION gradle.properties
```

#### Updating to New Stable Release

**Option 1: Merge (Recommended for first-time updates)**

```bash
# From your custom branch
git checkout nerovny/detox
git fetch upstream --tags

# Merge a specific stable release (e.g., 12.1.0)
git merge release-12.1.0

# Resolve conflicts (see below)
# After resolving all conflicts:
git add .
git commit -m "Merge upstream release-12.1.0"
git push private nerovny/detox
```

**Option 2: Rebase (Clean history, more complex)**

```bash
# CAUTION: Rebase rewrites history
git checkout nerovny/detox
git fetch upstream --tags

# Rebase your custom commits onto new release
git rebase release-12.1.0

# Resolve conflicts for each commit
# After resolving:
git rebase --continue

# Force push (since history was rewritten)
git push private nerovny/detox --force-with-lease
```

#### Handling Conflicts

Your custom modifications will likely conflict with upstream changes. Common conflict areas:

1. **BuildVars.java** - API credentials, blocklist system
2. **DialogsSearchAdapter.java** - Search filtering
3. **ChatActivity.java** - In-chat search blocking
4. **SearchAdapterHelper.java** - Channel filtering
5. **build.gradle** - Google Services disabled

**Conflict resolution strategy:**

```bash
# During merge/rebase, check which files have conflicts
git status

# For each conflicting file, look for your custom modifications
# They are marked with "// CUSTOM:" comments
grep -n "// CUSTOM:" path/to/conflicting/file.java

# Edit the file and preserve your custom changes
# Keep markers: <<<<<<< HEAD, =======, >>>>>>>
# Remove one side or combine both intelligently

# After fixing a file:
git add path/to/file.java

# Continue merge/rebase
git merge --continue   # if merging
git rebase --continue  # if rebasing
```

**Quick conflict finder:**
```bash
# Find all your custom modifications
grep -r "// CUSTOM:" TMessagesProj/src/ --include="*.java" -n
```

#### Testing After Update

After merging/rebasing from upstream:

1. **Clean build**:
   ```bash
   ./gradlew clean
   ```

2. **Build debug APK first** (faster iteration):
   ```bash
   # In Android Studio: Build → Build APK(s) with afatDebug variant
   ```

3. **Test all custom features**:
   - [ ] Blocklist loading from assets
   - [ ] Global search filtering (blocked chats hidden)
   - [ ] In-chat search blocking with notification
   - [ ] Public channel discovery disabled
   - [ ] Custom edition name in Settings

4. **Build release APK** after confirming everything works

5. **Update CLAUDE.md** if upstream changes affect custom features

#### Recommended Update Schedule

- **Check for updates**: Monthly
- **Apply updates**: Only on **minor version releases** (x.Y.0)
- **Skip**: Patch releases (x.y.Z) unless critical security fixes
- **Avoid**: Updating during major versions (X.0.0) without extensive testing

#### Tracking Changes

Keep a log of your merge commits to track which upstream versions you've integrated:

```bash
# View your merge history
git log --oneline --merges --graph

# View what changed in an upstream release
git log release-12.0.0..release-12.1.0 --oneline
```

#### Emergency: Abort Merge/Rebase

If conflicts become too complex:

```bash
# Abort merge
git merge --abort

# Abort rebase
git rebase --abort

# You'll return to the state before the merge/rebase started
```

### Custom Modifications Reference

All custom code is marked with `// CUSTOM:` comments for easy identification during conflict resolution:

```bash
# List all custom modifications with context
grep -r "// CUSTOM:" TMessagesProj/src/ -A 5 -B 1 --include="*.java"

# Count custom modifications
grep -r "// CUSTOM:" TMessagesProj/src/ --include="*.java" | wc -l
```

**Files with custom modifications:**
- `BuildVars.java` - Blocklist system, API credentials, auto-update disable
- `DialogsSearchAdapter.java` - Global search filtering
- `ChatActivity.java` - In-chat search blocking with notification
- `DialogsChannelsAdapter.java` - Channel discovery filtering
- `SearchAdapterHelper.java` - Global search channel filtering
- `ProfileActivity.java` - Custom edition branding
- `build.gradle` files - Google Services disabled, API credentials from local.properties
- `settings.gradle` - Huawei and HockeyApp modules disabled

## License

This project maintains the same GPL v2+ license as the official Telegram Android client.

## Contributing

This is a personal fork focused on specific anti-dopamine features. Feel free to fork and customize for your own needs.

## Disclaimer

This is an unofficial modification of Telegram. Use at your own risk. Not affiliated with or endorsed by Telegram.
