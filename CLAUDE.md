# Telegram No-Search - Anti-Dopamine Client

A modified version of the official Telegram Android client designed to reduce dopamine loop addiction by limiting discovery features and enabling focused communication.

## Quick Reference for Claude Code

### Build Commands
```bash
./gradlew assembleAfatDebug     # Debug APK (fast iteration)
./gradlew assembleAfatRelease   # Release APK
./gradlew clean                 # Clean build artifacts
```

### Key Directories
| Purpose | Path |
|---------|------|
| Main source code | `TMessagesProj/src/main/java/org/telegram/` |
| Runtime config files | `TMessagesProj/src/main/assets/` |
| Blocklists (edit these) | Project root: `blocked_chats.txt`, `blocked_comments.txt` |
| Build output | `TMessagesProj_App/build/outputs/apk/afat/` |

### Custom Code Marker
All custom modifications use: `// CUSTOM: [description]`

Find all custom code:
```bash
grep -r "// CUSTOM:" TMessagesProj/src/ --include="*.java"
```

### Before Making Changes
1. Read relevant section in this document
2. Search for existing `// CUSTOM:` markers in target file
3. Preserve all existing custom functionality
4. Add `// CUSTOM:` marker to any new modifications
5. Update this document if adding new features

---

## Architecture: Feature → File Mapping

### Blocklist System
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Chat blocklist loading | `BuildVars.java` | `loadBlockedChats()`, `isChatBlocked()` |
| Comment blocklist loading | `BuildVars.java` | `loadBlockedComments()`, `isCommentBlocked()` |
| Auto-update disabled | `BuildVars.java` | `CHECK_UPDATES = false` |
| API credentials | `BuildVars.java` | `APP_ID`, `APP_HASH` from BuildConfig |

### Search Filtering
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Dialog search filtering | `DialogsSearchAdapter.java` | `filter()` method |
| Message search filtering | `DialogsSearchAdapter.java` | Message processing loop in search results handler |
| Hashtag search filtering | `DialogsSearchAdapter.java` | Hashtag results processing |
| Global search channel filtering | `SearchAdapterHelper.java` | Global search result processing loop |
| Global search bot filtering | `SearchAdapterHelper.java` | Bot check in global search loop |
| Hashtag adapter filtering | `HashtagsSearchAdapter.java` | Search result processing |
| Public posts filtering | `PostsSearchContainer.java` | Message processing loop |
| In-chat search blocking | `ChatActivity.java` | `openSearchWithText()` |

### Channel Discovery Removal
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Recommended channels | `DialogsChannelsAdapter.java` | Recommendations processing loop |
| Channel search results | `DialogsChannelsAdapter.java` | Search results processing |
| Similar channels disabled | `MessagesController.java` | `getChannelRecommendations()` |

### Subscription & Access Control
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Profile JOIN button | `ProfileActivity.java` | `onJoinClicked()` |
| Chat JOIN button | `ChatActivity.java` | Bottom panel JOIN button handler |
| Discussion access | `ProfileActivity.java` | `openDiscussion()` |
| Comment button | `ChatActivity.java` | `didPressCommentButton()` callback |
| Profile channel links | `ProfileActivity.java` | `updateRowsIds()` - channelRow disabled |

### Link & Navigation Blocking
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Forward header (channels) | `ChatActivity.java` | `didPressChannelAvatar()` callback |
| Forward header (bots) | `ChatActivity.java` | `didPressUserAvatar()` callback |
| @mention clicks | `ChatActivity.java` | `URLSpanUserMention` handler |
| Username navigation (cached) | `MessagesController.java` | `openByUserName()` - cached entity path |
| Username navigation (async) | `MessagesController.java` | `openByUserName()` - async callback |
| URL intent blocking | `LaunchActivity.java` | Username resolution callback |
| Invite link blocking | `LaunchActivity.java` | `group != null` handler branch |

### UI Modifications
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Edition branding | `ProfileActivity.java` | Version row display |
| App name | `strings.xml` (all locales) | `AppName` string resource |

---

## How to Add New Custom Features

