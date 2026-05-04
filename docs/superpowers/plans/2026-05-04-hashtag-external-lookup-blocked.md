# Hashtag External Lookup Blocked Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the hashtag external-lookup loophole. After this lands, hashtag search and discovery are restricted to `SEARCH_MY_MESSAGES` and searches scoped to a subscribed channel/group or contact/non-bot user. All external lookups (`TL_channels_searchPosts` with no target, plus chat-scoped `TL_messages_search` against non-subscribed chats) are silently blocked at the API layer; the "Public posts" UI tab is hidden.

**Architecture:** Three surgical edits across three files. (1) A single allow-rule gate at `HashtagSearchController.searchHashtag()` covers every entry point that flows through the controller. (2) `SearchViewPager.updateItems()` no longer adds the `Item(PUBLIC_POSTS_TYPE)` tab. (3) `DialogsSearchAdapter`'s separate inline `TL_channels_searchPosts` request is commented out; the buried inner `// CUSTOM:` marker that lived inside that block is dropped (no longer load-bearing).

**Tech Stack:** Java edits to existing Telegram Android source. No new dependencies. `ChatObject` and `NotificationCenter` are in the same package as `HashtagSearchController` — no imports to add.

**Spec:** `docs/superpowers/specs/2026-05-04-hashtag-external-lookup-blocked-design.md`

**Marker count target:** 38 (current) → 40 (post-feature). +2 net (+3 added, −1 dropped from the buried block).
- `HashtagSearchController.java`: 0 → 1 (new entry in custom-modified-files list)
- `Components/SearchViewPager.java`: 0 → 1 (new entry)
- `Adapters/DialogsSearchAdapter.java`: 3 → 3 (drop 1 buried marker, add 1 new gate marker)

---

### Task 1: Pre-flight check

**Files:** none modified.

- [ ] **Step 1: Confirm clean working tree on `nerovny/detox` post-B**

```bash
git status
git rev-parse --abbrev-ref HEAD
git log -1 --format='%h %s'
```

Expected:
- `nothing to commit, working tree clean`
- branch `nerovny/detox`
- last commit subject relates to sub-project B or the spec for C

If the working tree isn't clean, stop and resolve before starting feature work.

- [ ] **Step 2: Confirm pre-feature CUSTOM marker count is 38**

```bash
grep -rc "// CUSTOM:" TMessagesProj/src/ --include="*.java" | awk -F: '{s+=$2} END{print s}'
```

Expected: `38`. If different, the working tree has drifted from what this plan assumes — investigate before proceeding.

- [ ] **Step 3: Confirm none of the new markers exist yet**

```bash
for s in 'CUSTOM: Block external hashtag lookup'; do
  n=$(grep -rE --include="*.java" -c -- "$s" TMessagesProj/src/ | awk -F: '{s+=$2} END{print s+0}')
  echo "  [$n]  $s"
done
```

Expected: count is `0`. If non-zero, feature work is partially done — re-ground the plan.

- [ ] **Step 4: Confirm the buried marker exists in DialogsSearchAdapter (it must, so we can drop it cleanly)**

```bash
grep -n "// CUSTOM: Filter out messages from non-subscribed channels" TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java
```

Expected: a single line — the marker around L1363. If zero lines, DialogsSearchAdapter has drifted from the plan's assumption (the buried marker was already dropped or relocated). Stop and re-ground.

---

