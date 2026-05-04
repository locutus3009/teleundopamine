# Sensitive (18+) Content Lockdown

**Date:** 2026-05-04
**Sub-project:** B of three (A = upstream 12.6.4 merge, landed in commit `e820a0117`; C = hashtag external-lookup loophole, deferred to its own brainstorm cycle).
**Branch:** `nerovny/detox` (in-place; no worktree).

## Goal

Force the "Show 18+ Content" toggle to OFF and hide it from the user. After this lands, sensitive media on this build is permanently invisible: redacted by upstream's existing spoiler effect, and tapping a redacted thumbnail produces a blocking bulletin instead of the upstream tap-to-reveal alert.

## Non-goals

- Server-side state sync. We do **not** call `TL_account.setContentSettings` to flip the server's `sensitive_enabled` flag. Local enforcement is the contract; the server flag is ignored. (User decision: don't trust the external party.)
- Removing the "18+" badge that upstream renders on redacted thumbnails. The badge stays as a "something hidden here" indicator; only the reveal action is killed.
- Removing or replacing upstream's media-spoiler/blur effect. Existing rendering is fine; only the click handler changes.
- Sub-project C work (hashtag external-lookup loophole). Stays deferred.

## Approach

Surgical overrides at five enforcement sites, all marked with `// CUSTOM:` and following the project's standard pattern of preserving the original code as a `/* */` block comment for upstream-merge resilience.

## Bulletin message

`"Sensitive (18+) content is blocked on this build"` — short, direct, mirrors existing patterns like `"Comments are blocked for this channel"`. Fired via `BulletinFactory.of(...).createErrorBulletin(...)`.

## The five enforcement sites

### Site 1 — `MessagesController.showSensitiveContent()` (around L23541)

The read API. Permanent override. Every caller (message rendering, hide/show decisions across the codebase) gets `false`.

```java
public boolean showSensitiveContent() {
    // CUSTOM: Sensitive (18+) content is permanently blocked on this build
    return false;
    /* original:
    if (contentSettings != null && System.currentTimeMillis() - contentSettingsLoadedTime < 1000 * 60 * 60) {
        return contentSettings.sensitive_enabled;
    }
    return ignoreRestrictionReasons == null || ignoreRestrictionReasons.contains("sensitive");
    */
}
```

### Site 2 — `MessagesController.setContentSettings(boolean)` (around L23517)

Defensive force-false at the entry point. The user-initiated path (the toggle) is hidden, so this is unreachable from our UI — but a one-line guard at the top costs nothing and protects against any future regression that re-introduces a caller (server push, bug, future merge).

```java
public void setContentSettings(boolean showSensitiveContent) {
    // CUSTOM: Sensitive (18+) content is permanently blocked - force false regardless of caller
    showSensitiveContent = false;
    /* ... rest of original method body unchanged ... */
}
```

### Site 3 — `MessagesController.getContentSettings(callback)` callback (around L23494–23499)

When server-side state arrives, always strip `"sensitive"` from `ignoreRestrictionReasons` regardless of what the server says. Local hygiene only — no server-side flip.

```java
if (contentSettings != null && ignoreRestrictionReasons != null) {
    // CUSTOM: Sensitive (18+) content is permanently blocked - always strip "sensitive"
    // from ignoreRestrictionReasons regardless of server state. We do NOT actively sync the
    // server-side flag; Site 1's override is sufficient to keep this client free of sensitive content.
    ignoreRestrictionReasons.remove("sensitive");
    /* original:
    if (contentSettings.sensitive_enabled) ignoreRestrictionReasons.add("sensitive");
    else ignoreRestrictionReasons.remove("sensitive");
    */
    if (mainPreferences != null) {
        mainPreferences.edit().putStringSet("ignoreRestrictionReasons", ignoreRestrictionReasons).apply();
    }
}
```

### Site 4 — `ThemeActivity.updateRowsIds()` row suppression (around L702–703)

Comment out the row-allocation block. `sensitiveContentRow` stays `-1` because of the existing `sensitiveContentRow = -1;` initializer at L586. All downstream `>= 0` guards (L925, 964, 1495, 1500, 2612, 2749) become natural no-ops; no further edits in this file.

```java
// CUSTOM: Hide "Show 18+ Content" toggle - sensitive content is permanently blocked
// (See MessagesController.showSensitiveContent() for the underlying enforcement.)
// if (contentSettings != null && contentSettings.sensitive_can_change) {
//     sensitiveContentRow = rowCount++;
// }
```