### Step 1: Plan the Modification
- Identify which file(s) need modification using the Feature → File Mapping table above
- Find existing `// CUSTOM:` markers nearby for context
- Determine if you need to add to an existing blocklist or create new logic

### Step 2: Implement with Markers
Every custom code block must include a marker comment:
```java
// CUSTOM: [Feature name] - [Brief description]
// Example:
// CUSTOM: Mobile subscription blocking - Prevents channel join on mobile
if (ChatObject.isNotInChat(currentChat)) {
    BulletinFactory.of(this).createErrorBulletin("Message here").show();
    return;
}
```

### Step 3: Update Configuration (if applicable)
If the feature uses a config file:
1. Add entries to the root config file (e.g., `blocked_chats.txt`)
2. Copy to assets: `cp [file] TMessagesProj/src/main/assets/[file]`

### Step 4: Test on Device
Since automated testing is not available:
- Build debug APK: `./gradlew assembleAfatDebug`
- Install on test device: `adb install -r TMessagesProj_App/build/outputs/apk/afat/debug/app.apk`
- Verify feature works as expected
- Verify no regression in related features

### Step 5: Update This Document
- Add feature to "Feature → File Mapping" table
- Add to "Feature Testing Checklist" section
- Update "Files with Custom Modifications" list if new file modified

---

## Project Overview

This is a fork of the official Telegram Android app with specific modifications to help users maintain healthy digital habits by:

1. **Blocking search in specific chats** - Prevent yourself from searching in distracting conversations
2. **Removing public channel discovery** - Hide unsubscribed public channels from all search results
3. **Filtering global search** - Blocked chats are excluded from all search contexts
4. **Mobile subscription blocking** - Require desktop for channel subscriptions to add intentional friction
5. **Comment access control** - Block comments on non-subscribed channels and specific blocklisted channels
6. **Link isolation** - Block navigation to non-subscribed channels via URLs, forwards, and @mentions
7. **Invite link blocking** - Prevent joining groups/channels via invite links on mobile
8. **Similar channels disabled** - Completely remove the "Similar Channels" recommendation feature

**Current Base Version**: Telegram Android 12.2.3 (build 6298)
**Package Name**: `org.telegram.messenger.detox`
**Custom Features**: Chat blocklist, search filtering, channel discovery removal, mobile subscription blocking, comment control, link isolation, invite blocking, similar channels disabled, profile channel blocking, auto-update disabled

---

## Key Modifications

### 1. Chat Blocklist System

**Quick Reference**:
- **Files**: `BuildVars.java`, `DialogsSearchAdapter.java`, `ChatActivity.java`
- **Functions**: `loadBlockedChats()`, `isChatBlocked()`, `filter()`, `openSearchWithText()`
- **Config**: `blocked_chats.txt` (root) → `assets/blocked_chats.txt`
- **User message**: "Search is blocked for this chat (see blocked_chats.txt)"

**Implementation**:

`TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`:
- `loadBlockedChats()` - Loads chat names from `assets/blocked_chats.txt`
- `isChatBlocked()` - Checks if a chat name matches any entry in the blocklist
- Text file-based configuration (easy to edit, no Java knowledge required)
- Case-insensitive substring matching for chat names
- Works with user names, group titles, and channel names

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

**Quick Reference**:
- **Files**: `DialogsSearchAdapter.java`
- **Functions**: `filter()` method, message search result processing loop
- **Config**: Uses `blocked_chats.txt` blocklist via `BuildVars.isChatBlocked()`

**Implementation**:

`TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java`:
- `filter()` method - Excludes blocked chats from dialog/user search results
- Message search loop - Filters messages from blocked chats during search result processing

Blocked chats will not appear in:
- Dialog/chat search
- User search
- Message search across all chats

### 3. In-Chat Search Blocking with User Feedback

**Quick Reference**:
- **Files**: `ChatActivity.java`
- **Functions**: `openSearchWithText()`
- **Config**: Uses `blocked_chats.txt` blocklist via `BuildVars.isChatBlocked()`
- **User message**: "Search is blocked for this chat (see blocked_chats.txt)"

**Implementation**:

`TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`:
- `openSearchWithText()` - Checks blocklist before opening search UI
- When you try to open search in a blocked chat, a notification popup appears
- Uses `BulletinFactory` to show user feedback referencing the configuration file

