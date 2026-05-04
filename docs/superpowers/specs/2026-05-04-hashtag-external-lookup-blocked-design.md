# Hashtag External Lookup Blocked

**Date:** 2026-05-04
**Sub-project:** C of three. A (12.6.4 merge) and B (sensitive content lockdown) already landed on `nerovny/detox`.
**Branch:** `nerovny/detox` (in-place; no worktree).

## Goal

Close the hashtag external-lookup loophole. After this lands, hashtag search and discovery are restricted to:
- Searches across the user's own messages (`SEARCH_MY_MESSAGES`).
- Searches scoped to a *subscribed* channel/group, or a contact / non-bot user.

All other hashtag lookups — global public-posts search (`TL_channels_searchPosts` with no target) and chat-scoped searches against non-subscribed chats — are silently blocked at the API layer. The "Public posts" tab in the search UI and the inline "Public posts" preview in the search "Chats" tab are hidden.

The user's original ask: *"Enable discovery by a hash-tag (#) only to the subscribed channels/chat, explicitly disallow such an external lookup of hashtags — this is a loophole to any other chat probably with some explicit content."*

## Non-goals

- **Bulletin notification when a search is blocked.** Per user direction, silent block — mirror the upstream "username resolution failed" empty-result branch (count=0, endReached=true, post `hashtagSearchUpdated`, return).
- **Removing the `expandedPublicPosts` field, the `PUBLIC_POSTS_TYPE` constant, or the unreachable tab-title / createView branches** in `SearchViewPager`. They remain in place — same pattern as our `channelRow` suppression in `ProfileActivity`. Keeps the change minimal and survives upstream merges via the same `// CUSTOM:` recognition.
- **Touching the existing post-hoc filters** in `HashtagsSearchAdapter`, `PostsSearchContainer`, and `DialogsSearchAdapter::filter()` (hashtag results loop). They stay as defense-in-depth.
- **Build/runtime verification.** User runs the build pass once after this lands, completing the original 4-task brief (merge + verify + sensitive lockdown + hashtag loophole).

## The loophole structure

```
Hashtag click / "Public posts" tab / Hashtag-as-search-query / Inline preview in Chats tab
    │
    ├─► HashtagSearchController.searchHashtag(query, guid, searchType, loadIndex)
    │       branches:
    │         · SEARCH_MY_MESSAGES        → TL_messages_searchGlobal      ✓ allowed
    │         · #tag@username             → TL_messages_search            ⚠ leak if non-subscribed
    │         · SEARCH_PUBLIC_POSTS / SEARCH_CHANNEL_POSTS w/o target
    │                                     → TL_channels_searchPosts       ✗ external loophole
    │
    └─► DialogsSearchAdapter (separate, in-search "Chats" tab inline preview)
            sends its own TL_channels_searchPosts (around L1337)          ✗ external loophole
```

## Approach

Approach 1 (chosen): API-layer chokepoints + UI hide.

**Allow rule** (single predicate at the controller gate):
- `searchType == SEARCH_MY_MESSAGES` → allow.
- Target chat is a `TLRPC.Chat` and `!ChatObject.isNotInChat(chat)` → allow (subscribed channel/group).
- Target chat is a `TLRPC.User` that is not a non-contact bot → allow (DM with contact, or any non-bot user).
- Otherwise → silent block.

The block sets `search.loading = false`, `search.endReached = true`, `search.count = 0`, posts `NotificationCenter.hashtagSearchUpdated` with the empty result, and returns. UI shows an empty result list.

## The three enforcement sites

### Site 1 — `HashtagSearchController.searchHashtag()` controller-layer gate

`TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java`, around L194–195 (after the username resolution block; before `int limit = 21;`).

```java
// CUSTOM: Block external hashtag lookup - allow only SEARCH_MY_MESSAGES or
// searches scoped to a subscribed chat / contact / non-bot user.
// Silent block: mirror the upstream "username resolution failed" branch.
if (searchType != ChatActivity.SEARCH_MY_MESSAGES) {
    boolean blocked = false;
    if (chat == null) {
        blocked = true; // no target = global public hashtag search (the loophole)
    } else if (chat instanceof TLRPC.Chat && ChatObject.isNotInChat((TLRPC.Chat) chat)) {
        blocked = true; // non-subscribed channel / group
    } else if (chat instanceof TLRPC.User) {
        TLRPC.User u = (TLRPC.User) chat;
        if (u.bot && !u.contact) {
            blocked = true; // non-contact bot
        }
    }
    if (blocked) {
        search.loading = false;
        search.endReached = true;
        search.count = 0;
        NotificationCenter.getInstance(currentAccount).postNotificationName(
            NotificationCenter.hashtagSearchUpdated,
            guid, search.count, search.endReached, search.getMask(), search.selectedIndex, 0
        );
        return;
    }
}
```

If `org.telegram.messenger.ChatObject` is not yet imported in this file, add the import during execution.

### Site 2 — `SearchViewPager` skip the "Public posts" tab

`TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java`, around L1494–1497 inside `updateItems()`.

```java
items.add(new Item(DIALOGS_TYPE));
// CUSTOM: Block external hashtag lookup - hide "Public posts" tab
// (See HashtagSearchController.searchHashtag() for the API-layer enforcement.)
// if (expandedPublicPosts) {
//     items.add(new Item(PUBLIC_POSTS_TYPE));
// }
items.add(new Item(CHANNELS_TYPE));
```

The `expandedPublicPosts` field, the tab-title branch (around L1539), and the createView branch (around L1612) become unreachable through normal flow but stay in place — same pattern as `channelRow` in `ProfileActivity`.