### Task 2: Site 1 — `HashtagSearchController.searchHashtag()` controller-layer gate

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java` (insert around L194–195, after the username-resolution block ends and before `int limit = 21;`)

- [ ] **Step 1: Confirm the insertion point**

```bash
sed -n '190,200p' TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java
```

Expected: shows the closing `}` of the `if (!TextUtils.isEmpty(username))` block followed by an empty line, then `int limit = 21;` and `TLObject request;`. Note the line numbers — they may have shifted from the spec's L194 estimate.

- [ ] **Step 2: Confirm `ChatObject` and `NotificationCenter` are usable without new imports**

```bash
echo "Package check:"; grep "^package" TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java
echo "ChatObject package:"; grep "^package" TMessagesProj/src/main/java/org/telegram/messenger/ChatObject.java
echo "NotificationCenter package:"; grep "^package" TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java
echo ""
echo "NotificationCenter already used in this file:"
grep -c "NotificationCenter\." TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java
```

Expected: all three classes are in `org.telegram.messenger` (same-package). NotificationCenter usage count ≥ 3 (the file already uses it without an import). Confirms no imports need adding.

- [ ] **Step 3: Insert the gate**

Find this passage in the file (the closing of the username-resolution block):

```java
                    searchHashtag(query, guid, searchType, loadIndex);
                });
                return;
            }
        }

        int limit = 21;
        TLObject request;
```

Replace with:

```java
                    searchHashtag(query, guid, searchType, loadIndex);
                });
                return;
            }
        }

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

        int limit = 21;
        TLObject request;
```

- [ ] **Step 4: Verify the edit**

```bash
grep -nA 26 "// CUSTOM: Block external hashtag lookup" TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java
echo "---"
echo "Marker count:"
grep -c "// CUSTOM:" TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java
```

Expected:
- Output shows the full gate block.
- Marker count is `1` (was `0`, +1).

- [ ] **Step 5: Sanity-check the gate is in the right structural location**

```bash
echo "Gate must come AFTER the username-resolution callback and BEFORE 'int limit = 21;':"
grep -nE "^        int limit = 21;$|// CUSTOM: Block external hashtag lookup|searchHashtag\(query, guid, searchType, loadIndex\);" TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java
```

Expected: the recursive `searchHashtag(query, guid, searchType, loadIndex);` line appears before the `// CUSTOM:` line, which appears before the `int limit = 21;` line. Order matters — gate must run after the async resolve so `chat` is populated for the recursive call.

---

### Task 3: Site 2 — `SearchViewPager` skip the "Public posts" tab

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java` around L1494–1497

- [ ] **Step 1: Confirm the insertion point**

```bash
sed -n '1490,1502p' TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java
```

Expected: shows the `updateItems()` method body with `items.add(new Item(DIALOGS_TYPE));`, the `if (expandedPublicPosts) { items.add(new Item(PUBLIC_POSTS_TYPE)); }` block, and `items.add(new Item(CHANNELS_TYPE));`.

- [ ] **Step 2: Comment out the "Public posts" tab block**

Find this passage:

```java
            items.add(new Item(DIALOGS_TYPE));
            if (expandedPublicPosts) {
                items.add(new Item(PUBLIC_POSTS_TYPE));
            }
            items.add(new Item(CHANNELS_TYPE));
```

Replace with:

```java
            items.add(new Item(DIALOGS_TYPE));
            // CUSTOM: Block external hashtag lookup - hide "Public posts" tab
            // (See HashtagSearchController.searchHashtag() for the API-layer enforcement.)
            // if (expandedPublicPosts) {
            //     items.add(new Item(PUBLIC_POSTS_TYPE));
            // }
            items.add(new Item(CHANNELS_TYPE));
```

- [ ] **Step 3: Verify the edit**

```bash
grep -nA 6 "// CUSTOM: Block external hashtag lookup" TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java
echo "---"
echo "Marker count:"
grep -c "// CUSTOM:" TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java
echo "---"
echo "Live (non-comment) PUBLIC_POSTS_TYPE 'items.add' must be ZERO:"
grep -nE "^[[:space:]]+items\.add\(new Item\(PUBLIC_POSTS_TYPE\)" TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java
```

Expected:
- Output shows the new `// CUSTOM:` block followed by the commented-out `if (expandedPublicPosts)` lines.
- Marker count is `1` (was `0`, +1).
- The "Live ..." check returns no output — the only `items.add(new Item(PUBLIC_POSTS_TYPE))` line is now commented out.

---