### 4. Public Channel Discovery Removal & Hashtag/Public Posts Filtering

**Quick Reference**:
- **Files**: `DialogsChannelsAdapter.java`, `SearchAdapterHelper.java`, `PostsSearchContainer.java`, `HashtagsSearchAdapter.java`, `DialogsSearchAdapter.java`
- **Functions**: Recommendation/search loops in each adapter, global search processing
- **Config**: None (hardcoded to filter non-subscribed channels)

This comprehensive filtering system blocks discovery of unsubscribed channels across all search contexts including hashtag searches and public posts.

**Implementation**:

`TMessagesProj/src/main/java/org/telegram/ui/Components/DialogsChannelsAdapter.java`:
- Recommended channels loop - Inverted logic to only show subscribed channels
- Search results loop - Modified to only show subscribed channels

`TMessagesProj/src/main/java/org/telegram/ui/Adapters/SearchAdapterHelper.java`:
- Global search loop - Always filters out `ChatObject.isNotInChat()` channels
- Bot filtering - Always filters out non-contact bots (`user.bot && !user.contact`)
- Removed the `allowGlobalResults` condition that previously allowed unsubscribed channels

`TMessagesProj/src/main/java/org/telegram/ui/Components/PostsSearchContainer.java`:
- Message loop - Filters messages from non-subscribed channels in "Public posts" search results
- Uses `ChatObject.isNotInChat()` to skip messages from channels you haven't joined

`TMessagesProj/src/main/java/org/telegram/ui/Components/HashtagsSearchAdapter.java`:
- Search result loop - Filters messages from non-subscribed channels in hashtag search results
- Processes hashtag searches (e.g., "#news", "$crypto") and only shows subscribed sources

`TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java`:
- Hashtag results loop - Filters hashtag search results in "Chats" tab to exclude non-subscribed channels

**Result**: You can only discover channels you're already subscribed to, and only bots you've added as contacts. Public channel and bot discovery via search is completely disabled across all search contexts:
- Regular channel search in "Channels" tab
- Global search results
- Hashtag searches (e.g., "#news", "$btc")
- "Public posts" tab results
- Inline hashtag previews in "Chats" tab

### 5. Mobile Subscription Blocking & Comment Access Control

**Quick Reference**:
- **Files**: `BuildVars.java`, `ProfileActivity.java`, `ChatActivity.java`
- **Functions**: `isCommentBlocked()`, `onJoinClicked()`, `openDiscussion()`, `didPressCommentButton()`, JOIN button handler
- **Config**: `blocked_comments.txt` (root) → `assets/blocked_comments.txt`
- **User messages**: See "User Feedback Messages" below

This feature implements a two-tier protection system to prevent impulsive channel engagement:

**Tier 1: Block Channel Subscription on Mobile**
**Tier 2: Block Comments on Non-Subscribed and Blocklisted Channels**

#### Design Philosophy

**Mobile = Impulsive, Desktop = Intentional**

- Mobile phones are where dopamine-driven behavior happens
- Desktop use is typically more deliberate and rational
- Forcing subscription via desktop creates a "cooling off period"
- Prevents the impulsive "found channel → subscribe → comment" loop

#### Configuration File

**File**: `blocked_comments.txt` (in project root)

**Locations**:
- **Edit**: `blocked_comments.txt` (project root)
- **Deployed**: `TMessagesProj/src/main/assets/blocked_comments.txt` (bundled in APK)

**How to use**:
1. Edit `blocked_comments.txt` in the project root
2. Add channel names (one per line) for subscribed channels you want to block comments on
3. Lines starting with `#` are comments
4. Copy to assets before building:
   ```bash
   cp blocked_comments.txt TMessagesProj/src/main/assets/blocked_comments.txt
   ```
5. Rebuild the app

**Example** (`blocked_comments.txt`):
```
# Channels where I want to read but not engage in comments
News Channel
Commentary Channel
```

#### Implementation Details

`TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`:
- `loadBlockedComments()` - Loads channel names from `assets/blocked_comments.txt`
- `isCommentBlocked()` - Checks if a channel name matches blocklist
- Case-insensitive substring matching

`TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`:
- `onJoinClicked()` - Blocks channel subscription on mobile
  - Shows: "Please use desktop Telegram to subscribe to new channels"
  - Prevents joining channels from profile/settings
- `openDiscussion()` - Two-tier blocking:
  - First check: Block if not subscribed - "Subscribe to the channel first to view discussion"
  - Second check: Block if in `blocked_comments.txt` - "Discussion is blocked for this channel (see blocked_comments.txt)"

`TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`:
- JOIN button handler (bottom panel) - Blocks subscription attempt with same message as profile button
- `didPressCommentButton()` - Two-tier blocking:
  - First check: Block if not subscribed - "Subscribe to the channel first to view comments"
  - Second check: Block if in `blocked_comments.txt` - "Comments are blocked for this channel (see blocked_comments.txt)"

#### How It Works

**Discovery Flow (Mobile)**:
1. User finds channel link in chat, similar channels, or user profile
2. User can preview channel content (read posts)
3. User **cannot** subscribe on mobile - must use desktop
4. User **cannot** access comments on non-subscribed channels

**Subscription Flow (Desktop Required)**:
1. User saves channel link or remembers channel name
2. User opens desktop Telegram (intentional action)
3. User subscribes to channel (deliberate choice, not impulsive)
4. User can now view channel on mobile as subscriber

**Comment Access Control**:
- **Non-subscribed channels**: Comments blocked automatically (can preview posts only)
- **Subscribed channels**: Comments allowed UNLESS channel is in `blocked_comments.txt`
- **Blocklisted channels**: Comments blocked even after subscription (opt-in read-only mode)

#### User Feedback Messages

All error messages reference configuration files and explain the restriction:

- **Subscription (both buttons)**: "Please use desktop Telegram to subscribe to new channels"
- **Comments (not subscribed)**: "Subscribe to the channel first to view comments"
- **Comments (blocklisted)**: "Comments are blocked for this channel (see blocked_comments.txt)"
- **Discussion (not subscribed)**: "Subscribe to the channel first to view discussion"
- **Discussion (blocklisted)**: "Discussion is blocked for this channel (see blocked_comments.txt)"

#### Benefits

1. **Prevents impulsive subscriptions** - Mobile is blocked, desktop is required
2. **Preview without commitment** - Can read channel posts before subscribing
3. **Natural cooling-off period** - Time delay between discovery and subscription
4. **Reduces comment toxicity exposure** - Can't engage in random channel comments
5. **Maintains blocklist for known problematic channels** - Even after subscription
6. **Future-proof** - New channels are protected by default until desktop subscription
7. **Escape hatch preserved** - `blocked_comments.txt` still works for subscribed channels

**Result**: You can discover and preview channels on mobile, but must use desktop to subscribe. Comments are blocked on all non-subscribed channels and additionally on channels in your blocklist. This creates intentional friction at the right moments in the engagement funnel.

### 6. Link Isolation System

**Quick Reference**:
- **Files**: `ChatActivity.java`, `MessagesController.java`, `LaunchActivity.java`
- **Functions**: `didPressChannelAvatar()`, `didPressUserAvatar()`, `URLSpanUserMention` handler, `openByUserName()`, URL intent handler
- **Config**: None (hardcoded to check subscription status)
- **User message**: "Cannot open non-subscribed channels from mobile (use desktop)"

This feature blocks navigation to non-subscribed channels through various click paths, creating a comprehensive isolation from discovery.

#### What's Blocked

1. **Forward Header Clicks**: When a message is forwarded from a channel, clicking "Forwarded from: Channel Name" is blocked if you're not subscribed to that channel
2. **URL Links**: Clicking `t.me/channelname` links in messages is blocked for non-subscribed channels
3. **@Mention Clicks**: Clicking `@channel_name` or `@bot_name` mentions in messages is blocked for non-subscribed channels and non-contact bots
4. **tg:// Deep Links**: All `tg://resolve` and similar protocol links are blocked for non-subscribed targets
5. **Article Channel Links**: Channel links within Instant View articles are blocked for non-subscribed channels

#### What's Allowed

