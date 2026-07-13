# Detox Consolidation + Upstream Merge to 12.8.1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate all fork policy into a single `Detox.java`, then merge upstream Telegram 12.8.1, audit its new features for discovery surfaces, and hide the AI Editor.

**Architecture:** A new class `org.telegram.messenger.Detox` holds every kill-switch constant, every blocklist, every user-facing string, every predicate, and every guard. Upstream files keep only one-line bindings (`if (Detox.guardX(this)) return;`). Upstream code is never deleted or commented out — it is wrapped in a `Detox` flag check, so future merges flow into live text instead of conflicting with a comment. The `// CUSTOM:` marker convention is retired; the compiled token `Detox.` replaces it.

**Tech Stack:** Java 17, Gradle 8.x (CLI only — Android Studio is not installed), Android NDK, Telegram Android fork.

**Spec:** `docs/superpowers/specs/2026-07-13-merge-1281-and-detox-consolidation-design.md`

---

## Important context for the implementer

**There is no test framework in this repo.** Telegram Android ships no unit tests for this code, and none will be added — the classes involved (`ChatActivity`, `MessagesController`) are 20k–40k-line Android UI/singleton classes with no seams for testing. TDD does not apply here. The mechanical gates are instead:

1. **The compiler.** `./gradlew assembleAfatRelease` — ~10–15 s incremental on this machine. Run it after every task. A broken binding is a compile error, not a silent bug.
2. **The loss detector.** Every public member of `Detox` must have ≥1 call site in an upstream file. A member with zero call sites means a guard was lost. This is checked by grep and is the acceptance criterion for the merge.

Runtime behavior is verified manually on-device by the user at the very end.

**Build command (used in every task):**
```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease
```
Expected: `BUILD SUCCESSFUL`. APK lands at `TMessagesProj_App/build/outputs/apk/afat/release/app.apk`.

**Line numbers in this plan are from the pre-Phase-0 tree and will drift as you edit.** Do not trust them blindly. Locate each site by its `// CUSTOM:` marker text:
```bash
grep -rn "// CUSTOM:" TMessagesProj/src/ --include="*.java"
```
There are exactly **42 markers across 14 files** at the start. By the end of Task 4 there must be **zero**.

**Behavior is preserved by construction.** The guards move; they do not change. The one intentional exception is string unification (Task 2), which changes three user-visible messages.

---

## File structure

| File | Role after this plan |
|---|---|
| `TMessagesProj/src/main/java/org/telegram/messenger/Detox.java` | **NEW.** All fork policy: constants, blocklists, strings, predicates, guards. Has no upstream counterpart, so it can never conflict during a merge. |
| `.../messenger/BuildVars.java` | Loses the two blocklists. Keeps only real build flags. |
| `.../messenger/MessagesController.java` | 10 markers → `Detox.` bindings |
| `.../messenger/HashtagSearchController.java` | 1 marker → binding |
| `.../ui/ChatActivity.java` | 10 markers → bindings |
| `.../ui/ProfileActivity.java` | 3 markers → bindings |
| `.../ui/LaunchActivity.java` | 2 markers → bindings |
| `.../ui/ThemeActivity.java` | 1 marker → binding |
| `.../ui/Adapters/DialogsSearchAdapter.java` | 3 markers → bindings |
| `.../ui/Adapters/SearchAdapterHelper.java` | 2 markers → bindings |
| `.../ui/Components/SharedMediaLayout.java` | 1 marker → binding |
| `.../ui/Components/SearchViewPager.java` | 1 marker → binding |
| `.../ui/Components/PostsSearchContainer.java` | 1 marker → binding |
| `.../ui/Components/HashtagsSearchAdapter.java` | 1 marker → binding |
| `.../ui/Components/DialogsChannelsAdapter.java` | 2 markers → bindings |
| `CLAUDE.md` | Feature→File table replaced by the `Detox` inventory; verification protocol rewritten |

---

## Task 1: Create `Detox.java`; move blocklists; convert the 7 filter sites

**Why first:** these are pure mechanical moves with no behavior change and no UI involvement. If the build is green after this task, the foundation is sound.

**Files:**
- Create: `TMessagesProj/src/main/java/org/telegram/messenger/Detox.java`
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` (remove lines ~105–203)
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/Components/PostsSearchContainer.java` (marker ~:277)
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/Components/HashtagsSearchAdapter.java` (marker ~:137)
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/Components/DialogsChannelsAdapter.java` (markers ~:99, ~:119)
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/Adapters/SearchAdapterHelper.java` (markers ~:226, ~:233)
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java` (markers ~:287, ~:659)

---

- [ ] **Step 1: Create `Detox.java` with the full class**

This is the complete file. The guards are written now but not yet wired up — that happens in Task 2. That is intentional: the class compiles standalone, and Task 2 is then a pure call-site edit.