### Site 3 — `DialogsSearchAdapter` skip the inline hashtag-preview API call

`TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java`, around L1327–1381. The entire `if (finalHashtag != null)` block (which constructs and sends `TL_channels_searchPosts` and populates `publicPosts`) is wrapped in a `/* */` block.

```java
// CUSTOM: Block external hashtag lookup - skip the inline hashtag-preview API call.
// publicPosts stays empty -> the "Public posts" header cell at the rendering site
// (also in this file, around L1987) is naturally hidden via its !publicPosts.isEmpty()
// guard. (See HashtagSearchController.searchHashtag() for the broader enforcement.)
/*
if (finalHashtag != null) {
    waitingResponseCount++;
    AndroidUtilities.runOnUIThread(searchHashtagRunnable = () -> {
        ... ~50 lines of TL_channels_searchPosts request and response handling ...
    }, 300);
}
*/
```

The buried `// CUSTOM: Filter out messages from non-subscribed channels` marker that lived inside this block is **removed** from the buried original (so `// CUSTOM:` count remains semantically meaningful — only active markers count). The post-hoc filtering it provided is no longer load-bearing because the API call doesn't go out.

## Marker count math

| File | Pre-feature | Post-feature | Δ |
|---|---:|---:|---:|
| `HashtagSearchController.java` | 0 | 1 | +1 |
| `Components/SearchViewPager.java` | 0 | 1 | +1 |
| `Adapters/DialogsSearchAdapter.java` | 3 | 3 (-1 buried marker dropped, +1 new gate marker) | 0 |
| **Project total** | **38** | **40** | **+2** |

## Edge cases / risks

| Risk | Mitigation |
|---|---|
| Async `#tag@username` resolution: `getUserOrChat` returns null, `getUserNameResolver().resolve()` runs async, then `searchHashtag` recurses | Gate runs in the recursive call too; the resolved-chat check applies on the second entry. The original async resolve path is untouched. |
| `ChatObject` not imported in `HashtagSearchController.java` | Verify during execution; add import if missing. |
| `searchHashtagRunnable` field becomes unused after Site 3 edit | Field stays declared. Cancellation guard `if (searchHashtagRequest >= 0)` is harmless when nothing's pending. |
| `waitingResponseCount` accounting | Original increments before runnable scheduling and decrements on response. With the block commented, neither runs — accounting balances. |
| `PUBLIC_POSTS_TYPE` branches at L1539 and L1612 in SearchViewPager | Become unreachable when no `Item(PUBLIC_POSTS_TYPE)` is added. Safe; same pattern as `channelRow` suppression. |
| `HashtagActivity` (opened from hashtag clicks in messages) | Goes through `HashtagSearchController` → blocked by Site 1 gate. UI shows empty result list. |
| Future upstream change introduces a new direct `TL_channels_searchPosts` caller | Existing post-hoc filters in `HashtagsSearchAdapter` and `PostsSearchContainer` remain as defense-in-depth. Future merge surfaces the new caller via the post-merge verification walk. |
| Bulletin missing for user-initiated paths (Q3=a) | Per user direction. UI shows empty list when block fires; consistent with how silent failures look in the rest of the app. |

## Deliverables

1. **Feature commit** on `nerovny/detox`: `Block external hashtag lookup - subscribed sources only`. Three edits across three files.
2. **CLAUDE.md follow-up commit**:
   - New subsection `### 11. External Hashtag Lookup Blocked`.
   - Renumber the existing `### 11. Additional Features` to `### 12. Additional Features`.
   - New rows in Feature → File Mapping table (extend the existing "Public Channel Discovery Removal & Hashtag/Public Posts Filtering" coverage).
   - New rows in Feature Testing Checklist.
   - Update the marker baseline in Post-Merge Verification (38 → 40).
3. **Spec doc** (this file) committed at `docs/superpowers/specs/2026-05-04-hashtag-external-lookup-blocked-design.md`.

## Verification (code-level only)

- 2 new `// CUSTOM:` markers in `HashtagSearchController.java` and `Components/SearchViewPager.java`; 1 new gate marker in `Adapters/DialogsSearchAdapter.java` replacing the dropped buried marker.
- The buried `// CUSTOM: Filter out messages from non-subscribed channels` marker that lived inside the now-commented block in `DialogsSearchAdapter` is removed.
- `HashtagSearchController.searchHashtag()` contains a gate that conditionally returns before the `int limit = 21;` line.
- `SearchViewPager.updateItems()` does not add an `Item(PUBLIC_POSTS_TYPE)` to `items` outside of a comment line.
- `DialogsSearchAdapter` has no live `TL_channels_searchPosts` instantiation — every match for `new TLRPC.TL_channels_searchPosts` in this file is inside a `/* */` block.
- Total `// CUSTOM:` count: 38 → 40 (+2 net; +3 added, −1 dropped from buried block).

User runs the build/runtime verification once after this sub-project lands.

## Files modified

- `TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java` — Site 1 (1 marker; new entry in custom-modified-files list)
- `TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java` — Site 2 (1 marker; new entry)
- `TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java` — Site 3 (gate marker added, buried marker removed; net 0)
- `CLAUDE.md` (in the follow-up commit, not the feature commit)

## Closing note

This is the third and final sub-project of the 2026-05-04 update batch. After it lands, the user will run the build/runtime test once, covering all three sub-projects together: 12.6.4 merge integrity, sensitive (18+) content lockdown, and hashtag external-lookup loophole closure.