- Links to channels you're already subscribed to work normally
- Links to regular users (non-bot) work normally
- Links to bots you've added as contacts work normally
- Forward headers from subscribed channels work normally

#### Implementation Details

`TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`:
- `didPressChannelAvatar()` callback - Blocks forward header clicks to non-subscribed channels
  - Checks `ChatObject.isNotInChat(chat)` before allowing navigation
  - Shows: "Cannot open non-subscribed channels from mobile (use desktop)"
- `didPressUserAvatar()` callback - Blocks forward header clicks to non-contact bots
  - Checks `user.bot && !user.contact` before allowing navigation
  - Shows: "Cannot open non-subscribed bots from mobile (use desktop)"
- `URLSpanUserMention` handler - Blocks @mention clicks
  - For channels (negative IDs): Checks `ChatObject.isNotInChat(chat)` before navigation
  - For bots (positive IDs): Checks `user.bot && !user.contact` before navigation
  - Shows appropriate error message for blocked navigation

`TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java`:
- `openByUserName()` method (cached entity path) - Blocks cached entity navigation
  - For channels: Checks `ChatObject.isNotInChat(chat)` before opening
  - For bots: Checks `user.bot && !user.contact` before opening
  - Shows: "Cannot open non-subscribed channels/bots from mobile (use desktop)"
- `openByUserName()` method (async callback) - Blocks async username resolution
  - For channels (`peerId < 0`): Checks `ChatObject.isNotInChat(chat)`
  - For bots (`peerId > 0`): Checks `user.bot && !user.contact`
  - Shows appropriate error message for blocked navigation

`TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java`:
- Username resolution callback - Blocks URL intent navigation
  - For channels (`peerId < 0`): Checks `ChatObject.isNotInChat(chat)`
  - For bots (`peerId > 0`): Checks `user.bot && !user.contact`
  - Shows: "Cannot open non-subscribed channels/bots from mobile (use desktop)"

#### User Feedback Messages

- **Forward headers (channels)**: "Cannot open non-subscribed channels from mobile (use desktop)"
- **Forward headers (bots)**: "Cannot open non-subscribed bots from mobile (use desktop)"
- **URL links**: "Cannot open non-subscribed channels/bots from mobile (use desktop)"
- **@mentions (channels)**: "Cannot open non-subscribed channels from mobile (use desktop)"
- **@mentions (bots)**: "Cannot open non-subscribed bots from mobile (use desktop)"

### 7. Invite Link Blocking

**Quick Reference**:
- **Files**: `LaunchActivity.java`
- **Functions**: `group != null` handler branch in URL processing
- **Config**: None (all invite links blocked)
- **User message**: "Cannot join via invite links on mobile (use desktop)"

All invite links are blocked on mobile to prevent impulsive joining of new groups/channels.

#### What's Blocked

- `t.me/+AbCdEfG` style invite links
- `t.me/joinchat/...` style invite links
- `tg://join?invite=...` deep links

#### Implementation Details

`TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java`:
- `group != null` handler - Blocks at start of invite link processing
  - Intercepts before any API call is made
  - Shows: "Cannot join via invite links on mobile (use desktop)"
  - Original invite handling code is commented out but preserved

#### User Feedback Message

- "Cannot join via invite links on mobile (use desktop)"

### 8. Similar Channels Disabled

**Quick Reference**:
- **Files**: `MessagesController.java`
- **Functions**: `getChannelRecommendations()`
- **Config**: None (feature completely disabled)

The "Similar Channels" and "Similar Bots" recommendation feature is completely disabled.

#### What's Disabled

- "Similar Channels" section in channel profiles (SharedMediaLayout)
- "Similar Bots" section in bot profiles
- Channel recommendations shown after joining a channel (ChannelRecommendationsCell)
- The API call to fetch recommendations is never made

#### Implementation Details

`TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java`:
- `getChannelRecommendations()` - Returns null immediately
  - Prevents API call to `TL_channels_getChannelRecommendations`
  - `ChannelRecommendations.hasRecommendations()` returns false for all channels
  - UI components gracefully hide when no recommendations are available
  - Original code is commented out but preserved

#### Benefits