### Task 4: Site 3 — `DialogsSearchAdapter` skip the inline hashtag-preview API call (drops buried marker, adds new gate marker)

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java` around L1327–1381

This task does two things in one edit: drops the now-unreachable buried `// CUSTOM: Filter out messages from non-subscribed channels` marker (which lives inside the block we're commenting out), and adds the new `// CUSTOM: Block external hashtag lookup` marker above the commented-out block.

- [ ] **Step 1: Confirm the block boundaries**

```bash
sed -n '1325,1382p' TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java
```

Expected: shows `final String finalHashtag = hashtag;` followed by the entire `if (finalHashtag != null) { ... }` block ending with `}, 300);` and a closing `}`.

- [ ] **Step 2: Replace the block with a commented-out version (without the buried marker)**

Find this passage in the file:

```java
            final String finalHashtag = hashtag;

            if (finalHashtag != null) {
                waitingResponseCount++;
                AndroidUtilities.runOnUIThread(searchHashtagRunnable = () -> {
                    searchHashtagRunnable = null;
                    if (searchId != lastSearchId) {
                        return;
                    }
                    if (searchHashtagRequest >= 0) {
                        ConnectionsManager.getInstance(currentAccount).cancelRequest(searchHashtagRequest, true);
                    }
                    TLRPC.TL_channels_searchPosts req = new TLRPC.TL_channels_searchPosts();
                    req.flags |= 1;
                    req.hashtag = finalHashtag;
                    req.limit = 3;
                    req.offset_peer = new TLRPC.TL_inputPeerEmpty();
                    searchHashtagRequest = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (searchId != lastSearchId) {
                            return;
                        }
                        if (res instanceof TLRPC.messages_Messages) {
                            TLRPC.messages_Messages msgs = (TLRPC.messages_Messages) res;
                            int totalCount = 0;
                            if (msgs instanceof TLRPC.TL_messages_messages) {
                                totalCount = ((TLRPC.TL_messages_messages) msgs).messages.size();
                            } else if (msgs instanceof TLRPC.TL_messages_messagesSlice) {
                                totalCount = ((TLRPC.TL_messages_messagesSlice) msgs).count;
                            }
                            publicPostsTotalCount = totalCount;
                            publicPostsLastRate = msgs.next_rate;
                            publicPostsHashtag = finalHashtag;
                            MessagesController controller = MessagesController.getInstance(currentAccount);
                            controller.putUsers(msgs.users, false);
                            controller.putChats(msgs.chats, false);
                            for (int i = 0; i < msgs.messages.size(); ++i) {
                                TLRPC.Message msg = msgs.messages.get(i);

                                // CUSTOM: Filter out messages from non-subscribed channels
                                long dialogId = MessageObject.getDialogId(msg);
                                if (DialogObject.isChatDialog(dialogId)) {
                                    TLRPC.Chat chat = controller.getChat(-dialogId);
                                    if (chat != null && ChatObject.isNotInChat(chat)) {
                                        continue; // Skip messages from non-subscribed channels
                                    }
                                }

                                publicPosts.add(new MessageObject(currentAccount, msg, false, true));
                            }
                            if (delegate != null) {
                                delegate.searchStateChanged(waitingResponseCount > 0, true);
                            }
                            notifyDataSetChanged();
                        }
                    }));
                }, 300);
            }
```

Replace with:

```java
            final String finalHashtag = hashtag;

            // CUSTOM: Block external hashtag lookup - skip the inline hashtag-preview API call.
            // publicPosts stays empty -> the "Public posts" header cell at the rendering site
            // (also in this file, around L1987) is naturally hidden via its !publicPosts.isEmpty()
            // guard. (See HashtagSearchController.searchHashtag() for the broader enforcement.)
            // The buried "// CUSTOM: Filter out messages from non-subscribed channels" marker that
            // lived inside the original block is intentionally dropped: the post-hoc filter is no
            // longer load-bearing because the API call doesn't go out at all.
            /*
            if (finalHashtag != null) {
                waitingResponseCount++;
                AndroidUtilities.runOnUIThread(searchHashtagRunnable = () -> {
                    searchHashtagRunnable = null;
                    if (searchId != lastSearchId) {
                        return;
                    }
                    if (searchHashtagRequest >= 0) {
                        ConnectionsManager.getInstance(currentAccount).cancelRequest(searchHashtagRequest, true);
                    }
                    TLRPC.TL_channels_searchPosts req = new TLRPC.TL_channels_searchPosts();
                    req.flags |= 1;
                    req.hashtag = finalHashtag;
                    req.limit = 3;
                    req.offset_peer = new TLRPC.TL_inputPeerEmpty();
                    searchHashtagRequest = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (searchId != lastSearchId) {
                            return;
                        }
                        if (res instanceof TLRPC.messages_Messages) {
                            TLRPC.messages_Messages msgs = (TLRPC.messages_Messages) res;
                            int totalCount = 0;
                            if (msgs instanceof TLRPC.TL_messages_messages) {
                                totalCount = ((TLRPC.TL_messages_messages) msgs).messages.size();
                            } else if (msgs instanceof TLRPC.TL_messages_messagesSlice) {
                                totalCount = ((TLRPC.TL_messages_messagesSlice) msgs).count;
                            }
                            publicPostsTotalCount = totalCount;
                            publicPostsLastRate = msgs.next_rate;
                            publicPostsHashtag = finalHashtag;
                            MessagesController controller = MessagesController.getInstance(currentAccount);
                            controller.putUsers(msgs.users, false);
                            controller.putChats(msgs.chats, false);
                            for (int i = 0; i < msgs.messages.size(); ++i) {
                                TLRPC.Message msg = msgs.messages.get(i);

                                long dialogId = MessageObject.getDialogId(msg);
                                if (DialogObject.isChatDialog(dialogId)) {
                                    TLRPC.Chat chat = controller.getChat(-dialogId);
                                    if (chat != null && ChatObject.isNotInChat(chat)) {
                                        continue; // Skip messages from non-subscribed channels
                                    }
                                }

                                publicPosts.add(new MessageObject(currentAccount, msg, false, true));
                            }
                            if (delegate != null) {
                                delegate.searchStateChanged(waitingResponseCount > 0, true);
                            }
                            notifyDataSetChanged();
                        }
                    }));
                }, 300);
            }
            */
```

Note that the buried `// CUSTOM: Filter out messages from non-subscribed channels` line is **omitted** from the commented-out version (intentionally dropped per the spec).

- [ ] **Step 3: Verify the edit**

```bash
grep -nA 6 "// CUSTOM: Block external hashtag lookup" TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java
echo "---"
echo "Marker count (must be 3, unchanged):"
grep -c "// CUSTOM:" TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java
echo "---"
echo "Buried marker must be GONE (zero matches):"
grep -c "// CUSTOM: Filter out messages from non-subscribed channels" TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java
echo "---"
echo "Live TL_channels_searchPosts must be ZERO:"
grep -nE "TL_channels_searchPosts" TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java
```

Expected:
- New gate marker present.
- Total marker count is `3` (was `3`; −1 buried marker dropped, +1 new gate marker = net 0).
- Buried marker count is `0`.
- The `TL_channels_searchPosts` reference still appears in the grep (one line), but it's inside the `/* */` block. **Visually verify** the surrounding context — the line must be between the `/*` opener and the matching `*/` closer. (If it's outside the comment, the edit is wrong.)

- [ ] **Step 4: Sanity-check the file compiles structurally**

```bash
echo "Brace balance check (informational; counts braces in the block):"
sed -n '1325,1395p' TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java | grep -oE '[{}]' | sort | uniq -c
```

This is just informational — the file is too large to check overall balance via shell. Visually confirm that the `/*` opener and `*/` closer in the new edit are properly paired and that no other Java statement was accidentally captured.

---

### Task 5: Code-level verification of all three sites

**Files:** none modified.