```java
/*
 * Detox fork policy.
 *
 * Every restriction this fork applies lives in this file: the kill switches, the
 * blocklists, the user-facing strings, the predicates and the guards. Upstream
 * files contain only one-line bindings into this class.
 *
 * Two rules keep this maintainable across upstream merges:
 *
 *   1. Never delete or comment out upstream code. Wrap it in a flag check from
 *      this class instead. Commented-out code cannot absorb an incoming upstream
 *      edit, so it conflicts on every merge; live code merges normally.
 *
 *   2. Every public member of this class must have at least one call site in an
 *      upstream file. A member with zero call sites means a merge silently ate a
 *      guard. See CLAUDE.md for the inventory and the post-merge check.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class Detox {

    private Detox() {
    }

    // ------------------------------------------------------------------
    // Kill switches. These back the suppressions that cannot be relocated:
    // an in-place `return false`, a changed tab count, a row that must not be
    // allocated. The upstream code stays live behind them.
    // ------------------------------------------------------------------

    public static final boolean SENSITIVE_BLOCKED = true;
    public static final boolean RECOMMENDATIONS_BLOCKED = true;
    public static final boolean PUBLIC_POSTS_BLOCKED = true;
    public static final boolean PROFILE_CHANNEL_BLOCKED = true;
    public static final boolean AI_EDITOR_BLOCKED = true;

    // ------------------------------------------------------------------
    // User-facing strings. One string per event, one bulletin style.
    // ------------------------------------------------------------------

    public static final String MSG_OPEN_CHANNEL = "Cannot open non-subscribed channels from mobile (use desktop)";
    public static final String MSG_OPEN_BOT = "Cannot open non-subscribed bots from mobile (use desktop)";
    public static final String MSG_SUBSCRIBE = "Please use desktop Telegram to subscribe to new channels";
    public static final String MSG_COMMENTS_NOT_SUBSCRIBED = "Subscribe to the channel first to view comments";
    public static final String MSG_COMMENTS_BLOCKLISTED = "Comments are blocked for this channel (see blocked_comments.txt)";
    public static final String MSG_DISCUSSION_NOT_SUBSCRIBED = "Subscribe to the channel first to view discussion";
    public static final String MSG_DISCUSSION_BLOCKLISTED = "Discussion is blocked for this channel (see blocked_comments.txt)";
    public static final String MSG_SEARCH_BLOCKED = "Search is blocked for this chat (see blocked_chats.txt)";
    public static final String MSG_INVITE_BLOCKED = "Cannot join via invite links on mobile (use desktop)";
    public static final String MSG_SENSITIVE_BLOCKED = "Sensitive (18+) content is blocked on this build";

    // ------------------------------------------------------------------
    // Blocklists, loaded lazily from assets. Edit the copies in the project
    // root, then copy them into TMessagesProj/src/main/assets/ and rebuild.
    // ------------------------------------------------------------------

    private static final Set<String> BLOCKED_CHAT_NAMES = new HashSet<>();
    private static final Set<String> BLOCKED_COMMENT_CHANNELS = new HashSet<>();
    private static boolean chatBlocklistLoaded;
    private static boolean commentBlocklistLoaded;

    /** Returns true once the asset has actually been read (or was missing); false if the app context is not up yet, so the caller retries later. */
    private static boolean load(String assetName, Set<String> into) {
        if (ApplicationLoader.applicationContext == null) {
            return false;
        }
        try {
            InputStream is = ApplicationLoader.applicationContext.getAssets().open(assetName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    into.add(line.toLowerCase());
                }
            }
            reader.close();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("Detox: loaded " + into.size() + " entries from " + assetName);
            }
        } catch (Exception e) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("Detox: failed to load " + assetName, e);
            }
        }
        return true;
    }

    private static boolean matches(Set<String> blocklist, String name) {
        if (name == null || blocklist.isEmpty()) {
            return false;
        }
        String normalized = name.toLowerCase().trim();
        for (String blocked : blocklist) {
            if (normalized.contains(blocked)) {
                return true;
            }
        }
        return false;
    }

    /** blocked_chats.txt — case-insensitive substring match on the chat/user display name. */
    public static boolean isNameBlocked(String name) {
        if (!chatBlocklistLoaded) {
            chatBlocklistLoaded = load("blocked_chats.txt", BLOCKED_CHAT_NAMES);
        }
        return matches(BLOCKED_CHAT_NAMES, name);
    }

    /** blocked_comments.txt — channels we read but do not engage with. */
    public static boolean isCommentBlocked(TLRPC.Chat chat) {
        if (chat == null) {
            return false;
        }
        if (!commentBlocklistLoaded) {
            commentBlocklistLoaded = load("blocked_comments.txt", BLOCKED_COMMENT_CHANNELS);
        }
        return matches(BLOCKED_COMMENT_CHANNELS, chat.title);
    }

    // ------------------------------------------------------------------
    // Predicates. Pure, no UI, safe to call from adapters and loops.
    // ------------------------------------------------------------------

    /** A channel or group we have not joined. */
    public static boolean isBlockedChat(TLRPC.Chat chat) {
        return chat != null && ChatObject.isNotInChat(chat);
    }

    /** A bot we have not added as a contact. */
    public static boolean isBlockedUser(TLRPC.User user) {
        return user != null && user.bot && !user.contact;
    }

    /** A message posted in a channel we have not joined. Used to filter search results. */
    public static boolean isBlockedMessage(int account, TLRPC.Message message) {
        if (message == null) {
            return false;
        }
        long dialogId = MessageObject.getDialogId(message);
        if (!DialogObject.isChatDialog(dialogId)) {
            return false;
        }
        return isBlockedChat(MessagesController.getInstance(account).getChat(-dialogId));
    }

    /** A message from a chat named in blocked_chats.txt. Distinct from isBlockedMessage: this is the name blocklist, not the subscription check. */
    public static boolean isNameBlockedMessage(int account, TLRPC.Message message) {
        if (message == null) {
            return false;
        }
        long dialogId = MessageObject.getDialogId(message);
        String name = null;
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
            if (user != null) {
                name = UserObject.getUserName(user);
            }
        } else if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            if (chat != null) {
                name = chat.title;
            }
        }
        return isNameBlocked(name);
    }

    /** A User or Chat named in blocked_chats.txt. For adapters that filter over a heterogeneous result list. */
    public static boolean isBlockedByName(Object obj) {
        String name = null;
        if (obj instanceof TLRPC.User) {
            name = UserObject.getUserName((TLRPC.User) obj);
        } else if (obj instanceof TLRPC.Chat) {
            name = ((TLRPC.Chat) obj).title;
        }
        return isNameBlocked(name);
    }

    /** A channel may be shown in a channel list only if we are subscribed — checking the cached local copy too, since search results carry a stripped Chat. */
    public static boolean isVisibleChannel(int account, TLRPC.Chat chat) {
        if (chat == null) {
            return false;
        }
        if (!ChatObject.isNotInChat(chat)) {
            return true;
        }
        TLRPC.Chat local = MessagesController.getInstance(account).getChat(chat.id);
        return local != null && !ChatObject.isNotInChat(local);
    }

    // ------------------------------------------------------------------
    // Guards. Each returns true meaning: blocked, the bulletin has been shown,
    // the caller must return immediately.
    //
    // A null fragment falls back to a global bulletin, for the call sites that
    // are not inside a fragment (LaunchActivity's URL resolver, the shared-media
    // grid when it has no host fragment).
    // ------------------------------------------------------------------

    private static boolean bulletin(BaseFragment fragment, String text) {
        if (fragment != null) {
            BulletinFactory.of(fragment).createErrorBulletin(text).show();
        } else {
            BulletinFactory.global().createErrorBulletin(text).show();
        }
        return true;
    }

    public static boolean guardOpen(BaseFragment fragment, TLRPC.Chat chat) {
        if (!isBlockedChat(chat)) {
            return false;
        }
        return bulletin(fragment, MSG_OPEN_CHANNEL);
    }

    public static boolean guardOpen(BaseFragment fragment, TLRPC.User user) {
        if (!isBlockedUser(user)) {
            return false;
        }
        return bulletin(fragment, MSG_OPEN_BOT);
    }

    public static boolean guardOpenPeer(BaseFragment fragment, int account, long peerId) {
        if (peerId < 0) {
            return guardOpen(fragment, MessagesController.getInstance(account).getChat(-peerId));
        }
        return guardOpen(fragment, MessagesController.getInstance(account).getUser(peerId));
    }

    /** Joining a channel from mobile is always blocked — desktop is the intentional friction. Always returns true. */
    public static boolean guardSubscribe(BaseFragment fragment) {
        return bulletin(fragment, MSG_SUBSCRIBE);
    }

    /** Two tiers: not subscribed at all, or subscribed but listed in blocked_comments.txt. */
    public static boolean guardComments(BaseFragment fragment, TLRPC.Chat chat, boolean discussion) {
        if (isBlockedChat(chat)) {
            return bulletin(fragment, discussion ? MSG_DISCUSSION_NOT_SUBSCRIBED : MSG_COMMENTS_NOT_SUBSCRIBED);
        }
        if (isCommentBlocked(chat)) {
            return bulletin(fragment, discussion ? MSG_DISCUSSION_BLOCKLISTED : MSG_COMMENTS_BLOCKLISTED);
        }
        return false;
    }

    /** In-chat search, blocked for chats named in blocked_chats.txt. Exactly one of chat/user is non-null. */
    public static boolean guardSearch(BaseFragment fragment, TLRPC.Chat chat, TLRPC.User user) {
        String name = null;
        if (chat != null) {
            name = chat.title;
        } else if (user != null) {
            name = UserObject.getUserName(user);
        }
        if (!isNameBlocked(name)) {
            return false;
        }
        return bulletin(fragment, MSG_SEARCH_BLOCKED);
    }

    public static boolean guardSensitive(BaseFragment fragment) {
        if (!SENSITIVE_BLOCKED) {
            return false;
        }
        return bulletin(fragment, MSG_SENSITIVE_BLOCKED);
    }

    /** Invite links are always blocked on mobile. Always returns true. */
    public static boolean guardInviteLink(BaseFragment fragment) {
        return bulletin(fragment, MSG_INVITE_BLOCKED);
    }

    /**
     * Hashtag search is allowed only over our own messages, or scoped to a subscribed
     * chat / a contact / a non-bot user. A null target means a global public lookup —
     * that is the loophole this closes. Silent: shows no bulletin, the caller reports
     * an empty result set.
     */
    public static boolean guardHashtagSearch(int searchType, TLObject target) {
        if (searchType == ChatActivity.SEARCH_MY_MESSAGES) {
            return false;
        }
        if (target == null) {
            return true;
        }
        if (target instanceof TLRPC.Chat) {
            return isBlockedChat((TLRPC.Chat) target);
        }
        if (target instanceof TLRPC.User) {
            return isBlockedUser((TLRPC.User) target);
        }
        return true;
    }
}
```