1. **Removes discovery vector** - Can't find new channels through recommendations
2. **Reduces distraction** - No "look at these similar channels" prompts
3. **Saves bandwidth** - No API calls for recommendations
4. **Clean UI** - No recommendation UI elements displayed

### 9. Profile Channel Links Blocked

**Quick Reference**:
- **Files**: `ProfileActivity.java`
- **Functions**: `updateRowsIds()` - channelRow/channelDividerRow disabled
- **Config**: None (feature completely disabled)

User profiles can display a linked "personal channel" as a native UI element. This feature is completely disabled to prevent channel discovery through user profiles.

#### What's Disabled

- "Personal Channel" row in user profile pages
- The clickable cell showing channel name, avatar, and subscriber count
- Any navigation to channels through this UI element

#### Implementation Details

`TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`:
- `updateRowsIds()` - Commented out `channelRow` and `channelDividerRow` creation
  - The row is never added to the profile layout
  - Original code preserved as comments for future reference
  - Click handlers become unreachable since `channelRow` remains `-1`

#### Benefits

1. **Removes discovery vector** - Can't find channels through user profiles
2. **Clean UI** - No channel link visible in profiles
3. **Consistent with other blocks** - Matches the approach used for Similar Channels
4. **No broken UI elements** - Hiding is cleaner than blocking clicks

### 10. Additional Features

#### Auto-Update Disabled

**Quick Reference**:
- **Files**: `BuildVars.java`
- **Config**: `CHECK_UPDATES = false`

`TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`:
- `CHECK_UPDATES = false` - Prevents Telegram's built-in update mechanism from prompting updates
- Ensures you stay on your custom build without update notifications

#### Custom Edition Branding

**Quick Reference**:
- **Files**: `ProfileActivity.java`
- **Location**: Version row display in Settings

`TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`:
- Settings screen displays: "Nikolay Nerovny edition (detox)" below version info
- Helps distinguish custom build from official Telegram

#### Secure API Credentials

**Quick Reference**:
- **Files**: `TMessagesProj/build.gradle`, `BuildVars.java`
- **Config**: `local.properties` (gitignored)

- API credentials loaded from `local.properties` (gitignored)
- No hardcoded credentials in source code
- Easy to configure per developer without committing secrets

#### App Name and Icon Configuration

**Quick Reference**:
- **Files**: `AndroidManifest.xml`, `values-*/strings.xml`

- App name: "Telegram (detox)" configured in all language files
- Icon and label attributes added to `<application>`, `DefaultIcon` activity-alias, and `LaunchActivity`
- Ensures proper display across all launchers (tested with Niagara Launcher)
- Localized app names prevent fallback to default "Telegram" in non-English languages

#### R8 Minification Disabled

**Quick Reference**:
- **Files**: All `build.gradle` files in app modules
- **Config**: `minifyEnabled false`

- `minifyEnabled false` for all build types (debug, release, standalone, etc.)
- Dramatically reduces build time (~2-3 minutes instead of ~8 minutes)
- APK size increases (~150MB instead of ~70MB)
- No functional difference - only affects build optimization
- Suitable for personal builds where APK size doesn't matter

---

## Feature Testing Checklist

Use this checklist after any modification. Each feature has specific verification steps.

### Chat Blocklist
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| Blocked chat hidden from search | 1. Add "TestChat" to `blocked_chats.txt` 2. Copy to assets 3. Rebuild 4. Search for "TestChat" | Chat does not appear in results |
| In-chat search blocked | 1. Open blocked chat 2. Tap search icon in toolbar | Notification: "Search is blocked for this chat (see blocked_chats.txt)" |
| Message search filtered | 1. Search for text known to exist in blocked chat | Messages from blocked chat not shown |

### Channel Discovery
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| Non-subscribed channels hidden in search | 1. Search for public channel you're not subscribed to | Channel does not appear in "Channels" tab |
| Subscribed channels visible | 1. Subscribe to channel on desktop 2. Search for it on mobile | Channel appears normally |
| Hashtag search filtered | 1. Search for hashtag (e.g., "#news") | Only messages from subscribed channels shown |
| Public posts filtered | 1. Search, go to "Public posts" tab | Only posts from subscribed channels shown |
| Non-contact bots hidden | 1. Search for bot you haven't contacted | Bot does not appear in results |

