# Telegram No-Search - Anti-Dopamine Client

A modified version of the official Telegram Android client designed to reduce dopamine loop addiction by limiting discovery features and enabling focused communication.

## Quick Reference for Claude Code

### Build Commands

Android Studio is not installed. All builds run from the CLI; `org.gradle.java.home` in `gradle.properties` points at JDK 17.

```bash
./gradlew assembleAfatRelease   # Release APK. ~1 min incremental, ~7 min from clean
./gradlew assembleAfatDebug     # Debug APK
./gradlew clean                 # Clean build artifacts
```

Release builds are cheap enough to use as a checkpoint after every change. **The compiler is the primary correctness gate for this fork:** a `Detox.` binding that lost its call site is a compile error, not a silent behavior change. There is no test suite and none is planned — the classes involved (`ChatActivity` at ~40k lines, `MessagesController`) have no seams for unit tests.

### Key Directories
| Purpose | Path |
|---------|------|
| Main source code | `TMessagesProj/src/main/java/org/telegram/` |
| Runtime config files | `TMessagesProj/src/main/assets/` |
| Blocklists (edit these) | Project root: `blocked_chats.txt`, `blocked_comments.txt` |
| Build output | `TMessagesProj_App/build/outputs/apk/afat/` |

### The Detox Convention

All fork policy lives in **one file**: `TMessagesProj/src/main/java/org/telegram/messenger/Detox.java`. It holds the kill-switch constants, the blocklists, the user-facing strings, the predicates and the guards. It has no upstream counterpart, so it can never conflict during a merge.

Upstream files contain only one-line bindings into it:

```java
if (Detox.guardSubscribe(this)) {
    return;
}
```

Find every binding:
```bash
grep -rna "Detox\." TMessagesProj/src/ --include="*.java" | grep -v "/Detox.java:"
```

Two rules govern every modification:

1. **Never delete or comment out upstream code — wrap it in a `Detox` flag check.** A comment cannot absorb an incoming upstream edit, so it conflicts on every merge; live code merges normally. Note that `if (Detox.FLAG) return;` does not make the code below it an "unreachable statement" error — JLS 14.21 exempts `if` from unreachability analysis precisely for this idiom. That is what lets the upstream body stay live.

2. **Every public member of `Detox` must have at least one call site in an upstream file.** A member with zero call sites means a merge silently ate a guard. Members used only inside `Detox` are `private`, so they never trip this check.

The `// CUSTOM:` marker convention that predated this file is **retired**. There should be zero `// CUSTOM:` markers in the tree. The compiled token `Detox.` replaces them, and unlike a comment it cannot be dropped without breaking the build.

### Before Making Changes
1. Read relevant section in this document
2. Add the policy to `Detox.java` (a constant for a server-gated feature, a guard for a click path, a predicate for a result filter)
3. Add the one-line binding at the call site — wrap upstream code, never comment it out
4. Update the Detox Inventory table below
5. Build: `./gradlew assembleAfatRelease`

---

## Architecture: The Detox Inventory

Every restriction this fork applies, and where it binds into upstream code. This table **is** the post-merge loss detector: if a member below has no call site after a merge, a guard was eaten.

### Guards — block an action and show a bulletin
Each returns `true` meaning "blocked, bulletin shown, caller must return now".

