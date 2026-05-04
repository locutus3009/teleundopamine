# Sensitive (18+) Content Lockdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Force the "Show 18+ Content" toggle to OFF and hide it from the user. Sensitive media stays redacted by upstream's spoiler effect; tapping a redacted thumbnail produces a blocking bulletin instead of the upstream tap-to-reveal alert. Local enforcement only — no server-side sync.

**Architecture:** Surgical overrides at five enforcement sites across four files, marked with `// CUSTOM:` and following the project's standard pattern of preserving original code as `/* */` blocks for upstream-merge resilience. One feature commit + one CLAUDE.md follow-up commit on `nerovny/detox`.

**Tech Stack:** Java edits to existing Telegram Android source; no new dependencies. Verification is grep-based; user runs the build/runtime test once after sub-project C also lands.

**Spec:** `docs/superpowers/specs/2026-05-04-sensitive-content-lockdown-design.md`

**Bulletin message (used at three sites):** `"Sensitive (18+) content is blocked on this build"`

**Marker count target:** 32 (current) → 38 (post-feature). 6 new `// CUSTOM:` markers across:
- `MessagesController.java`: 3 markers (Sites 1, 2, 3)
- `ThemeActivity.java`: 1 marker (Site 4)
- `ChatActivity.java`: 1 marker (Site 5a)
- `Components/SharedMediaLayout.java`: 1 marker (Site 5b — new entry in the custom-modified-files list)

---

### Task 1: Pre-flight check

**Files:** none modified.

- [ ] **Step 1: Confirm clean working tree on `nerovny/detox` post-merge**

```bash
git status
git rev-parse --abbrev-ref HEAD
git log -1 --format='%h %s'
```

Expected:
- `nothing to commit, working tree clean`
- branch `nerovny/detox`
- last commit subject mentions either the spec for this sub-project (`27f331fee`) or a later commit on top of the merge

If working tree is not clean, stop and resolve (a stash or commit) before starting feature work.

- [ ] **Step 2: Confirm pre-feature CUSTOM marker count is 32**

```bash
grep -rc "// CUSTOM:" TMessagesProj/src/ --include="*.java" | awk -F: '{s+=$2} END{print s}'
```

Expected: `32`. If different, the working tree has drifted from what this plan assumes — investigate before proceeding.

- [ ] **Step 3: Confirm none of the target methods have already been modified**

```bash
for s in 'CUSTOM: Sensitive (18+)' 'CUSTOM: Hide "Show 18+ Content"'; do
  n=$(grep -rE --include="*.java" -c -- "$s" TMessagesProj/src/ | awk -F: '{s+=$2} END{print s+0}')
  echo "  [$n]  $s"
done
```

Expected: every count is `0`. If non-zero, stop — feature work is partially done and the plan needs to be re-grounded.

---

### Task 2: Site 1 — `MessagesController.showSensitiveContent()` permanent override

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` around L23541

- [ ] **Step 1: Locate the method**

```bash
grep -n "public boolean showSensitiveContent" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
```

Expected: prints one line, the method declaration. The line number may have shifted from L23541 — use whatever it prints. Note the line for the next step.

- [ ] **Step 2: Read the existing 6-line method**

The method body before edit is:

```java
    public boolean showSensitiveContent() {
        if (contentSettings != null && System.currentTimeMillis() - contentSettingsLoadedTime < 1000 * 60 * 60) {
            return contentSettings.sensitive_enabled;
        }
        return ignoreRestrictionReasons == null || ignoreRestrictionReasons.contains("sensitive");
    }
```

- [ ] **Step 3: Replace the body with the override (preserving original as comment)**

Replace the method body with:

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

- [ ] **Step 4: Verify the edit**

```bash
grep -nA 10 "public boolean showSensitiveContent" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java | head -14
```

Expected output shows the new method body containing both `// CUSTOM:` and `return false;` followed by the commented-out original.

```bash
grep -c "// CUSTOM: Sensitive (18+) content is permanently blocked on this build" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
```

Expected: `1`.

---

