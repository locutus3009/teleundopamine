# Design: Detox consolidation + upstream merge to 12.8.1

**Date**: 2026-07-13
**Branch**: `nerovny/detox`
**Base before**: Telegram Android 12.6.4 (build 6666, upstream commit `009e97356`)
**Base after**: Telegram Android 12.8.1 (build 6916, upstream commit `9b50143d8`, head of `origin/master`)

## Problem

Three things land together in this cycle:

1. **The fork's custom code is scattered.** 42 `// CUSTOM:` markers across 14 upstream files. To answer "what does this fork actually block?" you must read 14 files. Worse, ~600 lines of upstream code sit *commented out* inside those files — which is precisely where upstream keeps editing, so every merge fights the same conflicts.
2. **The fork is two minor versions behind.** Upstream is at 12.8.1; we are pinned at 12.6.4.
3. **AI Editor is unwanted.** Telegram 12.6+ ships an "AI Editor" (server feature `ai_compose`) whose star button occupies space in the message input and in media caption editors. The user does not use it.

## Goals

- All fork policy lives in **one file**. Upstream files contain only one-line bindings.
- Merging upstream becomes **cheaper and safer**, with a mechanical detector for a lost guard.
- Fork updated to 12.8.1, with any new discovery/dopamine surfaces from 12.7/12.8 audited and blocked.
- AI Editor hidden.

## Non-goals

- **No bytecode weaving / AspectJ / Java agents.** Java reflection cannot intercept a call site; only a bytecode weaver can. On Android that means bolting an AGP-version-coupled Gradle transform onto a project that already juggles NDK, CMake and a disabled R8. It would change the failure mode from "does not compile, read the error" to "compiles, guard silently not applied" — unacceptable for a fork whose entire value is that the guards are actually in place. Rejected.
- **Full single-file consolidation is impossible** and is not the goal. Some modifications are edits *of* upstream code (a forced `return false`, a changed tab count), not insertions *into* it. They stay in place, reduced to a one-liner reading a `Detox` constant.
- No localization of user-facing strings (they remain English literals inside `Detox`; consolidating them makes a future translation trivial, but it is out of scope now).
- No runtime configuration UI. Editing source and rebuilding remains the intended friction.

---

## Architecture

### The single file

`TMessagesProj/src/main/java/org/telegram/messenger/Detox.java` — a new class with no upstream counterpart, therefore **it can never conflict during a merge**. It holds four kinds of member:

**1. Kill-switch constants** — for suppressions that cannot be relocated:

```java
public static final boolean SENSITIVE_BLOCKED       = true;
public static final boolean RECOMMENDATIONS_BLOCKED = true;
public static final boolean PUBLIC_POSTS_BLOCKED    = true;
public static final boolean PROFILE_CHANNEL_BLOCKED = true;
public static final boolean AI_EDITOR_BLOCKED       = true;
```

**2. Blocklists** — asset loading, parsing and lookup for `blocked_chats.txt` and `blocked_comments.txt`, moved wholesale out of `BuildVars`. `BuildVars` retains only genuine build flags (`CHECK_UPDATES`, `APP_ID`, `APP_HASH`).

**3. Predicates** — pure, no UI:

```java
public static boolean isBlockedChat(TLRPC.Chat chat);                    // ChatObject.isNotInChat
public static boolean isBlockedUser(TLRPC.User user);                    // user.bot && !user.contact
public static boolean isBlockedPeer(int account, long dialogId);
public static boolean isBlockedMessage(int account, TLRPC.Message msg);  // message from a non-subscribed channel
public static boolean isVisibleChannel(int account, TLRPC.Chat chat);    // subscribed, directly or via local chat
public static boolean isNameBlocked(String name);                        // blocked_chats.txt
public static boolean isNameBlockedMessage(int account, TLRPC.Message msg);
public static boolean isCommentBlocked(TLRPC.Chat chat);                 // blocked_comments.txt
```

**4. Guards** — return `true` meaning *"blocked; the bulletin has been shown; the caller must return now"*:

```java
public static boolean guardOpen(BaseFragment f, TLRPC.Chat chat);
public static boolean guardOpen(BaseFragment f, TLRPC.User user);
public static boolean guardOpenPeer(BaseFragment f, int account, long peerId);
public static boolean guardSubscribe(BaseFragment f);
public static boolean guardComments(BaseFragment f, TLRPC.Chat chat, boolean discussion);
public static boolean guardSearch(BaseFragment f, TLRPC.Chat chat, TLRPC.User user);
public static boolean guardSensitive(BaseFragment f);       // f may be null -> BulletinFactory.global()
public static boolean guardInviteLink(BaseFragment f);
public static boolean guardHashtagSearch(int searchType, TLObject target);  // silent, shows no bulletin
```

`guardSensitive` and `guardOpenPeer` accept a null fragment and fall back to `BulletinFactory.global()`; every other guard requires a fragment.

### The binding rule

**Upstream code is never deleted and never commented out. It is wrapped in a `Detox` check.**

Today's suppressions are commented-out upstream lines. That is the single worst pattern for a fork: upstream keeps editing exactly those lines, and a comment cannot absorb an incoming edit — it conflicts. Wrapping instead of commenting keeps the upstream text live, so future merges flow into it normally.

| Site | Before | After |
|---|---|---|
| `ProfileActivity.updateRowsIds()` | `// channelRow = rowCount++;` (commented) | `if (!Detox.PROFILE_CHANNEL_BLOCKED && <upstream cond>) { channelRow = rowCount++; ... }` |
| `SearchViewPager.updateItems()` | `// items.add(new Item(PUBLIC_POSTS_TYPE));` | `if (!Detox.PUBLIC_POSTS_BLOCKED && expandedPublicPosts) { items.add(...); }` |
| `ThemeActivity.updateRowsIds()` | `// sensitiveContentRow = rowCount++;` | `if (!Detox.SENSITIVE_BLOCKED && <upstream cond>) sensitiveContentRow = rowCount++;` |
| `DialogsSearchAdapter` inline hashtag preview | whole `TL_channels_searchPosts` request commented | `if (!Detox.PUBLIC_POSTS_BLOCKED && finalHashtag != null) { <upstream request> }` |
| `MessagesController.showSensitiveContent()` | `return false;`, body commented | `if (Detox.SENSITIVE_BLOCKED) return false;` + restored upstream body |
| `MessagesController.getChannelRecommendations()` | `return null;`, body commented | `if (Detox.RECOMMENDATIONS_BLOCKED) return null;` + restored upstream body |
| `ChatActivity` hashtag tab `getItemCount()` | `return 2;` | `return Detox.PUBLIC_POSTS_BLOCKED ? 2 : 3;` |
| `ChatActivity.defaultSearchPage` | `= 0;`, upstream conditional commented | `= Detox.PUBLIC_POSTS_BLOCKED ? 0 : (<upstream conditional>);` |
| `ProfileActivity.onJoinClicked()` etc. | guard + ~40 commented lines | `if (Detox.guardSubscribe(this)) return;` + **restored** upstream body (dead code behind the guard) |

The `getItemCount()`/`defaultSearchPage` pair is hard-coupled: a count of 2 with a default page of 2 makes `scrollToTab(2, 2)` target a nonexistent tab and crash. Today that coupling is held together by a comment. Routing both through `PUBLIC_POSTS_BLOCKED` makes it structural.

### The loss detector

After Phase 0, every binding is a compiled reference to a `Detox` member. This yields an invariant that does not exist today:

> **Every public member of `Detox` has at least one call site in an upstream file. A member with zero call sites is a guard the merge silently ate.**

A one-line binding is easy to lose in a conflict resolution and easy to miss by eye — this is the main risk of refactoring *before* merging. The detector converts that risk into something a `grep -rn "Detox\."` can catch. The inventory (member → call sites) is recorded in CLAUDE.md **before** the merge starts; it is the acceptance criterion for Phase 2.

The `// CUSTOM:` marker convention is retired entirely. The token `Detox.` replaces it, and unlike a comment it cannot be dropped without breaking the build.

---

## Phases

Each phase is a separate commit (or commit series) and ends with a green `assembleAfatRelease` before the next begins. Release builds are ~10–15 s incremental on this machine, so the build is used as a cheap checkpoint rather than a final gate.

### Phase 0 — Consolidation into `Detox.java`

Runs **before** the merge, so this merge already benefits from thin bindings and from the ~600 restored lines of upstream code.

Landed as four commits, each independently buildable, in this order (safest first):