- [ ] **Step 1: Total CUSTOM marker count**

```bash
total=$(grep -rc "// CUSTOM:" TMessagesProj/src/ --include="*.java" | awk -F: '{s+=$2} END{print s}')
echo "Post-feature CUSTOM marker count: $total (target: 40)"
```

Expected: `40`.

- [ ] **Step 2: Per-file marker counts**

```bash
for f in \
  "TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java:1" \
  "TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java:1" \
  "TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java:3" \
; do
  file="${f%:*}"
  target="${f##*:}"
  c=$(grep -c "// CUSTOM:" "$file")
  flag=""; [ "$c" -ne "$target" ] && flag=" *** EXPECTED $target ***"
  echo "  $c  $file$flag"
done
```

Expected: `1, 1, 3`.

- [ ] **Step 3: New marker present at all three sites**

```bash
n=$(grep -rE --include="*.java" -c -- "// CUSTOM: Block external hashtag lookup" TMessagesProj/src/ | awk -F: '{s+=$2} END{print s}')
echo "New 'Block external hashtag lookup' marker total: $n (target: 3)"
```

Expected: `3`.

- [ ] **Step 4: Buried marker is gone**

```bash
grep -rE --include="*.java" -- "// CUSTOM: Filter out messages from non-subscribed channels" TMessagesProj/src/
```

Expected: matches in `HashtagsSearchAdapter.java` and `PostsSearchContainer.java` (both still have their post-hoc filter markers — those stay), but **NOT** in `DialogsSearchAdapter.java`. Specifically:

```bash
grep -c "// CUSTOM: Filter out messages from non-subscribed channels" TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java
```

Expected: `0`.

- [ ] **Step 5: Live API-call references are zero in `DialogsSearchAdapter`, gated in `HashtagSearchController`**

```bash
echo "DialogsSearchAdapter: any non-comment-block 'TL_channels_searchPosts' is a bug"
grep -nE "TL_channels_searchPosts" TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java
echo "(if any matches above, visually verify each is inside a /* ... */ block)"

echo ""
echo "HashtagSearchController: TL_channels_searchPosts is still live (this is the request branch the gate protects)"
grep -nE "TL_channels_searchPosts" TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java
```

Expected:
- `DialogsSearchAdapter`: one match line, but it must be inside the `/* */` block introduced in Task 4. Visually inspect surrounding context if uncertain.
- `HashtagSearchController`: one match — the `TLRPC.TL_channels_searchPosts req = new ...` instantiation. This stays live; the gate (Site 1) prevents it from being reached for non-allowed cases.

- [ ] **Step 6: SearchViewPager `Item(PUBLIC_POSTS_TYPE)` is only inside a comment**

```bash
echo "Live (non-comment) items.add(new Item(PUBLIC_POSTS_TYPE)):"
grep -nE "^[[:space:]]+items\.add\(new Item\(PUBLIC_POSTS_TYPE\)" TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java
echo "(zero lines = correct)"
echo ""
echo "Commented (with leading //) items.add(new Item(PUBLIC_POSTS_TYPE)):"
grep -nE "//\s+items\.add\(new Item\(PUBLIC_POSTS_TYPE\)" TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java
echo "(should show one line)"
```

Expected: zero live, one commented.

- [ ] **Step 7: Modified files set is exactly three**

```bash
git diff --name-only
```

Expected exactly:
```
TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java
TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java
TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java
```

If any other file is dirty, investigate before staging.

- [ ] **Step 8: Other custom features remain intact (regression spot-check)**

```bash
total=$(grep -rc "// CUSTOM:" TMessagesProj/src/ --include="*.java" | awk -F: '{s+=$2} END{print s}')
echo "Total markers: $total (must be 40)"
echo ""
echo "Spot-check existing customizations are still in place:"
echo "  CHECK_UPDATES = false:    $(grep -c 'CHECK_UPDATES = false' TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java)"
echo "  showSensitiveContent return false: $(grep -A 2 'public boolean showSensitiveContent' TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java | grep -c 'return false;')"
echo "  isChatBlocked defined:    $(grep -c 'public static boolean isChatBlocked' TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java)"
echo "  isCommentBlocked defined: $(grep -c 'public static boolean isCommentBlocked' TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java)"
```