Two deliberate differences from the code being replaced:

1. **The blocklist load no longer latches on failure.** `BuildVars.loadBlockedChats()` set `blocklistLoaded = true` *before* checking whether `ApplicationLoader.applicationContext` was null — so a single early call with no app context permanently left the blocklist empty for the process lifetime. `Detox.load()` returns `false` in that case and the next caller retries. This is a latent-bug fix, not a behavior change in the normal path.
2. **Blocklist entries are lowercased once at load** instead of on every comparison.

- [ ] **Step 2: Build**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease
```
Expected: `BUILD SUCCESSFUL`. `Detox.java` compiles standalone; nothing calls it yet.

- [ ] **Step 3: Strip the blocklists out of `BuildVars.java`**

Delete both `// CUSTOM:` blocklist blocks — everything from `// CUSTOM: Blocklist of chat names to exclude from search` (~line 105) through the closing brace of `isCommentBlocked` (~line 203). That removes `BLOCKED_CHAT_NAMES`, `loadBlockedChats()`, `isChatBlocked()`, `BLOCKED_COMMENT_CHANNELS`, `loadBlockedComments()`, `isCommentBlocked()`.

Then remove the imports that only those blocks used:
```java
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
```
Check first — `grep -n "HashSet\|Set<\|BufferedReader\|InputStream" TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` — and only drop an import if nothing else in the file uses it.

Leave the two remaining markers alone for now (`CHECK_UPDATES` at ~:30 and the `BuildConfig` credentials at ~:34); they are cleaned up in Task 4.

The build is now **expected to fail** — 5 call sites still reference the deleted methods. That is the point: the compiler is enumerating the work for you.

