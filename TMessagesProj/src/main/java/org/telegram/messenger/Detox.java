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
    public static final boolean PACK_SEARCH_BLOCKED = true;
    public static final boolean GUEST_BOT_HINTS_BLOCKED = true;

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

    /**
     * Returns true once the asset has actually been read (or was found missing); false if the
     * app context is not up yet, so that the next caller retries instead of latching an empty
     * blocklist for the lifetime of the process.
     */
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

    /** blocked_chats.txt - case-insensitive substring match on the chat/user display name. */
    private static boolean isNameBlocked(String name) {
        if (!chatBlocklistLoaded) {
            chatBlocklistLoaded = load("blocked_chats.txt", BLOCKED_CHAT_NAMES);
        }
        return matches(BLOCKED_CHAT_NAMES, name);
    }

    /** blocked_comments.txt - channels we read but do not engage with. */
    private static boolean isCommentBlocked(TLRPC.Chat chat) {
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

    /**
     * A message from a chat named in blocked_chats.txt. Distinct from isBlockedMessage:
     * this is the name blocklist, not the subscription check.
     */
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

    /**
     * A User or Chat named in blocked_chats.txt. For adapters that filter over a
     * heterogeneous result list.
     */
    public static boolean isBlockedByName(Object obj) {
        String name = null;
        if (obj instanceof TLRPC.User) {
            name = UserObject.getUserName((TLRPC.User) obj);
        } else if (obj instanceof TLRPC.Chat) {
            name = ((TLRPC.Chat) obj).title;
        }
        return isNameBlocked(name);
    }

    /**
     * A channel may be shown in a channel list only if we are subscribed - checking the cached
     * local copy too, since search results carry a stripped Chat.
     */
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

    /**
     * Joining a channel from mobile is always blocked - desktop is the intentional friction.
     * Always returns true.
     */
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

    /**
     * In-chat search, blocked for chats named in blocked_chats.txt. Exactly one of chat/user
     * is non-null.
     */
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
     * Hashtag search is allowed only over our own messages, or scoped to a subscribed chat /
     * a contact / a non-bot user. A null target means a global public lookup - that is the
     * loophole this closes. Silent: shows no bulletin, the caller reports an empty result set.
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
