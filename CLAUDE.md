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

### Sensitive (18+) Content Lockdown
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Read API force-false | `MessagesController.java` | `showSensitiveContent()` returns `false` |
| Write API defensive force-false | `MessagesController.java` | `setContentSettings(boolean)` first line |
| Local hygiene strip | `MessagesController.java` | `getContentSettings(callback)` callback |
| Toggle row hidden | `ThemeActivity.java` | `updateRowsIds()` row allocation commented out |
| Per-message reveal blocked (chat) | `ChatActivity.java` | `didPressRevealSensitiveContent()` |
| Per-message reveal blocked (shared media) | `SharedMediaLayout.java` | `isSensitive()` branch in cell-tap dispatcher |

### External Hashtag Lookup Blocked
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Controller-layer gate | `HashtagSearchController.java` | `searchHashtag()` allow-rule |
| "Public posts" tab hidden (global search) | `Components/SearchViewPager.java` | `updateItems()` `Item(PUBLIC_POSTS_TYPE)` commented out |
| "Public Posts" tab hidden (chat hashtag search) | `ChatActivity.java` | hashtag search adapter `getItemCount()` returns 2 (was 3); `defaultSearchPage` clamped to 0 |
| Inline preview API call blocked | `Adapters/DialogsSearchAdapter.java` | `if (finalHashtag != null) { ... TL_channels_searchPosts ... }` commented out |

### Link & Navigation Blocking
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Forward header (channels) | `ChatActivity.java` | `didPressChannelAvatar()` callback |
| Forward header (bots) | `ChatActivity.java` | `didPressUserAvatar()` callback |
| Channel reply icon clicks | `ChatActivity.java` | `didPressChannelAvatar()` - non-forward path |
| Story channel links | `MessagesController.java` | `openChatOrProfileWith()` |
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

**Current Base Version**: Telegram Android 12.6.4 (build 6666)
**Package Name**: `org.telegram.messenger.detox`
**Custom Features**: Chat blocklist, search filtering, channel discovery removal, mobile subscription blocking, comment control, link isolation, invite blocking, similar channels disabled, profile channel blocking, auto-update disabled

---

## Key Modifications

### 1. Chat Blocklist System

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
2. Add one chat name per line (case-insensitive substring match; `#` for comments; empty lines ignored)
3. Personal `blocked_chats.txt` is gitignored for privacy - use `blocked_chats.txt.example` as template
4. Copy to assets before building:
   ```bash
   cp blocked_chats.txt TMessagesProj/src/main/assets/blocked_chats.txt
   ```
5. Rebuild the app (no runtime configuration - changes require rebuild by design)

**Example** (`blocked_chats.txt`):
```
# My blocked chats
Distracting Friend
Time Waster Group
News Channel
```

### 2. Global Search Filtering

**Implementation**:

`TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java`:
- `filter()` method - Excludes blocked chats from dialog/user search results
- Message search loop - Filters messages from blocked chats during search result processing

Blocked chats will not appear in dialog/chat search, user search, or message search across all chats.

### 3. In-Chat Search Blocking with User Feedback

**Implementation**:

`TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`:
- `openSearchWithText()` - Checks blocklist before opening search UI
- When you try to open search in a blocked chat, a notification popup appears
- Uses `BulletinFactory` to show user feedback referencing the configuration file

### 4. Public Channel Discovery Removal & Hashtag/Public Posts Filtering

This comprehensive filtering system blocks discovery of unsubscribed channels across all search contexts including hashtag searches and public posts. You can only discover channels you're already subscribed to, and only bots you've added as contacts. Public channel and bot discovery is completely disabled in: regular channel search ("Channels" tab), global search results, hashtag searches (e.g., "#news", "$btc"), "Public posts" tab, and inline hashtag previews in "Chats" tab.

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

### 5. Mobile Subscription Blocking & Comment Access Control

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

**Access control summary**:
- **Non-subscribed channels**: Comments blocked automatically (can preview posts only)
- **Subscribed channels**: Comments allowed UNLESS channel is in `blocked_comments.txt`
- **Blocklisted channels**: Comments blocked even after subscription (opt-in read-only mode)

**Result**: You can discover and preview channels on mobile, but must use desktop to subscribe. This creates intentional friction at the right moments in the engagement funnel.

### 6. Link Isolation System

This feature blocks navigation to non-subscribed channels through various click paths, creating a comprehensive isolation from discovery.