| `Detox` member | Blocks | Call sites |
|---|---|---|
| `guardOpen(f, Chat)` | Opening a channel/group we have not joined | `ChatActivity` — `URLSpanUserMention` handler, `didPressChannelAvatar()` (forward header + channel reply icon); `MessagesController` — `openChatOrProfileWith()` (story links), `openByUserName()` cached path, `openByUserName()` async callback |
| `guardOpen(f, User)` | Opening a bot we have not added as a contact | `ChatActivity` — `URLSpanUserMention` handler, `didPressUserAvatar()` (forward headers only); `MessagesController` — `openChatOrProfileWith()`, `openByUserName()` cached path, `openByUserName()` async callback |
| `guardOpenPeer(f, acct, peerId)` | Same, for a raw peer id | `LaunchActivity` — username-resolution callback (t.me / tg:// URL intents) |
| `guardSubscribe(f)` | Joining a channel/group from mobile (always) | `ProfileActivity.onJoinClicked()`; `ChatActivity` bottom-overlay JOIN button; `ArticleViewer.joinChannel()` (Instant View channel block — it sends `TL_channels_joinChannel` directly, bypassing `addUserToChat`, so it needs its own guard); `TopicsFragment.joinToGroup()` (forum JOIN); the "Join channel" item in the channel-recommendation preview menu (`ChatActivity`, `SharedMediaLayout`); `JoinGroupAlert` (reachable from star-subscription renewal, not only from invite links) |
| `guardComments(f, chat, discussion)` | Comments/discussion: not subscribed, or listed in `blocked_comments.txt` | `ChatActivity.didPressCommentButton()` (`discussion=false`); `ProfileActivity.openDiscussion()` (`discussion=true`) |
| `guardSearch(f, chat, user)` | In-chat search for chats named in `blocked_chats.txt` | `ChatActivity.openSearchWithText()`, `openSearchWithUser()`, `openSearchWithChat()` |
| `guardSensitive(f)` | Revealing 18+ media | `ChatActivity.didPressRevealSensitiveContent()`; `SharedMediaLayout` cell-tap dispatcher (`isSensitive()` branch) |
| `guardInviteLink(f)` | Joining via invite link on mobile (always) | `LaunchActivity` — `group != null` branch |
| `guardHashtagSearch(type, target)` | Hashtag lookup outside our own messages / subscribed sources. **Silent** — no bulletin | `HashtagSearchController.searchHashtag()` |

### Predicates — filter results, no UI

| `Detox` member | Filters out | Call sites |
|---|---|---|
| `isBlockedChat(chat)` | Channels/groups we have not joined | `SearchAdapterHelper` global-search loop |
| `isBlockedUser(user)` | Non-contact bots | `SearchAdapterHelper` global-search loop |
| `isBlockedMessage(acct, msg)` | Messages posted in channels we have not joined | `PostsSearchContainer`; `HashtagsSearchAdapter`; `DialogsSearchAdapter` inline hashtag preview |
| `isNameBlockedMessage(acct, msg)` | Messages from chats named in `blocked_chats.txt` | `DialogsSearchAdapter` message-search loop |
| `isBlockedByName(obj)` | Users/chats named in `blocked_chats.txt` | `DialogsSearchAdapter.filter()` |
| `isVisibleChannel(acct, chat)` | Keeps *only* subscribed channels | `DialogsChannelsAdapter` — recommendations loop + the three search-result loops |

### Kill switches — suppressions that cannot be relocated

The upstream code stays live behind each flag.

| `Detox` member | Suppresses | Call sites |
|---|---|---|
| `SENSITIVE_BLOCKED` | 18+ content, everywhere | `MessagesController.showSensitiveContent()` (read), `setContentSettings()` (write), `getContentSettings()` callback (strips `"sensitive"` from `ignoreRestrictionReasons`); `ThemeActivity.updateRowsIds()` (hides the Settings toggle) |
| `RECOMMENDATIONS_BLOCKED` | Similar Channels / Similar Bots | `MessagesController.getChannelRecommendations()` — returns null, so every consumer hides itself |
| `PUBLIC_POSTS_BLOCKED` | The "Public posts" surfaces | `SearchViewPager.updateItems()` (global-search tab); `ChatActivity` hashtag tab strip — `getItemCount()` **and** `defaultSearchPage` (**hard-coupled: desyncing them makes `scrollToTab(2)` target a tab that is not there and crash**); `DialogsSearchAdapter` inline hashtag-preview request; the **"Posts" tab** in global search (`SearchViewPager`) plus `PostsSearchContainer.load()` — that tab used to send the raw query out and filter results only post-hoc |
| `PROFILE_CHANNEL_BLOCKED` | The "Personal Channel" row in user profiles | `ProfileActivity.updateRowsIds()` |
| `AI_EDITOR_BLOCKED` | The AI Editor. **12.8.1 deleted upstream's own server gate** — `aiEditorAvailable()` and the `ai_compose_styles` app_config key are gone, so this constant is now the *only* thing hiding the feature | `ChatActivityEnterView.showAiButton()`, `ChatAttachAlert.showAiButton()`, `CaptionPhotoViewer.showAiButton()`, `LinkManager.handleAiStyle()` (the `t.me/addstyle` deep link), `PremiumPreviewFragment` (drops the Premium feature row) |
| `PACK_SEARCH_BLOCKED` | Global public sticker/emoji **pack** search — the "Global Search Result" carousel with its add-pack CTA. New in 12.8; upstream itself had it commented out at 12.6.4 and switched it back on. Searching *installed* packs is unaffected — it never reaches this method | `MediaDataController.searchStickerSets()` — returns an empty list |
| `GUEST_BOT_HINTS_BLOCKED` | Server-ranked "guest bots" merged into the `@`-mention autocomplete | `MentionsAdapter` |

### Not policy — plain build config
`BuildVars.java`: `CHECK_UPDATES = false` (no in-app update prompts), `APP_ID` / `APP_HASH` from `local.properties`.

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

**Current Base Version**: Telegram Android 12.8.1 (build 6916)
**Package Name**: `org.telegram.messenger.detox`
**Custom Features**: Chat blocklist, search filtering, channel discovery removal, mobile subscription blocking, comment control, link isolation, invite blocking, similar channels disabled, profile channel blocking, auto-update disabled

---

## Key Modifications

Implementation detail — which method, which call site — lives in the **Detox Inventory** above; it is generated from the code and is the thing that must stay true. This section is the *why*, which the code cannot tell you.

### Design philosophy: mobile is impulsive, desktop is deliberate

Most of the fork's blocks do not remove a capability, they *relocate* it to the desktop. You can still discover and preview a channel on the phone; you just cannot subscribe from it. This inserts a cooling-off period exactly where the impulsive "found channel → subscribe → comment" loop closes, without making the client useless.

Consequences worth remembering:
- Every JOIN path must be guarded, including the ones that do not go through `MessagesController.addUserToChat` — Instant View sends `TL_channels_joinChannel` on its own.
- Comment access is two-tier: not subscribed at all (blocked automatically), or subscribed but listed in `blocked_comments.txt` (opt-in read-only mode).
- The blocks are **silent where possible and explanatory where not**. A search that would reach the public web returns an empty list rather than an error; a tap that you deliberately made must tell you why it did nothing.

### Blocklists

Two text files, loaded lazily from assets, case-insensitive substring match. There is deliberately no runtime UI: editing source and rebuilding *is* the friction.

| | Edit here | Deployed to |
|---|---|---|
| Chats where search is blocked, and which are hidden from search results | `blocked_chats.txt` (project root) | `TMessagesProj/src/main/assets/blocked_chats.txt` |
| Subscribed channels you want to read but not engage with | `blocked_comments.txt` (project root) | `TMessagesProj/src/main/assets/blocked_comments.txt` |

```bash
nano blocked_chats.txt
cp blocked_chats.txt TMessagesProj/src/main/assets/blocked_chats.txt
./gradlew assembleAfatRelease
```

One entry per line; `#` starts a comment; blank lines ignored. The personal copies are gitignored — `*.example` files are the templates.

### Things that look like bugs but are not

- **The 18+ badge still renders** on redacted media. That is upstream's "something is hidden here" indicator; only the tap is blocked. Leaving the badge is deliberate — it tells you the block worked.
- **The server-side sensitive-content flag is not synced.** `SENSITIVE_BLOCKED` enforces locally and strips `"sensitive"` from `ignoreRestrictionReasons` on every settings load. The account-level flag on Telegram's servers is left alone.
- **`ChatActivity`'s hashtag tab count and `defaultSearchPage` are coupled.** Both read `PUBLIC_POSTS_BLOCKED`. If they ever disagree, `scrollToTab(2)` targets a tab that is not there and the app crashes on hashtag entry.
- **Some upstream code behind a `Detox` flag is unreachable.** That is on purpose — see rule 1 of the Detox convention. Do not "clean it up".

### Other fork changes

- **Auto-update disabled** (`BuildVars.CHECK_UPDATES = false`) — we build our own APKs.
- **API credentials from `local.properties`** (gitignored), never hardcoded.
- **App name** "Telegram (detox)" in every locale's `strings.xml`, so non-English launchers do not fall back to "Telegram".
- **Edition branding** "Nikolay Nerovny edition (detox)" in the Settings version row.
- **R8 minification disabled** for all build types. Bigger APK, much faster builds; irrelevant for a personal build.
- **Google Services / Firebase disabled** for the custom package name.

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

### AI Editor Hidden
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| No AI button in the message input | 1. Open any chat 2. Type several lines of text | The AI star button never appears, at any text length |
| No AI button in media captions | 1. Attach a photo 2. Type several lines in the caption (both caption positions) 3. Open the photo viewer and type a caption there | The AI star button never appears in any of them |
| AI style deep link inert | 1. Tap a `t.me/addstyle?slug=...` link | Nothing opens |
| No AI Editor row in Premium | 1. Open the Telegram Premium screen 2. Scroll the feature carousel | No "AI Editor" row |

### Pack Search / Guest Bots
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| No global pack search | 1. Open the emoji/sticker keyboard panel 2. Type into its search box | Only *installed* packs match. No "Global Search Result" carousel of public packs, no add-pack button |
| No guest-bot suggestions | 1. Type `@` in a chat | Only your own inline bots are suggested; no server-ranked "guest bots" |

### JOIN Paths (all six must stay blocked)
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| Instant View JOIN | 1. Open an Instant View article that embeds a channel block 2. Tap JOIN | Bulletin: "Please use desktop Telegram to subscribe to new channels". **This one used to work** — it sent the join request directly |
| Forum JOIN | 1. Open a forum you have not joined 2. Tap JOIN | Same bulletin |
| Star-subscription renewal join | 1. Trigger a star-subscription renewal for a channel | Same bulletin |

### UI Verification
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| Edition name displayed | 1. Open Settings 2. Scroll to bottom | Shows "Nikolay Nerovny edition (detox)" below version |
| App name correct | 1. Look at app in launcher | Shows "Telegram (detox)" |

### Note for this cycle's testing
Three blocking messages **changed wording** in the Detox consolidation (forward header, story channel link, channel reply icon). They now say *"Cannot open non-subscribed channels from mobile (use desktop)"* instead of *"Subscribing to channels on mobile is disabled. Please use desktop."* — this is intentional string unification, not a merge regression.

---

## Setup & Installation

### Prerequisites

1. **Android SDK + NDK 21.4.7075529** (`sdk.dir` in `local.properties`). Android Studio is not required and is not installed here — everything runs through `./gradlew`.
2. **JDK 17** (`org.gradle.java.home` in `gradle.properties`)
3. **Telegram API credentials** from https://my.telegram.org/apps

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

4. **Build the APK**:
   ```bash
   ./gradlew assembleAfatRelease
   ```
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
./gradlew assembleAfatRelease
adb install -r TMessagesProj_App/build/outputs/apk/afat/release/app.apk
```

**Update Comment Blocklist**
```bash
nano blocked_comments.txt  # Edit comment blocklist
cp blocked_comments.txt TMessagesProj/src/main/assets/blocked_comments.txt
./gradlew assembleAfatRelease
adb install -r TMessagesProj_App/build/outputs/apk/afat/release/app.apk
```

**Rebuild & Reinstall (Quick)**
```bash
./gradlew assembleAfatRelease
adb install -r TMessagesProj_App/build/outputs/apk/afat/release/app.apk
```

**Find All Fork Bindings**
```bash
grep -rna "Detox\." TMessagesProj/src/ --include="*.java" | grep -v "/Detox.java:"
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
./gradlew assembleAfatRelease
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
App Start → (user searches) → Detox.isNameBlocked() → lazy-load assets/blocked_chats.txt
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

# For each conflicting file, find our bindings - every one is a call into Detox
grep -na "Detox\." path/to/conflicting/file.java

# Our side is a one-line binding sitting inside otherwise-upstream code. In nearly every
# case the right resolution is: take upstream's version of the surrounding code, then
# re-insert our Detox. line at the same semantic position.

# After fixing a file:
git add path/to/file.java

# Continue merge
git merge --continue
```

**Quick conflict finder:**
```bash
# Find all your custom modifications
grep -rna "Detox\." TMessagesProj/src/ --include="*.java" | grep -v "/Detox.java:"
```

#### Testing After Update

After merging from upstream:

1. **Clean build**:
   ```bash
   ./gradlew clean
   ```

2. **Build debug APK first** (faster iteration):
   ```bash
   ./gradlew assembleAfatDebug
   ```

3. **Test all custom features** (see Feature Testing Checklist above)

4. **Build release APK** after confirming everything works

5. **Update CLAUDE.md** if upstream changes affect custom features

#### Post-Merge Verification (Code-Level)

After resolving conflicts and before reporting the merge complete:

1. **Run the loss detector.** Every public member of `Detox` must have at least one call site in an upstream file. A member with zero call sites means the merge silently ate a guard. List the bindings and check them against the Detox Inventory above:
   ```bash
   grep -rna "Detox\." TMessagesProj/src/ --include="*.java" | grep -v "/Detox.java:"
   ```
   **The `-a` is load-bearing.** `grep` on this machine is `ugrep`, which will classify some of the larger Java files (`ChatActivity`, `MessagesController`) as binary and silently skip them — turning the detector into a false "a guard was lost" alarm, or worse, hiding a guard that really was lost. Always pass `-a`.

   String constants are exempt — they are referenced only from inside `Detox` itself. Members used only inside `Detox` are `private` and never appear here.

2. **Check call-site anchoring.** A binding being *present* is not enough — open it and confirm it is still attached to the user-visible action it is supposed to block. The classic failure after a large merge is a guard that survives textually but reattaches to the wrong call site (see commit `598bbcf26` for the historical example). `ChatActivity.java` is where this is most likely: it carries the largest upstream diff and hosts the most bindings.

3. **Confirm there are no `// CUSTOM:` markers.** The convention is retired; any that reappear came in from an old branch and represent policy living outside `Detox.java`:
   ```bash
   grep -rn "// CUSTOM:" TMessagesProj/src/
   ```

4. **Verify the Android package name is intact.** `gradle.properties` must still read `APP_PACKAGE=org.telegram.messenger.detox`, and `TMessagesProj_App/build.gradle` must still set `defaultConfig.applicationId = APP_PACKAGE` (not a hardcoded string). The afatRelease build's applicationId must end up as `org.telegram.messenger.detox` and afatDebug as `org.telegram.messenger.detox.beta`. Silent regression of this is a fork-killer: the app would install as stock Telegram with our code, no longer distinguishable from the official app.

5. **Search for any `// MERGE-FLAG:` annotations** introduced during conflict resolution — these mark spots where a TLRPC type or interface may have changed shape. All must be resolved before the merge is done.

6. **Build.** `./gradlew assembleAfatRelease` must be green. The compiler is the primary gate: a binding that lost its call site is a compile error, not a silent behavior change.

7. **Audit new upstream features.** A minor-version bump usually ships new surfaces. Diff the upstream range and look for anything shaped like discovery: new search entry points, recommendation surfaces, feeds, new tabs, new calls into public-content APIs (`TL_*search*`, `TL_*recommend*`, `TL_*public*`, `TL_*suggest*`), new server-config-gated features in `MessagesController`'s app_config parsing. A single server-gated boolean (as with `aiEditorAvailable()`) is usually the cheapest place to block.

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

### Files the fork touches

All policy lives in `TMessagesProj/src/main/java/org/telegram/messenger/Detox.java`. Every other file contains only one-line bindings into it:

```bash
grep -rna "Detox\." TMessagesProj/src/ --include="*.java" | grep -v "/Detox.java:"
```

That command is the authoritative list — it cannot go stale, unlike a hand-maintained one. See the **Detox Inventory** at the top of this document for what each member blocks and where it binds.

Non-policy fork changes live in: `BuildVars.java` (update check off, credentials from `local.properties`), the `build.gradle` files (Google Services disabled, credentials wired in), `settings.gradle` (optional build variants disabled), and `strings.xml` in every locale (app name).

---

## License

This project maintains the same GPL v2+ license as the official Telegram Android client.

## Contributing

This is a personal fork focused on specific anti-dopamine features. Feel free to fork and customize for your own needs.

## Disclaimer

This is an unofficial modification of Telegram. Use at your own risk. Not affiliated with or endorsed by Telegram.