1. **Blocklists + predicates.** Create `Detox.java`; move blocklist loading out of `BuildVars`; convert the 7 filter sites. Pure mechanical moves, no behavior change.
2. **Guards.** Convert the 13 guard sites. This is where the user-facing strings get unified (below) and where the commented-out upstream bodies get restored.
3. **Suppressions.** Convert the 11 in-place suppressions to `Detox` constant checks, restoring upstream code shape.
4. **Cleanup.** Remove every remaining `// CUSTOM:` marker; write the `Detox` inventory into CLAUDE.md.

**String unification.** Three different strings and two different bulletin styles currently fire for the same semantic event. They collapse to one set:

| Event | Unified string |
|---|---|
| Open a non-subscribed channel (link, @mention, forward header, story link, reply icon) | `Cannot open non-subscribed channels from mobile (use desktop)` |
| Open a non-contact bot | `Cannot open non-subscribed bots from mobile (use desktop)` |
| Join / subscribe | `Please use desktop Telegram to subscribe to new channels` |
| Comments blocked, not subscribed | `Subscribe to the channel first to view comments` |
| Comments blocked, blocklisted | `Comments are blocked for this channel (see blocked_comments.txt)` |
| Discussion blocked, not subscribed | `Subscribe to the channel first to view discussion` |
| Discussion blocked, blocklisted | `Discussion is blocked for this channel (see blocked_comments.txt)` |
| In-chat search blocked | `Search is blocked for this chat (see blocked_chats.txt)` |
| Invite link | `Cannot join via invite links on mobile (use desktop)` |
| Sensitive content | `Sensitive (18+) content is blocked on this build` |
| AI Editor | *(no string — the button simply never appears)* |

All guards use `createErrorBulletin`, one style. Behavior change, accepted: tapping a forward header or a story channel link now says "Cannot open non-subscribed channels…" instead of "Subscribing to channels on mobile is disabled…" — the new wording describes what actually happened.

The `LaunchActivity` combined channels/bots string (`Cannot open non-subscribed channels/bots from mobile (use desktop)`) disappears: `guardOpenPeer` resolves the peer and picks the channel or bot string.

Two `FileLog.d("[BYPASS_FIX]...")` debug lines are dropped.

**Verification:** `assembleAfatRelease` after each of the four commits; final walk of the `Detox` inventory confirming every member has a call site.

### Phase 1 — Merge upstream 12.8.1

```bash
git merge 9b50143d8    # "update to 12.8.1 (6916)", head of origin/master
```

Upstream stopped tagging releases after 11.4.2, so we pin to the version-bump commit, exactly as the 12.6.4 merge did.

Single jump rather than 12.7.0 → 12.7.3 → 12.8.1: conflicts do not compose linearly, and a stepwise path would mean resolving the same regions of `ChatActivity` three times.

**`gradle.properties`** is a guaranteed conflict. Take upstream's `APP_VERSION_NAME=12.8.1` and `APP_VERSION_CODE=6916`. Keep ours: `APP_PACKAGE=org.telegram.messenger.detox`, `RELEASE_KEY_*` / `RELEASE_STORE_PASSWORD`, `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk`, `org.gradle.jvmargs`.

**Expected conflict weight** (diff 12.6.4 → 12.8.1, our files):

| File | Diff |
|---|---|
| `ChatActivity.java` | ~3356 lines |
| `MessagesController.java` | ~1555 lines |
| `ProfileActivity.java` | ~939 |
| `LaunchActivity.java` | ~580 |
| `SharedMediaLayout.java` | ~313 |
| `DialogsSearchAdapter.java` | ~129 |
| `ThemeActivity.java` | ~26 |
| `BuildVars.java` | ~2 |

Where upstream has changed the shape of a TLRPC type or an interface and the correct resolution is not obvious, mark the spot `// MERGE-FLAG:` and resolve it during the compile pass. Every `// MERGE-FLAG:` must be gone before Phase 2 closes.

If conflicts prove unmanageable: `git merge --abort`. Phase 0 is already committed and survives.

### Phase 2 — Post-merge verification