- [ ] **Step 4: Confirm the compiler lists exactly the sites you are about to fix**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease 2>&1 | grep -E "error:|BuildVars"
```
Expected: errors naming `BuildVars.isChatBlocked` in `DialogsSearchAdapter.java` (×2) and `BuildVars.isCommentBlocked` in `ChatActivity.java` and `ProfileActivity.java`. (The `ChatActivity`/`ProfileActivity` ones are the comment guards — leave them broken until Task 2; you may temporarily point them at `Detox.isCommentBlocked(currentChat)` to keep the tree compiling, and Task 2 will replace the whole block anyway.)

Simplest path: do Step 5 and Step 6 below, then in `ChatActivity.java` and `ProfileActivity.java` replace the bare `BuildVars.isCommentBlocked(<chat>.title)` calls with `Detox.isCommentBlocked(<chat>)`, leaving the surrounding guard blocks untouched for now.

- [ ] **Step 5: Convert the two subscription filters (byte-identical duplicates)**

`PostsSearchContainer.java` (~:277) and `HashtagsSearchAdapter.java` (~:137) contain the same 8-line block. In each, replace it with:

```java
if (Detox.isBlockedMessage(currentAccount, message)) {
    continue;
}
```

Match the local variable name for the message at each site (it may be `message` or `msg` — read the loop). Add `import org.telegram.messenger.Detox;` if the file does not already import from that package with a wildcard.

- [ ] **Step 6: Convert the remaining five filters**

`DialogsChannelsAdapter.java` (~:99 and the three loops at ~:119) — replace each copy of the predicate with:

```java
if (!Detox.isVisibleChannel(currentAccount, chat)) {
    continue;
}
```
(or, where the upstream shape is a boolean condition rather than a `continue`, use `Detox.isVisibleChannel(currentAccount, chat)` as the condition directly — keep the upstream control flow, only swap the predicate.)

`SearchAdapterHelper.java` (~:226, ~:233) — the custom code here is an injected `||` clause on an existing `continue` condition. Replace the clause:
```java
// was: || ChatObject.isNotInChat(chat)
|| Detox.isBlockedChat(chat)
```
```java
// was: || user.bot && !user.contact
|| Detox.isBlockedUser(user)
```

`DialogsSearchAdapter.java` `filter(Object obj)` (~:287) — replace the whole 13-line block with:
```java
if (Detox.isBlockedByName(obj)) {
    return false;
}
```

`DialogsSearchAdapter.java` message loop (~:659) — replace the whole 16-line block with:
```java
if (Detox.isNameBlockedMessage(currentAccount, message)) {
    continue;
}
```

- [ ] **Step 7: Build**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Verify no orphan references to the deleted BuildVars methods**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -rn "BuildVars.isChatBlocked\|BuildVars.isCommentBlocked" TMessagesProj/src/
```
Expected: no output.

- [ ] **Step 9: Commit**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git add -A && git commit -m "Detox: create policy class, move blocklists, convert result filters

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Convert the 13 guard sites; unify strings; restore commented-out upstream bodies

**Files:**
- Modify: `.../ui/ProfileActivity.java` (markers ~:6783 `onJoinClicked`, ~:6954 `openDiscussion`)
- Modify: `.../ui/ChatActivity.java` (markers ~:8493 JOIN button, ~:34000 `openSearchWithText`, ~:35441 + ~:35453 `URLSpanUserMention`, ~:38257 `didPressChannelAvatar`, ~:38449 `didPressUserAvatar`, ~:40602 `didPressCommentButton`, ~:40834 `didPressRevealSensitiveContent`)
- Modify: `.../ui/LaunchActivity.java` (markers ~:4166 username resolution, ~:4839 invite links)
- Modify: `.../messenger/MessagesController.java` (markers ~:21700 + ~:21708 `openChatOrProfileWith`, ~:21784 + ~:21794 `openByUserName` sync, ~:21826 + ~:21834 `openByUserName` async)
- Modify: `.../messenger/HashtagSearchController.java` (marker ~:196 `searchHashtag`)
- Modify: `.../ui/Components/SharedMediaLayout.java` (marker ~:3149 sensitive cell tap)