**What's blocked**: forward header clicks, `t.me/channelname` URL links, `@channel_name`/`@bot_name` mentions, `tg://` deep links, and Instant View article channel links — all blocked for non-subscribed channels and non-contact bots. Links to subscribed channels, regular users (non-bot), and contact bots work normally.

**User Feedback Messages**:
- Forward headers (channels): "Cannot open non-subscribed channels from mobile (use desktop)"
- Forward headers (bots): "Cannot open non-subscribed bots from mobile (use desktop)"
- URL links / @mentions / deep links: "Cannot open non-subscribed channels/bots from mobile (use desktop)"

#### Implementation Details

`TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`:
- `didPressChannelAvatar()` callback - Blocks forward header clicks to non-subscribed channels
  - Checks `ChatObject.isNotInChat(chat)` before allowing navigation
- `didPressUserAvatar()` callback - Blocks forward header clicks to non-contact bots
  - Checks `user.bot && !user.contact` before allowing navigation
- `URLSpanUserMention` handler - Blocks @mention clicks
  - For channels (negative IDs): Checks `ChatObject.isNotInChat(chat)` before navigation
  - For bots (positive IDs): Checks `user.bot && !user.contact` before navigation

`TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java`:
- `openByUserName()` method (cached entity path) - Blocks cached entity navigation
  - For channels: Checks `ChatObject.isNotInChat(chat)` before opening
  - For bots: Checks `user.bot && !user.contact` before opening
- `openByUserName()` method (async callback) - Blocks async username resolution
  - For channels (`peerId < 0`): Checks `ChatObject.isNotInChat(chat)`
  - For bots (`peerId > 0`): Checks `user.bot && !user.contact`

`TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java`:
- Username resolution callback - Blocks URL intent navigation
  - For channels (`peerId < 0`): Checks `ChatObject.isNotInChat(chat)`
  - For bots (`peerId > 0`): Checks `user.bot && !user.contact`
  - Shows: "Cannot open non-subscribed channels/bots from mobile (use desktop)"

### 7. Invite Link Blocking

All invite links are blocked on mobile to prevent impulsive joining of new groups/channels. Blocked link formats: `t.me/+AbCdEfG`, `t.me/joinchat/...`, `tg://join?invite=...`.

#### Implementation Details

`TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java`:
- `group != null` handler - Blocks at start of invite link processing
  - Intercepts before any API call is made
  - Shows: "Cannot join via invite links on mobile (use desktop)"
  - Original invite handling code is commented out but preserved

### 8. Similar Channels Disabled

The "Similar Channels" and "Similar Bots" recommendation feature is completely disabled. This covers: the "Similar Channels" section in channel profiles (SharedMediaLayout), "Similar Bots" section in bot profiles, channel recommendations shown after joining (ChannelRecommendationsCell), and the API call to fetch recommendations.

#### Implementation Details

`TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java`:
- `getChannelRecommendations()` - Returns null immediately
  - Prevents API call to `TL_channels_getChannelRecommendations`
  - `ChannelRecommendations.hasRecommendations()` returns false for all channels
  - UI components gracefully hide when no recommendations are available
  - Original code is commented out but preserved

### 9. Profile Channel Links Blocked

User profiles can display a linked "personal channel" as a native UI element (clickable cell showing channel name, avatar, subscriber count). This feature is completely disabled to prevent channel discovery through user profiles.

#### Implementation Details

`TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`:
- `updateRowsIds()` - Commented out `channelRow` and `channelDividerRow` creation
  - The row is never added to the profile layout
  - Original code preserved as comments for future reference
  - Click handlers become unreachable since `channelRow` remains `-1`

### 10. Sensitive (18+) Content Lockdown

The "Show 18+ Content" toggle in Settings is hidden, and sensitive content is permanently invisible on this build. Tapping a redacted thumbnail produces a blocking bulletin instead of the upstream tap-to-reveal alert. Local enforcement only — the server-side flag is not actively synced.

**Implementation**:

`TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java`:
- `showSensitiveContent()` — returns `false` unconditionally. Original code preserved as a `/* */` comment.
- `setContentSettings(boolean)` — defensive force-false at the entry point. The user-facing path is hidden; this guards against any future caller. Note that the original method body remains live but runtime-unreachable: with the parameter forced to `false`, the `if (showSensitiveContent)` branch never executes and the `else` branch (which removes `"sensitive"` from `ignoreRestrictionReasons`) runs on every call.
- `getContentSettings(callback)` — when server state arrives, always strips `"sensitive"` from `ignoreRestrictionReasons` regardless of the server's `sensitive_enabled` flag. No active server-side sync.

`TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java`:
- `updateRowsIds()` — `sensitiveContentRow = rowCount++` is commented out. The row stays `-1`; the existing `>= 0` guards across the file (notification, click handler, rendering, highlight) all become natural no-ops.

`TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`:
- `didPressRevealSensitiveContent(ChatMessageCell)` — replaced with `BulletinFactory.of(this).createErrorBulletin("Sensitive (18+) content is blocked on this build").show(); return;`. Original alert-flow body preserved as a `/* */` block.

`TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java`:
- The inner `if (messageObject != null && messageObject.isSensitive())` branch in the cell-tap dispatcher is replaced with the same bulletin (with a `BulletinFactory.global()` fallback when `profileActivity` is null). Original alert-flow body preserved as a `/* */` block.

**What stays untouched**:
- Upstream's "18+" badge rendering on redacted thumbnails (`SharedPhotoVideoCell2`). Still appears as a "something hidden here" indicator. Tap is now blocked.
- Upstream's media-spoiler/blur effect on sensitive media. Existing rendering is fine; only the click handler changes.

**Future-merge stability**: the unique string `MessageShowSensitiveContentMediaTitle` is the search anchor for the per-message reveal alert. If a future upstream release adds a third reveal site, the post-merge verification walk (Feature → File Mapping table) will surface it.

### 11. External Hashtag Lookup Blocked

Hashtag search and discovery are restricted to subscribed sources only. After this lands, every hashtag entry point — clicking a hashtag in a message, the "Public posts" tab in the search UI, the inline hashtag preview in the search "Chats" tab, deep links — is gated by a single allow-rule at the API layer. External lookups against the public web (`TL_channels_searchPosts` with no target) are silently blocked. Searches scoped to a non-subscribed chat are also blocked. Searches across the user's own messages and within subscribed channels work normally.

**Implementation**:

`TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java`:
- `searchHashtag(String, int, int, int)` — single gate after username resolution. Allow rule: `searchType == SEARCH_MY_MESSAGES`, OR target is a subscribed `TLRPC.Chat`, OR target is a non-bot or contact `TLRPC.User`. Otherwise: silent block — set count=0, endReached=true, post `hashtagSearchUpdated`, return.

`TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java`:
- `updateItems()` — the `if (expandedPublicPosts) { items.add(new Item(PUBLIC_POSTS_TYPE)); }` block is commented out. The `expandedPublicPosts` field, the `PUBLIC_POSTS_TYPE` constant, and the unreachable tab-title / createView branches stay in place (same pattern as `channelRow` in `ProfileActivity`).

`TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java`:
- The inline hashtag-preview `TL_channels_searchPosts` request block (separate from `HashtagSearchController`) is commented out. `publicPosts` stays empty → the "Public posts" header cell at the rendering site (also in this file) is naturally hidden via its existing `!publicPosts.isEmpty()` guard. The post-hoc non-subscribed-channel filter that lived inside that block is dropped (no longer load-bearing).

`TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`:
- The hashtag-search tab strip *inside chats* (a separate `ViewPagerFixed.Adapter` from `SearchViewPager`) had three tabs: "This Chat" / "My Messages" / "Public Posts". The adapter's `getItemCount()` now returns `2` (was `3`), dropping the "Public Posts" tab. The unreachable case branches for `SEARCH_PUBLIC_POSTS` in `createView()` and `getItemTitle()` stay in place (defensive).
- `defaultSearchPage` is force-set to `0` (the "This Chat" tab). The original conditional that picked `2` for `channelHashtags` / `forcePublic` / public-channel cases is preserved as a `/* */` comment. Without this clamp, `scrollToTab(2, 2)` would target a non-existent tab and crash on hashtag entry.

**What stays untouched (defense-in-depth)**:
- The post-hoc filters in `HashtagsSearchAdapter.java` and `PostsSearchContainer.java`. They remain as a second line of defense against any future code path that bypasses the controller.

**Future-merge stability**: the unique strings `// CUSTOM: Block external hashtag lookup`, `Item(PUBLIC_POSTS_TYPE)`, and `TL_channels_searchPosts` are search anchors. If a future upstream release introduces a new direct caller of `TL_channels_searchPosts`, the post-merge verification walk surfaces it.

### 12. Additional Features

#### Auto-Update Disabled

`TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`:
- `CHECK_UPDATES = false` - Prevents Telegram's built-in update mechanism from prompting updates
- Ensures you stay on your custom build without update notifications

#### Custom Edition Branding

`TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`:
- Settings screen displays: "Nikolay Nerovny edition (detox)" below version info
- Helps distinguish custom build from official Telegram

