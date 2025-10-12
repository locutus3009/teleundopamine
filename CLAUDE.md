# Telegram No-Search - Anti-Dopamine Client

A modified version of the official Telegram Android client designed to reduce dopamine loop addiction by limiting discovery features and enabling focused communication.

## Project Overview

This is a fork of the official Telegram Android app with specific modifications to help users maintain healthy digital habits by:

1. **Blocking search in specific chats** - Prevent yourself from searching in distracting conversations
2. **Removing public channel discovery** - Hide unsubscribed public channels from all search results
3. **Filtering global search** - Blocked chats are excluded from all search contexts

## Key Modifications

### 1. Chat Blocklist System

**File**: `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`

- **Lines 98-116**: Added `BLOCKED_CHAT_NAMES` HashSet and `isChatBlocked()` method
- Hardcoded blocklist approach (no UI required)
- Case-insensitive substring matching for chat names
- Works with user names, group titles, and channel names

**How to use**:
```java
public static final Set<String> BLOCKED_CHAT_NAMES = new HashSet<String>() {{
    add("Distracting Friend");
    add("Time Waster Group");
    add("News Channel");
}};
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

### Search Flow

```
User Input → DialogsSearchAdapter → filter() → (check blocklist) → Display Results
                                  ↓
                          SearchAdapterHelper → globalSearch → (filter unsubscribed)
                                  ↓
                          ChatActivity → openSearchWithText() → (check blocklist) → Allow/Block
```

## Building the Project

Follow the standard Telegram Android build instructions:

1. Clone this repository
2. Set up Android Studio with NDK
3. Configure your `api_id` and `api_hash` from https://my.telegram.org
4. Configure `release.keystore` and credentials in `gradle.properties`
5. Add Firebase `google-services.json`
6. Build the APK

See the main `README.md` for detailed build instructions.

## Customization Guide

### Adding Blocked Chats

Edit `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`:

```java
public static final Set<String> BLOCKED_CHAT_NAMES = new HashSet<String>() {{
    add("ChatName1");
    add("ChatName2");
    // Add more as needed
}};
```

Notes:
- Matching is case-insensitive
- Substring matching (e.g., "John" matches "John Doe")
- Rebuild the app after making changes
- No runtime configuration - changes require recompilation (by design)

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