Expected:
- Total markers: `40`.
- All existing-feature counters: `1`.

---

### Task 6: Stage and commit the feature

**Files:** none further modified.

- [ ] **Step 1: Stage the three modified files**

```bash
git add \
  TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java \
  TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java \
  TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java
git status
```

Expected: only those three files in "Changes to be committed".

- [ ] **Step 2: Pre-commit sanity diff**

```bash
git diff --cached --stat
```

Expected: 3 files changed; insertions clearly positive (we add the gate and comment-out wrappers).

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
Block external hashtag lookup - subscribed sources only

Three surgical edits across three files:

- HashtagSearchController.searchHashtag() gains a single allow-rule
  gate at the controller layer: allow only SEARCH_MY_MESSAGES or
  searches scoped to a subscribed chat / contact / non-bot user.
  Silent block on refusal (mirrors the upstream "username
  resolution failed" empty-result branch).
- SearchViewPager.updateItems() no longer adds the
  Item(PUBLIC_POSTS_TYPE) tab. The PUBLIC_POSTS_TYPE constant,
  expandedPublicPosts field, and the unreachable tab-title /
  createView branches stay in place (same pattern as channelRow
  suppression).
- DialogsSearchAdapter's separate inline TL_channels_searchPosts
  request (the "Public posts" preview in the in-search "Chats" tab)
  is commented out. publicPosts stays empty, so the "Public posts"
  header cell at the rendering site is naturally hidden via its
  !publicPosts.isEmpty() guard. The buried inner
  "// CUSTOM: Filter out messages from non-subscribed channels"
  marker is dropped (no longer load-bearing because the API call
  doesn't go out).

Existing post-hoc filters in HashtagsSearchAdapter and
PostsSearchContainer remain as defense-in-depth.

Marker count: 38 -> 40 (+3 added, -1 dropped from the buried
block; net +2). HashtagSearchController and SearchViewPager are
new entries in the custom-modified-files list.

See docs/superpowers/specs/2026-05-04-hashtag-external-lookup-blocked-design.md
for the full design.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git log -1 --format='%h %s'
```

Expected: commit lands on `nerovny/detox` with subject `Block external hashtag lookup - subscribed sources only`.

---

### Task 7: CLAUDE.md follow-up commit

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Locate anchors**

```bash
grep -n "^### [0-9]\+\.\|^\*\*Modified files:\|Post-Merge Verification" CLAUDE.md | head -20
```

Note line numbers for: the numbered subsections, the Modified files list, and the Post-Merge Verification subsection (with the marker baseline).

- [ ] **Step 2: Add new feature subsection 11 (renumber Additional Features to 12)**

In CLAUDE.md, find the line `### 11. Additional Features` and insert a new subsection **before** it. Then renumber the existing subsection from 11 to 12.

The new subsection text:

```markdown
### 11. External Hashtag Lookup Blocked

Hashtag search and discovery are restricted to subscribed sources only. After this lands, every hashtag entry point — clicking a hashtag in a message, the "Public posts" tab in the search UI, the inline hashtag preview in the search "Chats" tab, deep links — is gated by a single allow-rule at the API layer. External lookups against the public web (`TL_channels_searchPosts` with no target) are silently blocked. Searches scoped to a non-subscribed chat are also blocked. Searches across the user's own messages and within subscribed channels work normally.

**Implementation**:

`TMessagesProj/src/main/java/org/telegram/messenger/HashtagSearchController.java`:
- `searchHashtag(String, int, int, int)` — single gate after username resolution. Allow rule: `searchType == SEARCH_MY_MESSAGES`, OR target is a subscribed `TLRPC.Chat`, OR target is a non-bot or contact `TLRPC.User`. Otherwise: silent block — set count=0, endReached=true, post `hashtagSearchUpdated`, return.

`TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java`:
- `updateItems()` — the `if (expandedPublicPosts) { items.add(new Item(PUBLIC_POSTS_TYPE)); }` block is commented out. The `expandedPublicPosts` field, the `PUBLIC_POSTS_TYPE` constant, and the unreachable tab-title / createView branches stay in place (same pattern as `channelRow` in `ProfileActivity`).

`TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java`:
- The inline hashtag-preview `TL_channels_searchPosts` request block (separate from `HashtagSearchController`) is commented out. `publicPosts` stays empty → the "Public posts" header cell at the rendering site (also in this file) is naturally hidden via its existing `!publicPosts.isEmpty()` guard. The previously-buried `// CUSTOM: Filter out messages from non-subscribed channels` post-hoc filter inside that block is dropped (no longer load-bearing).

**What stays untouched (defense-in-depth)**:
- The post-hoc filters in `HashtagsSearchAdapter.java` and `PostsSearchContainer.java`. They remain as a second line of defense against any future code path that bypasses the controller.

**Future-merge stability**: the unique strings `// CUSTOM: Block external hashtag lookup`, `Item(PUBLIC_POSTS_TYPE)`, and `TL_channels_searchPosts` are search anchors. If a future upstream release introduces a new direct caller of `TL_channels_searchPosts`, the post-merge verification walk surfaces it.
```

Then change `### 11. Additional Features` to `### 12. Additional Features`.

- [ ] **Step 3: Add Feature → File Mapping rows**

Find the existing Feature → File Mapping table and add a new sub-table immediately after the "Sensitive (18+) Content Lockdown" sub-table:

```markdown
### External Hashtag Lookup Blocked
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Controller-layer gate | `HashtagSearchController.java` | `searchHashtag()` allow-rule |
| "Public posts" tab hidden | `Components/SearchViewPager.java` | `updateItems()` `Item(PUBLIC_POSTS_TYPE)` commented out |
| Inline preview API call blocked | `Adapters/DialogsSearchAdapter.java` | `if (finalHashtag != null) { ... TL_channels_searchPosts ... }` commented out |
```

- [ ] **Step 4: Add Feature Testing Checklist row group**

Find the Feature Testing Checklist section and add immediately after the "Sensitive (18+) Content Lockdown" row group:

```markdown
### External Hashtag Lookup Blocked
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| "Public posts" tab hidden | 1. Open the global search 2. Look at the tab list | The "Public posts" tab does not appear |
| Inline hashtag preview hidden | 1. In global search, type a hashtag like `#news` 2. Look at the "Chats" tab results | No "Public posts" header cell appears (no inline preview of public posts) |
| Hashtag click in subscribed channel | 1. Inside a subscribed channel, tap a `#hashtag` link in a message | Search opens; results limited to messages within the same subscribed channel |
| Hashtag click in non-subscribed channel preview | 1. Open a non-subscribed channel preview 2. Tap a `#hashtag` link in a visible message | Search opens; result list is empty (silent block) |
| `#tag@subscribed_channel` query | 1. Type `#news@somechannel` where the channel is subscribed | Search returns messages from that channel |
| `#tag@non_subscribed` query | 1. Type `#news@somechannel` where the channel is NOT subscribed | Search returns empty results (silent block) |
```

- [ ] **Step 5: Update the marker-count baseline in the Post-Merge Verification subsection**

In the Post-Merge Verification subsection, find the existing marker-baseline line (set during sub-project B). The current text reads:

```markdown
5. **Confirm the marker baseline.** The total `// CUSTOM:` marker count is currently **38 across 11 files** ...
```

Note: the existing "11 files" was actually an off-by-one from sub-project B (the correct count after B was 12 files; the line was set incorrectly when sub-project B landed). Change `38 across 11 files` to `40 across 14 files`.

The correct file inventory after sub-project C is:

```
1. BuildVars.java
2. DialogsSearchAdapter.java
3. ChatActivity.java
4. DialogsChannelsAdapter.java
5. SearchAdapterHelper.java
6. PostsSearchContainer.java
7. HashtagsSearchAdapter.java
8. ProfileActivity.java
9. LaunchActivity.java
10. MessagesController.java
11. ThemeActivity.java
12. Components/SharedMediaLayout.java
13. HashtagSearchController.java          <- NEW (sub-project C)
14. Components/SearchViewPager.java       <- NEW (sub-project C)
```

Marker total: 4+3+8+2+2+1+1+3+2+10+1+1+1+1 = **40**.

- [ ] **Step 6: Update the "Files with Custom Modifications" list**

Find the bulleted "Modified files:" list and append two new entries plus update one existing entry:

Add new lines:
```markdown
- `HashtagSearchController.java` - Controller-layer gate blocking external hashtag lookup
- `Components/SearchViewPager.java` - "Public posts" tab hidden in global search
```

Update the existing `DialogsSearchAdapter.java` line to append new behavior. The current line is:
```markdown
- `DialogsSearchAdapter.java` - Global search filtering, hashtag search filtering, blocked chat filtering
```

Change it to:
```markdown
- `DialogsSearchAdapter.java` - Global search filtering, hashtag search filtering, blocked chat filtering, inline public-posts hashtag preview API call blocked
```

- [ ] **Step 7: Verify all CLAUDE.md edits**

```bash
grep -n "^### [0-9]\+\." CLAUDE.md
echo "---"
grep -n "External Hashtag Lookup Blocked" CLAUDE.md
echo "---"
grep -n "40 across 14 files" CLAUDE.md
echo "---"
grep -n "HashtagSearchController\|Components/SearchViewPager" CLAUDE.md
```

Expected:
- Numbered subsections include `### 11. External Hashtag Lookup Blocked` and `### 12. Additional Features`.
- "External Hashtag Lookup Blocked" appears at least three times (subsection heading, Feature Mapping table heading, Testing Checklist heading).
- The marker baseline shows `40 across 14 files`.
- `HashtagSearchController` appears in the Feature Mapping table and Modified files list. `Components/SearchViewPager` appears in both as well.

- [ ] **Step 8: Stage and commit the CLAUDE.md follow-up**

```bash
git add CLAUDE.md
git diff --cached --stat CLAUDE.md
git commit -m "$(cat <<'EOF'
Document external hashtag lookup block in CLAUDE.md

- New subsection "11. External Hashtag Lookup Blocked" describing
  the three enforcement sites (controller-layer gate, hidden
  "Public posts" tab, inline preview API call commented out) and
  the silent-block behavior.
- Renumber the prior "Additional Features" subsection to 12.
- New Feature -> File Mapping table block.
- New Feature Testing Checklist block (covers tab hidden, inline
  preview hidden, hashtag clicks in subscribed/non-subscribed
  contexts, and #tag@username variants).
- Update marker-count baseline in the Post-Merge Verification
  subsection (40 markers across 13 files).
- Add HashtagSearchController.java and Components/SearchViewPager.java
  to the Modified files list, append new behavior note to the
  DialogsSearchAdapter.java entry.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git log -2 --format='%h %s'
```

Expected: two new commits visible — the CLAUDE.md follow-up on top, the feature commit beneath.

---

## Final state

- `nerovny/detox` advanced by two commits: the feature commit and the CLAUDE.md follow-up.
- Total `// CUSTOM:` markers: 40 across 14 files.
- The 4-task brief is now complete: 12.6.4 merge integrity, sensitive content lockdown, and hashtag external-lookup loophole are all on the branch.
- User runs the build/runtime test once, covering all three sub-projects together.
- After the user confirms the build works, the executing-plans skill's normal terminal step (`finishing-a-development-branch`) becomes appropriate — at that point we can decide what to do with the uncommitted/unpushed state of `nerovny/detox`.