#### Secure API Credentials

- API credentials loaded from `local.properties` (gitignored)
- No hardcoded credentials in source code
- Easy to configure per developer without committing secrets
- Files: `TMessagesProj/build.gradle`, `BuildVars.java`; Config: `local.properties`

#### App Name and Icon Configuration

- App name: "Telegram (detox)" configured in all language files
- Icon and label attributes added to `<application>`, `DefaultIcon` activity-alias, and `LaunchActivity`
- Ensures proper display across all launchers (tested with Niagara Launcher)
- Localized app names prevent fallback to default "Telegram" in non-English languages

#### R8 Minification Disabled

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
| Channel reply icon blocked | 1. Find message posted "as a channel" (non-subscribed) 2. Tap channel icon/avatar | Notification: "Subscribing to channels on mobile is disabled. Please use desktop." |
| Story channel link blocked | 1. Open Story with channel link (non-subscribed) 2. Tap channel link | Notification: "Subscribing to channels on mobile is disabled. Please use desktop." |
| Invite links blocked | 1. Tap `t.me/+AbCdEfG` link | Notification: "Cannot join via invite links on mobile (use desktop)" |

### Similar Channels
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| No similar channels shown | 1. Open any channel profile 2. Look for "Similar Channels" section | Section does not appear |

### Profile Channel Links
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| No personal channel shown | 1. Open user profile (who has personal channel set) | "Personal Channel" row does not appear |

### Sensitive (18+) Content Lockdown
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| 18+ toggle hidden in Settings | 1. Open Settings 2. Scroll to where the "Show 18+ Content" toggle would appear (between "Direct share" and "Send by Enter") | The toggle does not appear |
| 18+ media stays redacted | 1. Open a chat or shared-media gallery containing 18+ content | Media renders with the spoiler/blur overlay; "18+" badge is still visible (upstream behavior) |
| Tap on redacted 18+ media (chat) | 1. Tap a redacted 18+ thumbnail in a chat | Bulletin: "Sensitive (18+) content is blocked on this build". Media stays redacted |
| Tap on redacted 18+ media (shared media) | 1. Open a profile/shared-media gallery 2. Tap a redacted 18+ thumbnail | Same bulletin as above |

### External Hashtag Lookup Blocked
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| "Public posts" tab hidden (global search) | 1. Open the global search 2. Look at the tab list | The "Public posts" tab does not appear |
| "Public Posts" tab hidden (chat hashtag search) | 1. Open any chat 2. Tap a `#hashtag` in a message (or open search and type a hashtag) | The hashtag tab strip shows only "This Chat" and "My Messages"; no "Public Posts" tab. App does not crash. |
| Inline hashtag preview hidden | 1. In global search, type a hashtag like `#news` 2. Look at the "Chats" tab results | No "Public posts" header cell appears (no inline preview of public posts) |
| Hashtag click in subscribed channel | 1. Inside a subscribed channel, tap a `#hashtag` link in a message | Search opens; results limited to messages within the same subscribed channel |
| Hashtag click in non-subscribed channel preview | 1. Open a non-subscribed channel preview 2. Tap a `#hashtag` link in a visible message | Search opens; result list is empty (silent block) |
| `#tag@subscribed_channel` query | 1. Type `#news@somechannel` where the channel is subscribed | Search returns messages from that channel |
| `#tag@non_subscribed` query | 1. Type `#news@somechannel` where the channel is NOT subscribed | Search returns empty results (silent block) |

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

Chats are identified by name using `UserObject.getUserName(user)` for private chats, `chat.title` for groups/channels. The blocklist is loaded lazily from `assets/blocked_chats.txt` on first use and cached in memory.

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

#### Handling Conflicts

Your custom modifications will likely conflict with upstream changes. Common conflict areas:

1. **BuildVars.java** - API credentials, blocklist system
2. **DialogsSearchAdapter.java** - Search filtering
3. **ChatActivity.java** - In-chat search blocking
4. **SearchAdapterHelper.java** - Channel filtering
5. **build.gradle** - Google Services disabled

**Conflict resolution strategy:**

```bash
# During merge, check which files have conflicts
git status

# For each conflicting file, look for your custom modifications
# They are marked with "// CUSTOM:" comments
grep -n "// CUSTOM:" path/to/conflicting/file.java

# Edit the file and preserve your custom changes
# Keep markers: <<<<<<< HEAD, =======, >>>>>>>
# Remove one side or combine both intelligently

# After fixing a file:
git add path/to/file.java

# Continue merge
git merge --continue
```