1. **`Detox` inventory walk.** Every public member of `Detox` has ≥1 call site in an upstream file. Zero call sites = a guard was lost in a conflict. This is the primary check and it replaces the old `// CUSTOM:` marker-count baseline.
2. **Call-site anchoring.** For each member, confirm the call site is attached to the *user-visible action it is supposed to block*, not merely present somewhere in the file. The classic post-merge failure (see commit `598bbcf26`) is a guard that survives textually but reattaches to the wrong call site.
3. **Package name.** `gradle.properties` still reads `APP_PACKAGE=org.telegram.messenger.detox`; `TMessagesProj_App/build.gradle` still sets `applicationId = APP_PACKAGE`, not a hardcoded string. Silent regression here is a fork-killer.
4. **No `// MERGE-FLAG:` remains.**
5. **`assembleAfatRelease` is green.**

### Phase 3 — Audit new 12.7/12.8 features

Reconnaissance over the 12.6.4 → 12.8.1 diff for surfaces that conflict with the fork's philosophy: new search/discovery entry points, new recommendation or feed surfaces, new calls into discovery APIs, new tabs, new "explore"-type UI.

Findings are presented as a list with an assessment. The user decides what gets blocked. Approved blocks are implemented as new `Detox` members plus their bindings, following the same rules as Phase 0. `assembleAfatRelease`.

### Phase 4 — AI Editor kill switch

The feature is gated server-side by the `ai_compose` app_config key, surfaced through:

```java
// MessagesController
public String aiComposeStyles;
public boolean aiEditorAvailable() {
    return aiComposeStyles != null && !aiComposeStyles.isEmpty();
}
```

Every entry point ANDs on `aiEditorAvailable()`: 9 `showAiButton(...)` call sites across `ChatActivityEnterView` (message input, which also covers message editing) and `ChatAttachAlert` (both media caption editors). Both button views are created `View.GONE` and only ever shown through those call sites. There is no AI entry point in text selection, `PhotoViewer`, stories, or bot input.

The change is therefore one line:

```java
public boolean aiEditorAvailable() {
    if (Detox.AI_EDITOR_BLOCKED) return false;
    return aiComposeStyles != null && !aiComposeStyles.isEmpty();
}
```

Not touched: the "AI Editor" row in the Telegram Premium feature list (`PremiumPreviewFragment`, `PREMIUM_FEATURE_AI_EDITOR = 42`). It does not consult `aiEditorAvailable()` and does not occupy space in the input. Out of scope by decision.

`assembleAfatRelease`.

### Phase 5 — Documentation

CLAUDE.md is restructured around the new architecture:

- Base version → 12.8.1 (6916).
- The **Feature → File Mapping** table is replaced by the **`Detox` inventory**: member → what it blocks → call sites.
- The post-merge verification protocol is rewritten around the loss detector; the `// CUSTOM:` marker-count baseline (42 markers / 14 files) is removed, since the convention is retired.
- New sections: AI Editor disabled; whatever Phase 3 produced.
- Feature Testing Checklist updated (new rows for AI Editor and Phase 3 findings; the existing rows survive unchanged, since Phase 0 preserves behavior).
- Build commands section updated: Android Studio is gone, builds run from the CLI.

---

## Risks

**Phase 0 touches 14 files before the merge.** If the refactor silently breaks something, we would discover it mid-conflict-resolution and could not tell whose fault it was. Mitigations: Phase 0 is committed and built green *before* `git merge` is invoked; it lands as four separately-buildable commits; behavior is preserved by construction (guards move, they do not change).

**A one-line binding is easier to lose in a conflict than an eight-line block.** This is the cost of refactoring first. The loss detector is the countermeasure, and it is strictly stronger than what exists today — but only if the inventory is written into CLAUDE.md *before* the merge. That is a hard ordering requirement, not a nicety.

**String unification changes user-visible behavior** at the forward-header, story-link and reply-icon sites. Accepted, and it must not be mistaken for a merge regression during testing.

**`ChatActivity.java` carries a ~3356-line upstream diff** and hosts 10 of the 42 current guards. It is the single most likely place for a mis-anchored guard.

## Testing

No automated tests exist in this fork; the compiler and the inventory walk are the mechanical gates. Runtime verification is manual, on device, by the user, after Phase 5, using the CLAUDE.md Feature Testing Checklist plus:

| Test | Expected |
|---|---|
| AI star button in message input | Never appears, at any text length |
| AI star button in media caption (both caption positions) | Never appears |
| Every existing blocking feature | Behaves as before, except for the three unified strings |

The APK is built by Claude; installation and on-device testing are done by the user.