### Mobile Subscription Blocking
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| Profile JOIN blocked | 1. Open non-subscribed channel profile 2. Tap JOIN | Notification: "Please use desktop Telegram to subscribe to new channels" |
| Chat JOIN button blocked | 1. Open non-subscribed channel 2. Tap large JOIN button in bottom panel | Same notification as above |

### Comment Access Control
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| Non-subscribed comments blocked | 1. Open non-subscribed channel post 2. Tap comments button | Notification: "Subscribe to the channel first to view comments" |
| Blocklisted comments blocked | 1. Add subscribed channel to `blocked_comments.txt` 2. Copy to assets 3. Rebuild 4. Tap comments | Notification: "Comments are blocked for this channel (see blocked_comments.txt)" |
| Discussion blocked (not subscribed) | 1. Open non-subscribed channel profile 2. Tap Discussion | Notification: "Subscribe to the channel first to view discussion" |

### Link Blocking
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| t.me/ links blocked | 1. Receive message with `t.me/channel_name` (non-subscribed) 2. Tap link | Notification: "Cannot open non-subscribed channels from mobile (use desktop)" |
| @mention blocked (channel) | 1. See `@channel_name` in message (non-subscribed) 2. Tap mention | Same notification as above |
| @mention blocked (bot) | 1. See `@bot_name` in message (non-contact) 2. Tap mention | Notification: "Cannot open non-subscribed bots from mobile (use desktop)" |
| Forward header blocked | 1. See forwarded message from non-subscribed channel 2. Tap "Forwarded from:" header | Appropriate blocking notification |
| Invite links blocked | 1. Tap `t.me/+AbCdEfG` link | Notification: "Cannot join via invite links on mobile (use desktop)" |

### Similar Channels
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| No similar channels shown | 1. Open any channel profile 2. Look for "Similar Channels" section | Section does not appear |

### Profile Channel Links
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| No personal channel shown | 1. Open user profile (who has personal channel set) | "Personal Channel" row does not appear |

### UI Verification
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| Edition name displayed | 1. Open Settings 2. Scroll to bottom | Shows "Nikolay Nerovny edition (detox)" below version |
| App name correct | 1. Look at app in launcher | Shows "Telegram (detox)" |

---

## Setup & Installation

### Prerequisites

1. **Android Studio** with NDK 21.4.7075529 installed
2. **Telegram API credentials** from https://my.telegram.org/apps

### Setup Steps

1. **Clone this repository**

2. **Configure API credentials** (one-time):
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

3. **Configure blocklists** (optional):
   ```bash
   # Chat blocklist
   cp blocked_chats.txt.example blocked_chats.txt
   # Edit blocked_chats.txt with chat names to block
   cp blocked_chats.txt TMessagesProj/src/main/assets/blocked_chats.txt

   # Comment blocklist
   cp blocked_comments.txt.example blocked_comments.txt
   # Edit blocked_comments.txt with channels to block comments
   cp blocked_comments.txt TMessagesProj/src/main/assets/blocked_comments.txt
   ```

4. **Open project in Android Studio**

5. **Build the APK**:
   - Select build variant: `afatRelease` for TMessagesProj_App (or `afatDebug` for faster builds)
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Build time: ~2-3 minutes (R8 minification disabled for faster builds)
   - APK location: `TMessagesProj_App/build/outputs/apk/afat/release/app.apk`

**Note**: Google Services and Firebase are disabled for custom package names in this fork.

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

---

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

**App Name Shows "Telegram" Instead of "Telegram (detox)"**
```
Launcher shows default "Telegram" name when phone language is not English
```
**Fix**: Already fixed - all localized `strings.xml` files updated with "Telegram (detox)"

**Launcher Icon Shows Default Circle**
```
Launcher (e.g., Niagara) shows generic circle icon instead of Telegram arrow icon
```
**Fix**:
1. Clear launcher cache: Settings → Apps → [Your Launcher] → Storage → Clear Cache
2. Reboot phone
3. If still not working, use launcher's manual icon picker feature
4. Note: Icon and label attributes are properly configured in AndroidManifest.xml

