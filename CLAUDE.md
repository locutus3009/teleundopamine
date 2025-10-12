# Telegram No-Search - Anti-Dopamine Client

A modified version of the official Telegram Android client designed to reduce dopamine loop addiction by limiting discovery features and enabling focused communication.

## Project Overview

This is a fork of the official Telegram Android app with specific modifications to help users maintain healthy digital habits by:

1. **Blocking search in specific chats** - Prevent yourself from searching in distracting conversations
2. **Removing public channel discovery** - Hide unsubscribed public channels from all search results
3. **Filtering global search** - Blocked chats are excluded from all search contexts

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

### 3. In-Chat Search Blocking

**File**: `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`

- **Lines 34428-34437**: Modified `openSearchWithText()` to silently prevent search in blocked chats
- When you try to open search in a blocked chat, the action is simply ignored
- No error message shown (by design - keeps it subtle)

### 4. Public Channel Discovery Removal

**Files Modified**:

#### `TMessagesProj/src/main/java/org/telegram/ui/Components/DialogsChannelsAdapter.java`
- **Lines 99-101**: Inverted logic in recommended channels to only show subscribed channels
- **Lines 119-133**: Modified search results to only show subscribed channels

#### `TMessagesProj/src/main/java/org/telegram/ui/Adapters/SearchAdapterHelper.java`
- **Lines 226-228**: Always filter out `ChatObject.isNotInChat()` channels from global search
- Removed the `allowGlobalResults` condition that previously allowed unsubscribed channels

**Result**: You can only discover channels you're already subscribed to. Public channel discovery via search is completely disabled.

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

## Upstream

- **Origin**: https://github.com/DrKLO/Telegram (Official Telegram Android)
- **Private Fork**: git@github.com:locutus3009/teleundopamine.git

## License

This project maintains the same GPL v2+ license as the official Telegram Android client.

## Contributing

This is a personal fork focused on specific anti-dopamine features. Feel free to fork and customize for your own needs.

## Disclaimer

This is an unofficial modification of Telegram. Use at your own risk. Not affiliated with or endorsed by Telegram.