### Task 3: Site 2 — `MessagesController.setContentSettings(boolean)` defensive force-false

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` around L23517

- [ ] **Step 1: Locate the method**

```bash
grep -n "public void setContentSettings" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
```

Expected: prints one line. Note the line number.

- [ ] **Step 2: Read the existing first line of the method body**

The method signature is:

```java
    public void setContentSettings(boolean showSensitiveContent) {
        if (contentSettings != null) {
```

- [ ] **Step 3: Insert the defensive guard as the first two lines of the method body**

Modify the method so it begins:

```java
    public void setContentSettings(boolean showSensitiveContent) {
        // CUSTOM: Sensitive (18+) content is permanently blocked - force false regardless of caller
        showSensitiveContent = false;
        if (contentSettings != null) {
```

The rest of the method body stays unchanged. Do NOT add a `/* */` block here — we are not removing any original code, only adding two lines at the top.

- [ ] **Step 4: Verify the edit**

```bash
grep -nA 4 "public void setContentSettings" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
```

Expected output shows the method starting with the `// CUSTOM:` line followed by `showSensitiveContent = false;` followed by the original `if (contentSettings != null) {`.

```bash
grep -c "// CUSTOM: Sensitive (18+) content is permanently blocked - force false regardless of caller" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
```

Expected: `1`.

---

### Task 4: Site 3 — `MessagesController.getContentSettings(callback)` local hygiene

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` around L23494–23499

- [ ] **Step 1: Locate the block**

```bash
grep -n "if (contentSettings != null && ignoreRestrictionReasons != null)" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
```

Expected: prints one line. Note the line number.

- [ ] **Step 2: Read the existing block**

The pre-edit block is:

```java
            if (contentSettings != null && ignoreRestrictionReasons != null) {
                if (contentSettings.sensitive_enabled) ignoreRestrictionReasons.add("sensitive");
                else ignoreRestrictionReasons.remove("sensitive");
                if (mainPreferences != null) {
                    mainPreferences.edit().putStringSet("ignoreRestrictionReasons", ignoreRestrictionReasons).apply();
                }
            }
```

- [ ] **Step 3: Replace with the always-strip variant**

Replace the block with:

```java
            if (contentSettings != null && ignoreRestrictionReasons != null) {
                // CUSTOM: Sensitive (18+) content is permanently blocked - always strip "sensitive"
                // from ignoreRestrictionReasons regardless of server state. We do NOT actively sync
                // the server-side flag; the override at showSensitiveContent() is sufficient to
                // keep this client free of sensitive content.
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

- [ ] **Step 4: Verify the edit**

```bash
grep -nA 14 "if (contentSettings != null && ignoreRestrictionReasons != null)" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
```

Expected output shows:
- The new `// CUSTOM:` comment block.
- A single unconditional `ignoreRestrictionReasons.remove("sensitive");`.
- The commented-out original.
- The `mainPreferences` write block intact.

```bash
grep -c "always strip \"sensitive\"" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
```

Expected: `1`.

- [ ] **Step 5: Confirm the MessagesController CUSTOM count grew correctly**

```bash
grep -c "// CUSTOM:" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
```

Expected: `10` (was 7 pre-feature; +3 from Sites 1/2/3).

---

### Task 5: Site 4 — `ThemeActivity` row suppression

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java` around L702

- [ ] **Step 1: Locate the row-allocation block**

```bash
grep -n "contentSettings.sensitive_can_change" TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java
```

Expected: at least one line — the `if` predicate at the row-allocation site. (May print other matches; the one immediately before `sensitiveContentRow = rowCount++;` is the target.)

- [ ] **Step 2: Read the existing block**

The pre-edit block is:

```java
            TL_account.contentSettings contentSettings = getMessagesController().getContentSettings();
            if (contentSettings != null && contentSettings.sensitive_can_change) {
                sensitiveContentRow = rowCount++;
            }
            sendByEnterRow = rowCount++;
```

- [ ] **Step 3: Comment out the row allocation**

Replace the four lines (`if`, body, closing brace) with:

```java
            TL_account.contentSettings contentSettings = getMessagesController().getContentSettings();
            // CUSTOM: Hide "Show 18+ Content" toggle - sensitive content is permanently blocked
            // (See MessagesController.showSensitiveContent() for the underlying enforcement.)
            // if (contentSettings != null && contentSettings.sensitive_can_change) {
            //     sensitiveContentRow = rowCount++;
            // }
            sendByEnterRow = rowCount++;
```

The `TL_account.contentSettings contentSettings = ...` line above is preserved unchanged — it's used by other rows in the same method. The `sendByEnterRow = rowCount++;` line below is preserved unchanged.

- [ ] **Step 4: Verify the edit**

```bash
grep -nA 7 "TL_account.contentSettings contentSettings = getMessagesController" TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java
```

Expected output shows the `TL_account.contentSettings ...` line, the `// CUSTOM:` comment, the commented-out `if` block, and `sendByEnterRow = rowCount++;`.

```bash
grep -c "// CUSTOM: Hide \"Show 18+ Content\" toggle" TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java
```

Expected: `1`.

- [ ] **Step 5: Confirm `sensitiveContentRow = rowCount++` is now never live**

```bash
grep -nE 'sensitiveContentRow\s*=\s*rowCount\+\+' TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java
```

Expected: a single line that is part of the commented-out block (line begins with `//`). If you see a non-commented line, the edit is wrong.

---

### Task 6: Site 5a — `ChatActivity.didPressRevealSensitiveContent` neutralization

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java` around L40822–40903

- [ ] **Step 1: Locate the method**

```bash
grep -n "public void didPressRevealSensitiveContent" TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
```

Expected: one line. Note the line number (around 40822). The method body extends ~80 lines; the closing brace of the method should be a few lines before the next `public` or `private` declaration in the same anonymous class.

- [ ] **Step 2: Read the existing method body span**

The method header is:

```java
        @Override
        public void didPressRevealSensitiveContent(ChatMessageCell cell) {
            if (!getMessagesController().showSensitiveContent()) {
                final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.showDelayed(200);
                getMessagesController().getContentSettings(settings -> {
```

The body continues with the alert-builder flow (progress dialog, getContentSettings callback, alert dialog with verifyAge branch, etc.) and ends with:

```java
                });
                return;
            }
            if (cell.getMessageObject() != null) {
                cell.getMessageObject().isSensitiveCached = false;
            }
            cell.startRevealMedia();
        }
```

You can locate the closing brace by counting from `public void didPressRevealSensitiveContent` and finding the matching `}`. The method ends just before another `@Override` or `public void`/`public boolean`/`private void` at the same indentation in the same anonymous class — typically `public void openThisProfile()` follows.

- [ ] **Step 3: Replace the method body with bulletin + return + commented-out original**

The new method body is:

```java
        @Override
        public void didPressRevealSensitiveContent(ChatMessageCell cell) {
            // CUSTOM: Sensitive (18+) content is permanently blocked - close per-message reveal escape hatch
            BulletinFactory.of(ChatActivity.this).createErrorBulletin("Sensitive (18+) content is blocked on this build").show();
            return;
            // END CUSTOM
            /*
            if (!getMessagesController().showSensitiveContent()) {
                final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.showDelayed(200);
                getMessagesController().getContentSettings(settings -> {
                    progressDialog.dismissUnless(200);
                    final boolean needsVerification = getMessagesController().config.needAgeVideoVerification.get() && !TextUtils.isEmpty(getMessagesController().verifyAgeBotUsername);
                    final boolean cannotView = !(settings != null && settings.sensitive_can_change) && needsVerification;
                    boolean[] always = new boolean[1];
                    FrameLayout frameLayout = new FrameLayout(getContext());
                    if (needsVerification) {
                        always[0] = true;
                    } else if (settings != null && settings.sensitive_can_change) {
                        CheckBoxCell checkbox = new CheckBoxCell(getContext(), 1, getResourceProvider());
                        checkbox.setBackground(Theme.getSelectorDrawable(false));
                        checkbox.setText(getString(R.string.MessageShowSensitiveContentAlways), "", always[0], false);
                        checkbox.setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
                        frameLayout.addView(checkbox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                        checkbox.setOnClickListener(v -> {
                            CheckBoxCell cell1 = (CheckBoxCell) v;
                            always[0] = !always[0];
                            cell1.setChecked(always[0], true);
                        });
                    }
                    final AlertDialog.Builder alert = new AlertDialog.Builder(getContext(), getResourceProvider())
                            .setTitle(getString(R.string.MessageShowSensitiveContentMediaTitle))
                            .setMessage(getString(cannotView ? R.string.MessageShowSensitiveContentMediaTextClosed : R.string.MessageShowSensitiveContentMediaText))
                            .setView(frameLayout).setCustomViewOffset(9)
                            .setNegativeButton(getString(cannotView ? R.string.MessageShowSensitiveContentMediaTextClosedButton : R.string.Cancel), null);
                    if (!cannotView) {
                        alert.setPositiveButton(getString(R.string.MessageShowSensitiveContentButton), (di, w) -> {
                            final Utilities.Callback<Boolean> reveal = (all) -> {
                                if (all) {
                                    for (int i = 0; i < chatListView.getChildCount(); ++i) {
                                        View child = chatListView.getChildAt(i);
                                        if (!(child instanceof ChatMessageCell)) continue;
                                        ChatMessageCell messageCell = (ChatMessageCell) child;
                                        if (messageCell.getMessageObject() != null && messageCell.getMessageObject().isSensitive()) {
                                            messageCell.startRevealMedia();
                                        }
                                    }
                                } else {
                                    if (cell.getMessageObject() != null) {
                                        cell.getMessageObject().isSensitiveCached = false;
                                    }
                                    cell.startRevealMedia();
                                }
                            };
                            if (always[0]) {
                                if (needsVerification || settings != null && settings.sensitive_can_change) {
                                    ThemeActivity.verifyAge(getContext(), currentAccount, passed -> {
                                        if (!passed) {
                                            BulletinFactory.of(ChatActivity.this)
                                                .createSimpleBulletin(R.raw.error, getString(R.string.AgeVerificationFailedTitle), getString(R.string.AgeVerificationFailedText))
                                                .show();
                                            return;
                                        }
                                        getMessagesController().setContentSettings(true);
                                        BulletinFactory.of(ChatActivity.this)
                                            .createSimpleBulletinDetail(R.raw.chats_infotip, AndroidUtilities.replaceArrows(AndroidUtilities.premiumText(getString(R.string.SensitiveContentSettingsToast), () -> {
                                                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC).highlightSensitiveRow());
                                            }), true))
                                            .show(true);
                                        reveal.run(true);
                                    }, getResourceProvider());
                                } else {
                                    reveal.run(true);
                                }
                            } else {
                                reveal.run(false);
                            }
                        });
                    }
                    showDialog(alert.create());
                });
                return;
            }
            if (cell.getMessageObject() != null) {
                cell.getMessageObject().isSensitiveCached = false;
            }
            cell.startRevealMedia();
            */
        }
```

The original code is preserved verbatim inside the `/* */` block, just as `onJoinClicked` in `ProfileActivity.java` does. This avoids the unreachable-code compiler warning that live original code would produce.

- [ ] **Step 4: Verify the edit**

```bash
grep -nA 5 "public void didPressRevealSensitiveContent" TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
```

Expected output: method header followed by `// CUSTOM:`, the bulletin call, `return;`, `// END CUSTOM`.

```bash
grep -c "Sensitive (18+) content is blocked on this build" TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
```

Expected: `1`.

```bash
grep -c "// CUSTOM: Sensitive (18+) content is permanently blocked" TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
```

Expected: `1`.

- [ ] **Step 5: Confirm the original `MessageShowSensitiveContentMediaTitle` reference is now inside a comment**

```bash
grep -nE "MessageShowSensitiveContentMediaTitle" TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
```

Expected: a single line reporting the reference. Sanity-check it is inside the `/* ... */` block (read the surrounding context if uncertain). If the reference is outside the comment block, the edit is wrong.

---

### Task 7: Site 5b — `SharedMediaLayout` reveal alert neutralization

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java` around L3148–3220

- [ ] **Step 1: Locate the block**

```bash
grep -n "messageObject.isSensitive()" TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java
```

Expected: at least one line. The target is the line that opens the `if (messageObject != null && messageObject.isSensitive())` block at the cell-tap dispatcher.

- [ ] **Step 2: Read the existing block**

The pre-edit block (around L3147–3220) is:

```java
                } else if (mediaPage.selectedType == TAB_PHOTOVIDEO && view instanceof SharedPhotoVideoCell2) {
                    final SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
                    final MessageObject messageObject = cell.getMessageObject();
                    if (messageObject != null && messageObject.isSensitive()) {
                        if (profileActivity == null) return;
                        final int currentAccount = profileActivity.getCurrentAccount();
                        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
                        final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
                        progressDialog.showDelayed(200);
                        messagesController.getContentSettings(settings -> {
                            progressDialog.dismissUnless(200);
                            // ... ~60 lines of alert flow ...
                            if (profileActivity != null && profileActivity.getContext() != null) {
                                profileActivity.showDialog(alert.create());
                            } else {
                                alert.show();
                            }
                        });
                        return;
                    }
                    if (cell.canRevealSpoiler()) {
                        cell.startRevealMedia(x, y);
                        return;
                    }
                    if (messageObject != null) {
                        onItemClick(position, view, messageObject, 0, mediaPage.selectedType);
                    }
                } else if ((isAnyStoryPageType(mediaPage.selectedType)) && view instanceof SharedPhotoVideoCell2) {
```

- [ ] **Step 3: Replace the inner `if (...isSensitive())` block contents with bulletin + return**

Replace the body of the `if (messageObject != null && messageObject.isSensitive()) { ... }` block with:

```java
                    if (messageObject != null && messageObject.isSensitive()) {
                        // CUSTOM: Sensitive (18+) content is permanently blocked - close per-message reveal escape hatch
                        if (profileActivity != null) {
                            BulletinFactory.of(profileActivity).createErrorBulletin("Sensitive (18+) content is blocked on this build").show();
                        } else {
                            BulletinFactory.global().createErrorBulletin("Sensitive (18+) content is blocked on this build").show();
                        }
                        return;
                        // END CUSTOM
                        /*
                        if (profileActivity == null) return;
                        final int currentAccount = profileActivity.getCurrentAccount();
                        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
                        final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
                        progressDialog.showDelayed(200);
                        messagesController.getContentSettings(settings -> {
                            progressDialog.dismissUnless(200);
                            final boolean needsVerification = messagesController.config.needAgeVideoVerification.get() && !TextUtils.isEmpty(messagesController.verifyAgeBotUsername);
                            final boolean cannotView = !(settings != null && settings.sensitive_can_change) && needsVerification;
                            boolean[] always = new boolean[1];
                            FrameLayout frameLayout = new FrameLayout(context);
                            if (needsVerification) {
                                always[0] = true;
                            } else if (settings != null && settings.sensitive_can_change) {
                                CheckBoxCell checkbox = new CheckBoxCell(context, 1, profileActivity == null ? null : profileActivity.getResourceProvider());
                                checkbox.setBackground(Theme.getSelectorDrawable(false));
                                checkbox.setText(getString(R.string.MessageShowSensitiveContentAlways), "", always[0], false);
                                checkbox.setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
                                frameLayout.addView(checkbox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                                checkbox.setOnClickListener(v -> {
                                    CheckBoxCell cell1 = (CheckBoxCell) v;
                                    always[0] = !always[0];
                                    cell1.setChecked(always[0], true);
                                });
                            }
                            final AlertDialog.Builder alert = new AlertDialog.Builder(context, profileActivity == null ? null : profileActivity.getResourceProvider())
                                .setTitle(getString(R.string.MessageShowSensitiveContentMediaTitle))
                                .setMessage(getString(cannotView ? R.string.MessageShowSensitiveContentMediaTextClosed : R.string.MessageShowSensitiveContentMediaText))
                                .setView(frameLayout).setCustomViewOffset(9)
                                .setNegativeButton(getString(cannotView ? R.string.MessageShowSensitiveContentMediaTextClosedButton : R.string.Cancel), null);
                            if (!cannotView) {
                                alert.setPositiveButton(getString(R.string.MessageShowSensitiveContentButton), (di, w) -> {
                                    final Utilities.Callback<Boolean> reveal = (all) -> {
                                        cell.startRevealMedia(x, y);
                                    };
                                    if (always[0]) {
                                        if (needsVerification || settings != null && settings.sensitive_can_change) {
                                            final BaseFragment lastFragment1 = LaunchActivity.getSafeLastFragment();
                                            ThemeActivity.verifyAge(context, currentAccount, passed -> {
                                                final BaseFragment lastFragment2 = LaunchActivity.getSafeLastFragment();
                                                if (passed) {
                                                    messagesController.setContentSettings(true);
                                                    if (lastFragment2 != null) {
                                                        BulletinFactory.of(lastFragment2)
                                                            .createSimpleBulletinDetail(R.raw.chats_infotip, AndroidUtilities.replaceArrows(AndroidUtilities.premiumText(getString(R.string.SensitiveContentSettingsToast), () -> {
                                                                lastFragment2.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC).highlightSensitiveRow());
                                                            }), true))
                                                            .show(true);
                                                    }
                                                    reveal.run(true);
                                                } else if (lastFragment2 != null) {
                                                    BulletinFactory.of(lastFragment2)
                                                        .createSimpleBulletin(R.raw.error, getString(R.string.AgeVerificationFailedTitle), getString(R.string.AgeVerificationFailedText))
                                                        .show();
                                                }
                                            }, lastFragment1 == null ? null : lastFragment1.getResourceProvider());
                                        } else {
                                            reveal.run(true);
                                        }
                                    } else {
                                        reveal.run(false);
                                    }
                                });
                            }
                            if (profileActivity != null && profileActivity.getContext() != null) {
                                profileActivity.showDialog(alert.create());
                            } else {
                                alert.show();
                            }
                        });
                        return;
                        */
                    }
```

The trailing `if (cell.canRevealSpoiler())` and `if (messageObject != null)` blocks below the `if (...isSensitive())` block are NOT modified — they handle the non-sensitive media path.

- [ ] **Step 4: Verify the edit**

```bash
grep -nA 8 "messageObject != null && messageObject.isSensitive" TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java
```

Expected output shows the `if (...isSensitive())` line, the `// CUSTOM:` comment, the `BulletinFactory.of(profileActivity)...` call, the global fallback, `return;`, `// END CUSTOM`.

```bash
grep -c "Sensitive (18+) content is blocked on this build" TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java
```

Expected: `2` (one for the `BulletinFactory.of(profileActivity)` call, one for the `BulletinFactory.global()` fallback).

```bash
grep -c "// CUSTOM: Sensitive (18+) content is permanently blocked" TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java
```

Expected: `1`.

- [ ] **Step 5: Confirm the original alert flow is now inside a comment**

```bash
grep -nE "MessageShowSensitiveContentMediaTitle" TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java
```

Expected: a single line. Read the surrounding context to confirm it sits inside the `/* ... */` block introduced in Step 3.

---

### Task 8: Code-level verification of all five sites

**Files:** none modified.

- [ ] **Step 1: Total CUSTOM marker count**

```bash
total=$(grep -rc "// CUSTOM:" TMessagesProj/src/ --include="*.java" | awk -F: '{s+=$2} END{print s}')
echo "Post-feature CUSTOM marker count: $total (target: 38)"
```

Expected: `38`.

- [ ] **Step 2: Per-file marker counts (target after this feature)**

```bash
for f in \
  "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java:10" \
  "TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java:1" \
  "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java:8" \
  "TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java:1" \
; do
  file="${f%:*}"
  target="${f##*:}"
  c=$(grep -c "// CUSTOM:" "$file")
  flag=""; [ "$c" -ne "$target" ] && flag=" *** EXPECTED $target ***"
  echo "  $c  $file$flag"
done
```

Expected per-file counts: `MessagesController.java` 10, `ThemeActivity.java` 1, `ChatActivity.java` 8, `SharedMediaLayout.java` 1. Any mismatch means a site is missing or duplicated — re-check the corresponding task.

- [ ] **Step 3: Bulletin string occurrence**

```bash
echo "Bulletin occurrences across project:"
grep -rE --include="*.java" -- 'Sensitive \(18\+\) content is blocked on this build' TMessagesProj/src/ | wc -l
```

Expected: `3` (one in `ChatActivity.java`, two in `SharedMediaLayout.java`).

- [ ] **Step 4: Confirm key invariants**

```bash
echo "showSensitiveContent first non-comment statement (must be 'return false;'):"
grep -nA 4 "public boolean showSensitiveContent" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java | head -6

echo ""
echo "setContentSettings first non-comment statement (must be 'showSensitiveContent = false;'):"
grep -nA 3 "public void setContentSettings" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java | head -5

echo ""
echo "ignoreRestrictionReasons.add(\"sensitive\") (must be ZERO live occurrences):"
grep -nE 'ignoreRestrictionReasons\.add\("sensitive"\)' TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
```

Expected:
- `showSensitiveContent` body's first non-comment line is `return false;`.
- `setContentSettings` body's first non-comment line is `showSensitiveContent = false;`.
- `ignoreRestrictionReasons.add("sensitive")` matches: only line(s) inside `/* */` comment blocks (one in setContentSettings, one in the commented-out original of getContentSettings callback). Read context of each match; if any sits outside a comment block, an edit is wrong.

- [ ] **Step 5: Confirm `sensitiveContentRow = rowCount++` is never live**

```bash
grep -nE 'sensitiveContentRow\s*=\s*rowCount\+\+' TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java
```

Expected: a single line that begins with `//` (i.e., commented out). If a non-commented match is found, Site 4 is broken.

- [ ] **Step 6: Confirm no conflict markers, no leftover scratch**

```bash
git diff --name-only
git diff --check 2>&1 | grep -E '^<<<<<<<|^=======|^>>>>>>>' || echo "(no conflict markers)"
```

Expected:
- `git diff --name-only` shows only the 4 expected files (`MessagesController.java`, `ThemeActivity.java`, `ChatActivity.java`, `SharedMediaLayout.java`).
- No conflict markers anywhere.

If any other file is dirty, investigate before staging.

---

### Task 9: Stage and commit the feature

**Files:** none further modified; this task creates the feature commit.

- [ ] **Step 1: Stage all four modified files**

```bash
git add \
  TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java \
  TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java \
  TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java \
  TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java
git status
```

Expected: only those four files in "Changes to be committed". Working tree clean otherwise.

- [ ] **Step 2: Pre-commit sanity diff**

```bash
git diff --cached --stat
```

Expected: 4 files changed; net insertions clearly positive (the original code is preserved as comments, so we add lines without removing many).

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
Block sensitive (18+) content - hide toggle and force off

Five surgical overrides at all reachable enforcement sites:

- MessagesController.showSensitiveContent() now returns false
  unconditionally. Single-line override; original code preserved in
  a /* */ block.
- MessagesController.setContentSettings(boolean) defensively forces
  the argument to false at entry. Belt-and-suspenders against any
  caller that survives a future regression.
- MessagesController.getContentSettings() callback always strips
  "sensitive" from ignoreRestrictionReasons regardless of server
  state. No active server-side sync (per design decision).
- ThemeActivity.updateRowsIds() never allocates the
  sensitiveContentRow. Existing >= 0 guards make it a no-op
  everywhere it is referenced.
- ChatActivity.didPressRevealSensitiveContent and the equivalent
  per-message reveal alert in SharedMediaLayout are replaced with a
  blocking bulletin: "Sensitive (18+) content is blocked on this
  build". This neuters both the per-message tap-through and the
  per-dialog sensitiveAgreed opt-in.

Marker count: 32 -> 38 (+6 across 4 files; SharedMediaLayout is a
new entry in the custom-modified-files list).

See docs/superpowers/specs/2026-05-04-sensitive-content-lockdown-design.md
for the full design.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git log -1 --format='%h %s'
```

Expected: commit lands on `nerovny/detox` with the subject `Block sensitive (18+) content - hide toggle and force off`.

---

### Task 10: CLAUDE.md follow-up commit

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Find the existing "Files with Custom Modifications" list and the feature subsections**

```bash
grep -n "^### [0-9]\+\.\|Files with Custom Modifications\|Modified files:" CLAUDE.md | head -30
```

Note: there is a numbered list of feature subsections (e.g. `### 8. Similar Channels Disabled`, `### 9. Profile Channel Links Blocked`, `### 10. Additional Features`). The new subsection for sensitive content lockdown should be inserted as a new numbered entry in that sequence (matching the existing numbering).

- [ ] **Step 2: Add a new feature subsection before "Additional Features"**

Find the `### 10. Additional Features` heading and insert a new subsection immediately before it. The new subsection text:

```markdown
### 10. Sensitive (18+) Content Lockdown

The "Show 18+ Content" toggle in Settings is hidden, and sensitive content is permanently invisible on this build. Tapping a redacted thumbnail produces a blocking bulletin instead of the upstream tap-to-reveal alert. Local enforcement only — the server-side flag is not actively synced.

**Implementation**:

`TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java`:
- `showSensitiveContent()` — returns `false` unconditionally. Original code preserved as a `/* */` comment.
- `setContentSettings(boolean)` — defensive force-false at the entry point. The user-facing path is hidden; this guards against any future caller.
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
```

- [ ] **Step 3: Renumber the "Additional Features" section**

The current `### 10. Additional Features` heading must become `### 11. Additional Features`. Make this single rename.

- [ ] **Step 4: Add Feature → File Mapping rows**

Find the Feature → File Mapping table and add a new section (or add rows to an existing related section). The simplest placement: a new sub-table immediately after "Subscription & Access Control":

```markdown
### Sensitive (18+) Content Lockdown
| Feature | File | Key Functions/Locations |
|---------|------|-------------------------|
| Read API force-false | `MessagesController.java` | `showSensitiveContent()` returns `false` |
| Write API defensive force-false | `MessagesController.java` | `setContentSettings(boolean)` first line |
| Local hygiene strip | `MessagesController.java` | `getContentSettings(callback)` callback |
| Toggle row hidden | `ThemeActivity.java` | `updateRowsIds()` row allocation commented out |
| Per-message reveal blocked (chat) | `ChatActivity.java` | `didPressRevealSensitiveContent()` |
| Per-message reveal blocked (shared media) | `SharedMediaLayout.java` | `isSensitive()` branch in cell-tap dispatcher |
```

- [ ] **Step 5: Add Feature Testing Checklist row**

Find the existing Feature Testing Checklist sub-tables and add a new row group:

```markdown
### Sensitive (18+) Content Lockdown
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| 18+ toggle hidden in Settings | 1. Open Settings 2. Scroll to where the "Show 18+ Content" toggle would appear (between "Direct share" and "Send by Enter") | The toggle does not appear. |
| 18+ media stays redacted | 1. Open a chat or shared-media gallery containing 18+ content | Media renders with the spoiler/blur overlay. The "18+" badge is still visible (upstream behavior). |
| Tap on redacted 18+ media is blocked | 1. Tap a redacted 18+ thumbnail in a chat or in shared media | Bulletin: "Sensitive (18+) content is blocked on this build". Media stays redacted. |
| Tap on redacted 18+ media in shared media | 1. Open a profile/shared-media gallery 2. Tap a redacted 18+ thumbnail | Same bulletin as above. |
```

- [ ] **Step 6: Update the marker-count expectation in the Post-Merge Verification subsection**

Find the line in the "Post-Merge Verification (Code-Level)" subsection (added by the prior CLAUDE.md commit) that mentions `// CUSTOM:` count if any, OR add the new note. If no count is mentioned, append after step 4 of that subsection:

```markdown
The total `// CUSTOM:` marker count is currently 38 across 11 files. A merge that drops the count below this baseline has lost a guard somewhere — investigate before reporting the merge complete.
```

- [ ] **Step 7: Update the "Files with Custom Modifications" Modified Files list**

Find the bulleted "Modified files:" list and add `SharedMediaLayout.java`:

```markdown
- `SharedMediaLayout.java` - Per-message reveal alert blocked (sensitive content)
```

Also append to the `MessagesController.java`, `ThemeActivity.java`, and `ChatActivity.java` lines a brief mention of the new behavior. Existing lines:
- `MessagesController.java` — append `, sensitive content read/write force-off, ignoreRestrictionReasons hygiene`
- `ThemeActivity.java` — add this file if not already in the list (it isn't); insert a line: `- ThemeActivity.java - "Show 18+ Content" toggle hidden in Settings`
- `ChatActivity.java` — append `, per-message sensitive-content reveal blocked`

- [ ] **Step 8: Verify edits**

```bash
grep -n "^### [0-9]\+\." CLAUDE.md
grep -n "Sensitive (18+) Content Lockdown" CLAUDE.md
grep -n "SharedMediaLayout" CLAUDE.md
```

Expected:
- The numbered subsections include both `### 10. Sensitive (18+) Content Lockdown` and `### 11. Additional Features`.
- "Sensitive (18+) Content Lockdown" appears multiple times (subsection heading, Feature Mapping table heading, Feature Testing Checklist heading).
- `SharedMediaLayout` appears in the Modified files list, the Feature Mapping table, and the Feature subsection.

- [ ] **Step 9: Stage and commit the CLAUDE.md follow-up**

```bash
git add CLAUDE.md
git diff --cached --stat CLAUDE.md
git commit -m "$(cat <<'EOF'
Document sensitive (18+) content lockdown in CLAUDE.md

- New subsection "10. Sensitive (18+) Content Lockdown" describing
  the five enforcement sites and the bulletin-replacement design.
- Renumber the prior "Additional Features" subsection to 11.
- New Feature -> File Mapping table block.
- New Feature Testing Checklist block.
- Update marker-count baseline note in the Post-Merge Verification
  subsection (38 markers across 11 files).
- Add SharedMediaLayout.java to the Modified files list and append
  new behavior notes to MessagesController, ThemeActivity, and
  ChatActivity entries.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git log -2 --format='%h %s'
```

Expected: two new commits visible — the CLAUDE.md follow-up on top, the feature commit beneath.

---

## Final state

- `nerovny/detox` advanced by two commits: the feature commit and the CLAUDE.md follow-up.
- Total `// CUSTOM:` markers: 38 across 11 files.
- Bulletin string `"Sensitive (18+) content is blocked on this build"` appears 3 times across the codebase (1 in `ChatActivity.java`, 2 in `SharedMediaLayout.java`).
- Branch ready for sub-project C (hashtag external-lookup loophole) brainstorm.
- The user's later build/runtime test confirms: toggle absent in Settings, 18+ media stays redacted, tap-to-reveal blocked.