---

## Quick Reference

### Common Tasks

**Update Chat Blocklist**
```bash
nano blocked_chats.txt  # Edit blocklist
cp blocked_chats.txt TMessagesProj/src/main/assets/blocked_chats.txt
# Rebuild in Android Studio
adb install -r TMessagesProj_App/build/outputs/apk/afat/release/app.apk
```

**Update Comment Blocklist**
```bash
nano blocked_comments.txt  # Edit comment blocklist
cp blocked_comments.txt TMessagesProj/src/main/assets/blocked_comments.txt
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

- **afatDebug**: Fast build (~2-3 min), debuggable, package: `org.telegram.messenger.detox.beta`
- **afatRelease**: Fast build (~2-3 min), production-ready, package: `org.telegram.messenger.detox`

Note: R8 minification is disabled for all build types, resulting in larger APKs (~150MB) but much faster build times.

### File Locations

- **Chat blocklist (edit)**: `blocked_chats.txt` (project root)
- **Chat blocklist (deployed)**: `TMessagesProj/src/main/assets/blocked_chats.txt`
- **Comment blocklist (edit)**: `blocked_comments.txt` (project root)
- **Comment blocklist (deployed)**: `TMessagesProj/src/main/assets/blocked_comments.txt`
- **API credentials**: `local.properties` (gitignored)
- **Release APK**: `TMessagesProj_App/build/outputs/apk/afat/release/app.apk`
- **Debug APK**: `TMessagesProj_App/build/outputs/apk/afat/debug/app.apk`
- **Package name config**: `gradle.properties` (APP_PACKAGE)
- **App name**: `TMessagesProj/src/main/res/values/strings.xml`

---

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

---

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

---

## Philosophy

This client is designed with these principles:

1. **Intentional Communication**: You should know who you want to talk to
2. **No Discovery**: Prevent algorithmic or search-based discovery of new content
3. **Source Code Configuration**: No fancy UI for restrictions - editing code creates friction (which is the point)
4. **Subtle Enforcement**: Silent blocking rather than error messages

---

## Privacy & Security

- No telemetry added
- No data sent to third parties beyond standard Telegram protocol
- All modifications are client-side only
- Uses official Telegram API with your own credentials

---

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

3. **Test all custom features** (see Feature Testing Checklist above)

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

### Files with Custom Modifications

All custom code is marked with `// CUSTOM:` comments for easy identification during conflict resolution:

```bash
# List all custom modifications with context
grep -r "// CUSTOM:" TMessagesProj/src/ -A 5 -B 1 --include="*.java"

# Count custom modifications
grep -r "// CUSTOM:" TMessagesProj/src/ --include="*.java" | wc -l
```

**Modified files:**
- `BuildVars.java` - Blocklist system, API credentials, auto-update disable
- `DialogsSearchAdapter.java` - Global search filtering, hashtag search filtering, blocked chat filtering
- `ChatActivity.java` - In-chat search blocking with notification, mobile subscription blocking, comment blocking, forward header blocking, @mention blocking
- `DialogsChannelsAdapter.java` - Channel discovery filtering
- `SearchAdapterHelper.java` - Global search channel filtering, bot filtering
- `PostsSearchContainer.java` - Public posts tab filtering for non-subscribed channels
- `HashtagsSearchAdapter.java` - Hashtag search filtering for non-subscribed channels
- `ProfileActivity.java` - Custom edition branding, mobile subscription blocking, comment blocking, profile channel links blocking
- `LaunchActivity.java` - URL link blocking for non-subscribed channels/bots, invite link blocking
- `MessagesController.java` - Similar channels feature disabled, @mention navigation blocking for channels/bots
- `build.gradle` files - Google Services disabled, API credentials from local.properties
- `settings.gradle` - Optional build variants disabled (Huawei, HockeyApp, Standalone, Tests)

---

## License

This project maintains the same GPL v2+ license as the official Telegram Android client.

## Contributing

This is a personal fork focused on specific anti-dopamine features. Feel free to fork and customize for your own needs.

## Disclaimer

This is an unofficial modification of Telegram. Use at your own risk. Not affiliated with or endorsed by Telegram.