### Site 5 — Per-message reveal alert (`ChatActivity` around L40847, `SharedMediaLayout` around L3175)

The per-message tap-through. Both files build the same `MessageShowSensitiveContent*` alert. Both alert-builder blocks get replaced with a blocking bulletin and an early return.

```java
// CUSTOM: Sensitive (18+) content is permanently blocked - close per-message reveal escape hatch
BulletinFactory.of(this).createErrorBulletin("Sensitive (18+) content is blocked on this build").show();
return;
/* original alert-builder code preserved as comment */
```

This neuters both the per-message tap-through *and* the per-dialog `sensitiveAgreed` opt-in (the latter is only reachable via the same alert's "always show for this dialog" checkbox).

## Edge cases / risks

| Risk | Mitigation |
|---|---|
| `highlightSensitiveRow()` (`ThemeActivity:262`) is called externally and tries to scroll to row `-1` | Existing `>= 0` guards make it a no-op. Not editing speculatively. |
| `ConfirmSensitiveContent*` confirmation dialog | Reachable only via the row click — row is hidden, so unreachable. No edit needed. |
| `SensitiveContentSettingsToast` toast | Fires only from the reveal alert's "always show for this dialog" branch — alert is replaced, so unreachable. No edit needed. |
| `sensitive_can_change == false` from server | Pre-existing branch already kept the row off in this case. No behavior change, just always-off now. |
| Upstream adds a third reveal site in a future release | Future merges' Post-Merge Verification walk catches it via the unique `MessageShowSensitiveContentMediaTitle` string. CLAUDE.md gets a search-pattern hint. |
| Future merge re-anchors fail because upstream restructures the alert builder | Same M2 verify-during-resolve workflow as for prior CUSTOM blocks; bulletin string is unique enough to grep. |

## Deliverables

1. **Feature commit** on `nerovny/detox`: `Block sensitive (18+) content - hide toggle and force off`. Five edits across three files (`MessagesController.java`, `ThemeActivity.java`, `ChatActivity.java`, `SharedMediaLayout.java`).
2. **CLAUDE.md follow-up commit** (kept separate so the feature commit is pure):
   - New feature subsection `### N. Sensitive (18+) Content Lockdown` (consistent with existing numbered subsections).
   - New rows in Feature → File Mapping table.
   - New rows in Feature Testing Checklist.
   - Update marker-count expectation in Post-Merge Verification subsection (32 → 38; 6 new markers across 4 files, since Site 5 has two locations).
   - Add a one-line search-pattern hint for future merges (`MessageShowSensitiveContentMediaTitle`).
3. **Spec doc** (this file) committed at `docs/superpowers/specs/2026-05-04-sensitive-content-lockdown-design.md`.

## Verification (code-level only)

Per the project standard:
- All 5 new `// CUSTOM:` markers present in the three modified files.
- `MessagesController.showSensitiveContent()` first non-comment statement is `return false;`.
- `MessagesController.setContentSettings(boolean)` first non-comment statement is `showSensitiveContent = false;`.
- `ignoreRestrictionReasons.remove("sensitive")` is unconditional in the `getContentSettings` callback (no `add("sensitive")` reachable).
- `ThemeActivity.updateRowsIds()` does not contain a non-commented assignment of `sensitiveContentRow = rowCount++`.
- `BulletinFactory.of(...).createErrorBulletin("Sensitive (18+) content is blocked on this build")` appears in both `ChatActivity.java` and `SharedMediaLayout.java`.
- Total `// CUSTOM:` count: 32 → 38 (6 new markers across 4 files).

User runs the build/runtime verification once after sub-project C also lands.

## Files modified

- `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` — Sites 1, 2, 3 (3 markers)
- `TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java` — Site 4 (1 marker)
- `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java` — Site 5a (1 marker)
- `TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java` — Site 5b (1 marker; this file becomes a new entry in the custom-modified-files list)
- `CLAUDE.md` (in the follow-up commit, not the feature commit)

## Next step after this sub-project

Brainstorm sub-project C (hashtag external-lookup loophole). The existing hashtag filtering in `HashtagsSearchAdapter.java` and `PostsSearchContainer.java` filters results post-hoc — sub-project C tightens this so external lookups are not initiated at all (or the "Public posts" tab is suppressed entirely). Today's session's user feedback: existing post-hoc filters allow leakage during the brief moment between query and filter; the loophole is the API call itself.