**The critical instruction for this task:** several of these guards sit above a large `/* ... */` block of commented-out upstream code (`onJoinClicked`'s body ~40 lines, the invite-link handler ~250 lines, the sensitive-reveal dialogs ~60 lines each). **Uncomment those bodies.** The guard above them returns first, so they become unreachable dead code — but they are once again *live text* that a future upstream merge can flow into, which is the entire point of this refactor. Do not leave them commented.

---

- [ ] **Step 1: `ProfileActivity.onJoinClicked()` (~:6783)**

Replace the guard block with:
```java
if (Detox.guardSubscribe(this)) {
    return;
}
```
Then **uncomment the original upstream body** below it.

- [ ] **Step 2: `ProfileActivity.openDiscussion()` (~:6954)**

Two-tier guard (10 lines) collapses to:
```java
if (Detox.guardComments(this, currentChat, true)) {
    return;
}
```
The `true` selects the "discussion" wording.

- [ ] **Step 3: `ChatActivity` JOIN button in the bottom overlay (~:8493)**

```java
if (Detox.guardSubscribe(this)) {
    return;
}
```
Uncomment the original join body below it. Note: this site currently uses `createSimpleBulletin(R.raw.chats_infotip, ...)`. It now uses the unified `createErrorBulletin` style via `Detox`. That is intended.

- [ ] **Step 4: `ChatActivity.didPressCommentButton()` (~:40602)**

```java
if (Detox.guardComments(this, currentChat, false)) {
    return;
}
```
The `false` selects the "comments" wording.

- [ ] **Step 5: `ChatActivity.openSearchWithText()` (~:34000)**

```java
if (Detox.guardSearch(this, currentChat, currentUser)) {
    return;
}
```

- [ ] **Step 6: `ChatActivity` `URLSpanUserMention` handler (~:35441, ~:35453)**

Two adjacent guards (channel leg and bot leg) become, in their respective branches:
```java
if (Detox.guardOpen(ChatActivity.this, chat)) {
    return;
}
```
```java
if (Detox.guardOpen(ChatActivity.this, user)) {
    return;
}
```
Read the surrounding code: the current shape is an `if/else`, so keep the upstream navigation call in the `else`, or invert to an early return — whichever preserves the existing control flow with fewer edits.

- [ ] **Step 7: `ChatActivity.didPressChannelAvatar()` (~:38257) and `didPressUserAvatar()` (~:38449)**

```java
if (Detox.guardOpen(ChatActivity.this, chat)) {
    return;
}
```
```java
if (asForward && Detox.guardOpen(ChatActivity.this, user)) {
    return;
}
```
Preserve the existing `asForward` condition on the user-avatar site — it only blocks forward headers, not every avatar tap.

Drop the two `FileLog.d("[BYPASS_FIX]...")` debug lines at these sites.

**String change here:** `didPressChannelAvatar` currently says *"Subscribing to channels on mobile is disabled. Please use desktop."* and will now say *"Cannot open non-subscribed channels from mobile (use desktop)"*. Intended (spec, Phase 0).

- [ ] **Step 8: `ChatActivity.didPressRevealSensitiveContent()` (~:40834)**

```java
if (Detox.guardSensitive(this)) {
    return;
}
```
Uncomment the original alert-flow body below it.

- [ ] **Step 9: `SharedMediaLayout` sensitive cell tap (~:3149)**

```java
if (Detox.guardSensitive(profileActivity)) {
    return;
}
```
`profileActivity` may be null; `Detox.guardSensitive` already falls back to `BulletinFactory.global()`, so the existing null-check plumbing at this site can go. Uncomment the original alert-flow body.

- [ ] **Step 10: `LaunchActivity` username-resolution callback (~:4166)**

Both legs (peerId < 0 → channel, peerId > 0 → bot) collapse to one call. Keep the existing `dismissLoading` call:
```java
if (Detox.guardOpenPeer(null, intentAccount, peerId)) {
    dismissLoading.run();
    return;
}
```
Pass `null` as the fragment only if no `BaseFragment` is in scope at this site; if one is (check for `getLastFragment()` or similar), pass it. Read the surrounding code before choosing.

**String change here:** the combined *"Cannot open non-subscribed channels/bots from mobile (use desktop)"* disappears; `guardOpenPeer` resolves the peer and emits the channel or bot string.

- [ ] **Step 11: `LaunchActivity` invite-link handler (~:4839)**

```java
if (Detox.guardInviteLink(null)) {
    dismissLoading.run();
    return;
}
```
Then **uncomment the ~250-line upstream invite-handling block below it.** This is the single biggest conflict-noise reduction in the whole refactor — that block is large, upstream churns it, and it has been sitting as a comment.

- [ ] **Step 12: `MessagesController.openChatOrProfileWith()` (~:21700, ~:21708)**

```java
if (Detox.guardOpen(fragment, chat)) {
    return;
}
```
```java
if (Detox.guardOpen(fragment, user)) {
    return;
}
```
Drop the `FileLog.d` lines. **String change:** both currently say *"Subscribing to channels on mobile is disabled…"* (the bot one wrongly used the channel wording); they now say the correct channel/bot string.

- [ ] **Step 13: `MessagesController.openByUserName()` — sync path (~:21784, ~:21794)**

The bot leg must still call `progress.end()` before returning:
```java
if (Detox.guardOpen(fragment, user)) {
    if (progress != null) {
        progress.end();
    }
    return;
}
```
```java
if (Detox.guardOpen(fragment, chat)) {
    if (progress != null) {
        progress.end();
    }
    return;
}
```
Read the current code: it already does whatever `progress` bookkeeping is required. Preserve it exactly — only the block/bulletin logic moves into `Detox`.

- [ ] **Step 14: `MessagesController.openByUserName()` — async callback (~:21826, ~:21834)**

Same two calls, inside the resolver callback, with the resolved `chat` / `user`:
```java
if (Detox.guardOpen(fragment, chat)) {
    return;
}
```
```java
if (Detox.guardOpen(fragment, user)) {
    return;
}
```
Preserve any `progress` bookkeeping present at these sites.

- [ ] **Step 15: `HashtagSearchController.searchHashtag()` (~:196)**

The ~25-line block collapses to a guard plus the silent-empty-result report, which stays at the call site because it touches `search` and `NotificationCenter`:

```java
if (Detox.guardHashtagSearch(searchType, chat)) {
    search.loading = false;
    search.endReached = true;
    search.count = 0;
    NotificationCenter.getInstance(currentAccount).postNotificationName(
        NotificationCenter.hashtagSearchUpdated,
        guid, search.count, search.endReached, search.getMask(), search.selectedIndex, 0
    );
    return;
}
```
(`chat` here is the `TLObject` target resolved above — verify the variable name in the current code.)

- [ ] **Step 16: Fix up the two temporary `Detox.isCommentBlocked` call sites from Task 1**

Steps 2 and 4 replaced those whole blocks, so the temporary calls are gone. Confirm:
```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -rn "isCommentBlocked" TMessagesProj/src/
```
Expected: only the definition inside `Detox.java` and the two call sites inside `Detox.guardComments`.

- [ ] **Step 17: Build**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease
```
Expected: `BUILD SUCCESSFUL`. If uncommenting a body surfaced a reference to something that no longer exists, fix it — that code was frozen at whatever version it was commented out in.

- [ ] **Step 18: Confirm the old strings are gone**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -rn "Subscribing to channels on mobile is disabled\|channels/bots from mobile" TMessagesProj/src/
```
Expected: no output (both retired by unification).

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -rn "BYPASS_FIX" TMessagesProj/src/
```
Expected: no output.

- [ ] **Step 19: Commit**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git add -A && git commit -m "Detox: convert 13 guard sites, unify strings, restore commented upstream bodies

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Convert the 11 in-place suppressions to `Detox` constant checks

These cannot become a call — they are edits *of* upstream code. Each becomes a one-liner reading a `Detox` constant, and each restores the upstream code it currently comments out.

**Files:**
- Modify: `.../messenger/MessagesController.java` (~:22622 `getChannelRecommendations`, ~:23495 `getContentSettings`, ~:23525 `setContentSettings`, ~:23551 `showSensitiveContent`)
- Modify: `.../ui/ThemeActivity.java` (~:702 `sensitiveContentRow`)
- Modify: `.../ui/ProfileActivity.java` (~:10520 `channelRow`)
- Modify: `.../ui/ChatActivity.java` (~:7563 `getItemCount`, ~:34124 `defaultSearchPage`)
- Modify: `.../ui/Components/SearchViewPager.java` (~:1495 `PUBLIC_POSTS_TYPE`)
- Modify: `.../ui/Adapters/DialogsSearchAdapter.java` (~:1327 inline hashtag preview request)

---

- [ ] **Step 1: `MessagesController.showSensitiveContent()` (~:23551)**

```java
public boolean showSensitiveContent() {
    if (Detox.SENSITIVE_BLOCKED) {
        return false;
    }
    // ... restored upstream body ...
}
```
Uncomment the upstream body below the early return.

- [ ] **Step 2: `MessagesController.setContentSettings(boolean)` (~:23525)**

```java
public void setContentSettings(boolean showSensitiveContent) {
    if (Detox.SENSITIVE_BLOCKED) {
        showSensitiveContent = false;
    }
    // ... upstream body, unchanged ...
}
```

- [ ] **Step 3: `MessagesController.getContentSettings(callback)` (~:23495)**

The custom code unconditionally strips `"sensitive"` from `ignoreRestrictionReasons` where upstream conditionally adds it. Restore the upstream shape behind the flag:

```java
if (Detox.SENSITIVE_BLOCKED) {
    ignoreRestrictionReasons.remove("sensitive");
} else {
    // ... restored upstream conditional add/remove ...
}
```

- [ ] **Step 4: `MessagesController.getChannelRecommendations()` (~:22622)**

```java
public ChannelRecommendations getChannelRecommendations(long chatId) {
    if (Detox.RECOMMENDATIONS_BLOCKED) {
        return null;
    }
    // ... restored upstream body ...
}
```
Uncomment the ~60-line upstream body.

- [ ] **Step 5: `ThemeActivity.updateRowsIds()` (~:702)**

Restore the commented row allocation, wrapped:
```java
if (!Detox.SENSITIVE_BLOCKED && contentSettings != null && contentSettings.sensitive_can_change) {
    sensitiveContentRow = rowCount++;
}
```
Read the currently-commented line to recover the exact upstream condition — the one above is the expected shape, but the file is the source of truth.

- [ ] **Step 6: `ProfileActivity.updateRowsIds()` (~:10520)**

Restore `channelRow` and `channelDividerRow`, wrapped:
```java
if (!Detox.PROFILE_CHANNEL_BLOCKED && <upstream condition>) {
    channelRow = rowCount++;
    channelDividerRow = rowCount++;
}
```
Recover `<upstream condition>` from the commented-out code at that site.

- [ ] **Step 7: `ChatActivity` hashtag tab strip — the coupled pair**

These two must change together. Desyncing them makes `scrollToTab(2, 2)` target a tab that does not exist and the app crashes on hashtag entry.

`getItemCount()` (~:7563):
```java
@Override
public int getItemCount() {
    return Detox.PUBLIC_POSTS_BLOCKED ? 2 : 3;
}
```

`defaultSearchPage` (~:34124):
```java
defaultSearchPage = Detox.PUBLIC_POSTS_BLOCKED ? 0 : (<restored upstream conditional>);
```
Recover `<restored upstream conditional>` from the commented block at that site (it picks page 2 for `channelHashtags` / `forcePublic` / public-channel cases).

- [ ] **Step 8: `SearchViewPager.updateItems()` (~:1495)**

```java
if (!Detox.PUBLIC_POSTS_BLOCKED && expandedPublicPosts) {
    items.add(new Item(PUBLIC_POSTS_TYPE));
}
```

- [ ] **Step 9: `DialogsSearchAdapter` inline hashtag preview (~:1327)**

Restore the commented `TL_channels_searchPosts` request block, wrapped:
```java
if (!Detox.PUBLIC_POSTS_BLOCKED && finalHashtag != null) {
    // ... restored upstream request block ...
}
```
`publicPosts` stays empty at runtime, so the "Public posts" header cell in this same file stays hidden through its existing `!publicPosts.isEmpty()` guard. Do not re-add the post-hoc non-subscribed-channel filter that used to live inside this block — `Detox.isBlockedMessage` covers that class of filtering, and this block never executes anyway.

- [ ] **Step 10: Build**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease
```
Expected: `BUILD SUCCESSFUL`. The restored bodies are the likely failure point — they were frozen when they were commented out and may reference members that have since moved. Fix against the current code.

- [ ] **Step 11: Commit**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git add -A && git commit -m "Detox: route in-place suppressions through kill-switch constants

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Retire `// CUSTOM:`; write the `Detox` inventory into CLAUDE.md

**This task must complete before Task 5 (`git merge`).** The inventory is the detector that makes "refactor before merge" safe; writing it after the merge would defeat its purpose.

**Files:**
- Modify: `.../messenger/BuildVars.java` (last 2 markers)
- Modify: `CLAUDE.md`

---

- [ ] **Step 1: Remove the last two `// CUSTOM:` markers**

In `BuildVars.java`, the two remaining markers annotate genuine build config, not policy. Rewrite them as plain comments explaining *why*, with no marker:

```java
public static boolean CHECK_UPDATES = false; // fork: no in-app update prompts, we build our own APKs
```
```java
// fork: API credentials come from local.properties (gitignored), never hardcoded
public static int APP_ID = BuildConfig.TELEGRAM_APP_ID;
public static String APP_HASH = BuildConfig.TELEGRAM_APP_HASH;
```

- [ ] **Step 2: Verify the marker convention is fully retired**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -rn "// CUSTOM:" TMessagesProj/src/
```
Expected: **no output.** If anything remains, it is a guard that was not converted — convert it before continuing.

- [ ] **Step 3: Generate the call-site inventory**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -rn "Detox\." TMessagesProj/src/ --include="*.java" | grep -v "/Detox.java:"
```
This is the authoritative list of bindings. Every public member of `Detox` must appear in it at least once.

- [ ] **Step 4: Check the invariant now, before the merge**

For each public member of `Detox` (5 constants, 10 strings, 7 predicates, 9 guards), confirm ≥1 call site in the output above. A member with zero call sites at this point means the refactor missed a site — find it and fix it.

The string constants are the exception: they are referenced only from inside `Detox` itself (by the guards), which is correct and expected. Exclude them from the invariant.

- [ ] **Step 5: Write the inventory into CLAUDE.md**

Replace the **Feature → File Mapping** section with a `Detox` inventory table. Shape:

| `Detox` member | Blocks | Call sites |
|---|---|---|
| `guardSubscribe` | Joining a channel from mobile | `ProfileActivity.onJoinClicked()`, `ChatActivity` bottom-overlay JOIN |
| `guardOpen(Chat)` | Opening a non-subscribed channel | `ChatActivity` @mention / `didPressChannelAvatar`, `MessagesController.openChatOrProfileWith` / `openByUserName` (×2) |
| ... | ... | ... |

Use the actual grep output from Step 3 to fill it — do not write it from memory.

Then replace the **Post-Merge Verification (Code-Level)** section with the new protocol, in prose:

> After resolving conflicts, verify that no guard was lost. Every public member of `Detox` must have at least one call site in an upstream file — a member with zero call sites means a merge silently ate a guard. List the bindings with `grep -rn "Detox\." TMessagesProj/src/ --include="*.java" | grep -v "/Detox.java:"` and check that inventory against the table above. String constants are exempt: they are referenced only from inside `Detox`.
>
> A call site being *present* is not enough — confirm it is still attached to the user-visible action it is meant to block. The classic post-merge failure (commit `598bbcf26`) is a guard that survives textually but reattaches to the wrong call site.
>
> Also confirm `gradle.properties` still reads `APP_PACKAGE=org.telegram.messenger.detox` and that `TMessagesProj_App/build.gradle` still sets `applicationId = APP_PACKAGE` rather than a hardcoded string. Silent regression here is a fork-killer: the app would install as stock Telegram.
>
> Finally, confirm no `// MERGE-FLAG:` annotations remain, and that `./gradlew assembleAfatRelease` is green.

Delete the old `// CUSTOM:` marker-count baseline ("42 across 14 files") — the convention no longer exists.

- [ ] **Step 6: Commit**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git add -A && git commit -m "Detox: retire // CUSTOM: markers, document the guard inventory

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Merge upstream 12.8.1

**Files:** many. `gradle.properties` and the 14 policy files are the ones that matter.

---

- [ ] **Step 1: Confirm a clean tree and a green build before touching the merge**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git status --short && ./gradlew assembleAfatRelease
```
Expected: no output from `git status`; `BUILD SUCCESSFUL`. Do not start the merge from a dirty or broken tree.

- [ ] **Step 2: Merge**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git merge 9b50143d8
```
`9b50143d8` is upstream's `update to 12.8.1 (6916)`, the head of `origin/master`. Upstream stopped tagging after 11.4.2, so we pin to the version-bump commit — the same way the 12.6.4 merge did.

Expect conflicts. Do **not** `git merge --abort` on the first painful file; abort only if the merge is genuinely unresolvable. Task 0–4 work is already committed and survives an abort.

- [ ] **Step 3: Resolve `gradle.properties` first**

Take upstream's version:
```properties
APP_VERSION_CODE=6916
APP_VERSION_NAME=12.8.1
```
Keep ours:
```properties
APP_PACKAGE=org.telegram.messenger.detox
RELEASE_KEY_PASSWORD=android
RELEASE_KEY_ALIAS=androidkey
RELEASE_STORE_PASSWORD=android
org.gradle.jvmargs=-Xmx4096M
org.gradle.java.home=/usr/lib/jvm/java-17-openjdk
```
Plus every other line we already set (`org.gradle.daemon`, `org.gradle.parallel`, `android.*`). When in doubt, keep our line.

- [ ] **Step 4: Resolve the java conflicts**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git diff --name-only --diff-filter=U
```

For each conflicting file: our side is now a one-line `Detox.` binding sitting inside otherwise-upstream code. In nearly every case the correct resolution is **take upstream's version of the surrounding code, and re-insert our `Detox.` line at the same semantic position**.

Where upstream changed the shape of a TLRPC type, a method signature, or an interface and the right answer is not obvious, mark it:
```java
// MERGE-FLAG: <what is uncertain>
```
and resolve it during the compile pass in Step 6. Every `// MERGE-FLAG:` must be gone before Task 6 closes.

- [ ] **Step 5: Stage and commit the merge**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git add -A && git commit -m "Merge upstream 12.8.1 (6916) into nerovny/detox

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 6: Build and drive out the compile errors**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease
```
Iterate until `BUILD SUCCESSFUL`. Commit fixes as you go — do not amend the merge commit.

---

## Task 6: Post-merge verification

**Files:** none modified unless a check fails.

---

- [ ] **Step 1: Run the loss detector**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -rn "Detox\." TMessagesProj/src/ --include="*.java" | grep -v "/Detox.java:"
```
Check the output against the inventory table in CLAUDE.md. **Every public member (constants, predicates, guards — not the string constants) must have ≥1 call site.** A member with zero call sites is a guard the merge ate. Restore it.

- [ ] **Step 2: Check call-site anchoring**

For every binding in the inventory, open the call site and confirm it is still attached to the user-visible action it is meant to block — not merely present somewhere in the file. Upstream's `ChatActivity` diff is ~3356 lines and hosts 10 bindings; this is where a guard is most likely to have reattached to the wrong call site (see commit `598bbcf26` for the historical example).

- [ ] **Step 3: Verify the package name**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -n "APP_PACKAGE" gradle.properties && grep -n "applicationId" TMessagesProj_App/build.gradle
```
Expected: `APP_PACKAGE=org.telegram.messenger.detox`, and `applicationId APP_PACKAGE` (not a hardcoded string).

- [ ] **Step 4: Verify no MERGE-FLAGs remain**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -rn "MERGE-FLAG" TMessagesProj/src/
```
Expected: no output.

- [ ] **Step 5: Verify the version landed**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -E "APP_VERSION" gradle.properties
```
Expected: `APP_VERSION_CODE=6916`, `APP_VERSION_NAME=12.8.1`.

- [ ] **Step 6: Build**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit any fixes**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git add -A && git commit -m "Post-merge verification fixes

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
(Skip if nothing needed fixing.)

---

## Task 7: Audit 12.7/12.8 for new discovery surfaces

**Files:** `Detox.java` plus whatever binding sites the audit turns up.

---

- [ ] **Step 1: Reconnoitre the upstream diff**

Search the 12.6.4 → 12.8.1 diff for surfaces that conflict with the fork's philosophy (no algorithmic discovery, no unsubscribed-channel content, no engagement loops):

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git diff --stat 009e97356 9b50143d8 -- TMessagesProj/src/main/java/ | tail -40
```

Look specifically for: new search entry points; new recommendation surfaces (anything shaped like `getChannelRecommendations`); new feed/explore/stories-discovery UI; new calls into public-content APIs (grep the TLRPC diff for new `TL_*search*`, `TL_*recommend*`, `TL_*public*`, `TL_*suggest*` types); new tabs in existing tab strips; new server-config-gated features (`MessagesController` app_config parsing).

- [ ] **Step 2: Report findings to the user and get a decision**

Present each finding as: what it is, where it surfaces, whether it is server-gated (i.e. whether a single kill switch exists, as with AI Editor), and a recommendation. **The user decides what gets blocked.** Do not implement anything until they have chosen.

- [ ] **Step 3: Implement the approved blocks**

Follow the same rules as Phase 0: policy in `Detox` (a new constant for a server-gated feature, a new guard for a click path), a one-line binding at the call site, upstream code wrapped rather than deleted, no `// CUSTOM:` markers.

- [ ] **Step 4: Build**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git add -A && git commit -m "Block new discovery surfaces introduced in 12.7/12.8

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Hide the AI Editor

**Files:**
- Modify: `.../messenger/MessagesController.java` (`aiEditorAvailable()`, ~:715 pre-merge)

The feature (server key `ai_compose`) is available to everyone; Premium only raises a daily limit. Its star button appears in exactly two files — `ChatActivityEnterView` (message input, which also covers message editing) and `ChatAttachAlert` (both media caption editors) — across 9 `showAiButton(...)` call sites, every one of which ANDs on `aiEditorAvailable()`. Both button views are created `View.GONE` and are only ever shown through those call sites. So a single `false` hides all of it.

---

- [ ] **Step 1: Add the kill switch binding**

```java
public boolean aiEditorAvailable() {
    if (Detox.AI_EDITOR_BLOCKED) {
        return false;
    }
    return aiComposeStyles != null && !aiComposeStyles.isEmpty();
}
```

`Detox.AI_EDITOR_BLOCKED` already exists (Task 1). Verify the method still has this shape after the merge — upstream may have changed it.

- [ ] **Step 2: Confirm there is no second path to the button**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -rn "showAiButton\|new AIEditorAlert" TMessagesProj/src/ --include="*.java"
```
Expected: every `showAiButton(...)` call passes a condition that includes `aiEditorAvailable()`, and every `new AIEditorAlert(...)` sits inside a click handler on a view that is only made visible through `showAiButton`. If 12.8.1 added a call site that does **not** consult `aiEditorAvailable()`, add an early return in `ChatActivityEnterView.showAiButton(boolean)` and `ChatAttachAlert.showAiButton(boolean)`:
```java
if (Detox.AI_EDITOR_BLOCKED) {
    show = false;
}
```

Not touched by decision: the "AI Editor" row in the Telegram Premium feature list (`PremiumPreviewFragment`, `PREMIUM_FEATURE_AI_EDITOR = 42`). It does not consult `aiEditorAvailable()` and does not occupy space in the input.

- [ ] **Step 3: Build**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git add -A && git commit -m "Hide AI Editor via Detox kill switch

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Rewrite CLAUDE.md for the new architecture

**Files:**
- Modify: `CLAUDE.md`

---

- [ ] **Step 1: Update the facts**

- Base version → `Telegram Android 12.8.1 (build 6916)`.
- Build Commands section: Android Studio is gone. All builds are CLI: `./gradlew assembleAfatRelease` (~10–15 s incremental), `./gradlew assembleAfatDebug`, `./gradlew clean`. Remove the Android Studio setup and troubleshooting sections (NDK-via-SDK-Manager, Invalidate Caches, Trusted Projects, build-variant selection in the UI); keep the NDK version requirement and the Play Protect / install notes.
- Custom Code Marker section: replace the `// CUSTOM:` convention with the `Detox` convention. State the two rules: policy lives in `Detox.java`; upstream code is wrapped, never commented out.

- [ ] **Step 2: Confirm the inventory table is accurate post-merge**

The table was written in Task 4 against the pre-merge tree. Regenerate the call sites and correct any that moved:
```bash
cd /home/locutus/dev/telegram/telegram-nosearch && grep -rn "Detox\." TMessagesProj/src/ --include="*.java" | grep -v "/Detox.java:"
```

- [ ] **Step 3: Add the new feature sections**

- **AI Editor disabled** — what it was, the single server-gated switch (`ai_compose` → `aiEditorAvailable()`), the two files that hosted the button, and the note that the Premium feature row is deliberately untouched.
- Whatever Task 7 produced.

- [ ] **Step 4: Update the Feature Testing Checklist**

Add rows:

| Test Case | Steps | Expected Result |
|---|---|---|
| AI star button hidden (message input) | Open any chat, type several lines of text | The AI star button never appears |
| AI star button hidden (media caption) | Attach a photo, type several lines in the caption (both caption positions) | The AI star button never appears |

And a note that three blocking messages changed wording in this cycle (forward header, story channel link, reply icon → now *"Cannot open non-subscribed channels from mobile (use desktop)"*), so a tester must not mistake that for a merge regression.

Plus rows for anything Task 7 blocked.

- [ ] **Step 5: Commit**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && git add -A && git commit -m "Document Detox architecture, 12.8.1 base, AI Editor block

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 6: Hand the APK to the user for on-device testing**

```bash
cd /home/locutus/dev/telegram/telegram-nosearch && ./gradlew assembleAfatRelease && ls -la TMessagesProj_App/build/outputs/apk/afat/release/app.apk
```
Report the APK path. The user installs and runs the Feature Testing Checklist. Do not claim the work is verified until they report back — the compiler and the inventory prove the guards *exist*, not that they *fire*.