**Quick conflict finder:**
```bash
# Find all your custom modifications
grep -r "// CUSTOM:" TMessagesProj/src/ --include="*.java" -n
```

#### Testing After Update

After merging from upstream:

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

#### Post-Merge Verification (Code-Level)

After resolving conflicts and before reporting the merge complete:

1. **Walk the Feature → File Mapping table** (above) top-to-bottom. For each row, open the listed file and confirm the listed function still contains the matching `// CUSTOM:` block, anchored to the user-visible action the row describes. The most common failure mode after a large upstream merge is a custom guard that survived textually but is now attached to the wrong call-site (see commit `598bbcf26` for a historical example). This pass is what catches that.

2. **Spot-check the Feature Testing Checklist** by greping for the user-facing strings (e.g. `"Subscribe to the channel first to view comments"`, `"Cannot open non-subscribed channels from mobile"`, `"Cannot join via invite links on mobile"`). Each should still appear at least once. Counts should be ≥ pre-merge.

3. **Verify the Android package name is intact.** `gradle.properties` must still read `APP_PACKAGE=org.telegram.messenger.detox`, and `TMessagesProj_App/build.gradle` must still set `defaultConfig.applicationId = APP_PACKAGE` (not a hardcoded string). The afatRelease build's applicationId must end up as `org.telegram.messenger.detox` and afatDebug as `org.telegram.messenger.detox.beta`. Silent regression of this is a fork-killer: the app would install as stock Telegram with our code, side-by-side with no longer being distinguishable from the official app.

4. **Search for any `// MERGE-FLAG:` annotations** introduced during conflict resolution — these mark spots where a TLRPC type or interface may have changed shape and need verification during the build/compile pass.

5. **Confirm the marker baseline.** The total `// CUSTOM:` marker count is currently **42 across 14 files** (`grep -rc "// CUSTOM:" TMessagesProj/src/ --include="*.java" | awk -F: '{s+=$2} END{print s}'`). A merge that drops the count below this baseline has lost a guard somewhere — investigate before reporting the merge complete.

This verification is intentionally code-level only. The build/runtime test happens once after all in-flight feature work for the merge has landed.

#### Recommended Update Schedule

- **Check for updates**: Monthly
- **Apply updates**: Only on **minor version releases** (x.Y.0)
- **Skip**: Patch releases (x.y.Z) unless critical security fixes
- **Avoid**: Updating during major versions (X.0.0) without extensive testing

#### Emergency: Abort Merge

If conflicts become too complex:

```bash
git merge --abort
# You'll return to the state before the merge started
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
- `DialogsSearchAdapter.java` - Global search filtering, hashtag search filtering, blocked chat filtering, inline public-posts hashtag preview API call blocked
- `ChatActivity.java` - In-chat search blocking with notification, mobile subscription blocking, comment blocking, forward header blocking, channel reply icon blocking, @mention blocking, per-message sensitive-content reveal blocked
- `DialogsChannelsAdapter.java` - Channel discovery filtering
- `SearchAdapterHelper.java` - Global search channel filtering, bot filtering
- `PostsSearchContainer.java` - Public posts tab filtering for non-subscribed channels
- `HashtagsSearchAdapter.java` - Hashtag search filtering for non-subscribed channels
- `ProfileActivity.java` - Custom edition branding, mobile subscription blocking, comment blocking, profile channel links blocking
- `LaunchActivity.java` - URL link blocking for non-subscribed channels/bots, invite link blocking
- `MessagesController.java` - Similar channels feature disabled, @mention navigation blocking for channels/bots, Story link blocking, sensitive-content read/write force-off, ignoreRestrictionReasons hygiene
- `ThemeActivity.java` - "Show 18+ Content" toggle hidden in Settings
- `Components/SharedMediaLayout.java` - Per-message sensitive-content reveal alert blocked
- `HashtagSearchController.java` - Controller-layer gate blocking external hashtag lookup
- `Components/SearchViewPager.java` - "Public posts" tab hidden in global search
- `build.gradle` files - Google Services disabled, API credentials from local.properties
- `settings.gradle` - Optional build variants disabled (Huawei, HockeyApp, Standalone, Tests)

---

## License

This project maintains the same GPL v2+ license as the official Telegram Android client.

## Contributing

This is a personal fork focused on specific anti-dopamine features. Feel free to fork and customize for your own needs.

## Disclaimer

This is an unofficial modification of Telegram. Use at your own risk. Not affiliated with or endorsed by Telegram.
