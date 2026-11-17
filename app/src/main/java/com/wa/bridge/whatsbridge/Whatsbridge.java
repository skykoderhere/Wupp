package com.wa.bridge.whatsbridge;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory stub for the gomobile whatsbridge.aar Go bridge when the native AAR
 * binary is not present in the build environment.
 */
public final class Whatsbridge {
    private static NativeBridgeCallback sCallback;
    private static final AtomicBoolean sConnected = new AtomicBoolean(false);
    private static final AtomicBoolean sLoggedIn = new AtomicBoolean(false);
    private static volatile boolean sGhostPresence = false;
    private static volatile String sOwnPushName = "Wupp User";
    private static volatile String sOwnAbout = "Hey there! I am using WhatsApp.";

    private Whatsbridge() {}

    public static void initClient(String filesDir, NativeBridgeCallback callback) throws Exception {
        sCallback = callback;
        if (callback != null) {
            callback.onJsonEvent("ENGINE_READY", "{\"ownJid\":\"\",\"isLoggedIn\":false}");
        }
    }

    public static void connectClient() throws Exception {
        NativeBridgeCallback cb = sCallback;
        if (cb != null && !sLoggedIn.get()) {
            cb.onJsonEvent("QR", "{\"code\":\"2@WuppCompanionBridgeStub,AwaitingLiveGoAar\"}");
        }
    }

    public static void disconnectClient() {
        sConnected.set(false);
        NativeBridgeCallback cb = sCallback;
        if (cb != null) {
            cb.onJsonEvent("CONNECTION_STATE", "{\"state\":\"DISCONNECTED\"}");
        }
    }

    public static void resetSocket() {
        NativeBridgeCallback cb = sCallback;
        if (cb != null && !sLoggedIn.get()) {
            cb.onJsonEvent("QR", "{\"code\":\"2@WuppCompanionBridgeStub,Reset-" + System.currentTimeMillis() + "\"}");
        }
    }

    public static String pairWithPhoneNumber(String phone) throws Exception {
        String code = "WUPP-2026";
        NativeBridgeCallback cb = sCallback;
        if (cb != null) {
            cb.onJsonEvent("PAIR_CODE", "{\"code\":\"" + code + "\"}");
        }
        return code;
    }

    public static boolean isConnected() {
        return sConnected.get();
    }

    public static boolean isLoggedIn() {
        return sLoggedIn.get();
    }

    public static String getOwnJID() {
        return "";
    }

    public static void setGhostPresence(boolean enabled) {
        sGhostPresence = enabled;
    }

    public static String sendTextMessage(
            String chatJid,
            String text,
            String quotedId,
            String quotedSender,
            String quotedBody,
            String mentionsCsv,
            boolean ghostTyping
    ) throws Exception {
        return "LOCAL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public static void editMessage(String chatJid, String messageId, String newText) throws Exception {
    }

    public static void revokeMessage(String chatJid, String messageId) throws Exception {
    }

    public static String sendMediaMessage(
            String chatJid,
            String filePath,
            String mimeType,
            String caption,
            String qualityMode,
            boolean isVoiceNote,
            long durationSec,
            String waveformCsv
    ) throws Exception {
        return "MEDIA_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public static String resolveParticipantNamesJson(String jidsCsv) {
        return "{}";
    }

    public static void fetchFullProfilePicture(String jidStr) {
    }

    public static void syncContactsAndAvatars(String jidsCsv) {
    }

    public static void markReadManual(String chatJid, String senderJid, String messageIdsCsv) throws Exception {
    }

    public static void sendPlayedReceiptManual(String chatJid, String senderJid, String messageId) throws Exception {
    }

    public static boolean hasCachedMediaMeta(String messageId) {
        return false;
    }

    public static void requestMediaDownload(String chatJid, String senderJid, String messageId, long timestampMs) throws Exception {
    }

    public static void sendChatPresenceManual(String chatJid, String state, String media, boolean blockedByGhost) throws Exception {
    }

    public static void subscribeContactPresence(String jidStr) throws Exception {
    }

    public static String getGroupParticipantsJson(String groupJid) throws Exception {
        return "[]";
    }

    public static String verifyNumbersOnWhatsApp(String phonesCsv) throws Exception {
        return "[]";
    }

    public static void setAutoDownloadPolicy(boolean allowPhoto, boolean allowAudio, boolean allowVideo, boolean allowDoc) {
    }

    public static void fetchNewsletterMessages(String channelJid) throws Exception {
    }

    public static void toggleNewsletterMute(String channelJid, boolean muted) throws Exception {
    }

    public static void deleteChatRemote(
            String chatJid,
            String lastMsgId,
            boolean lastMsgFromMe,
            String lastMsgParticipant,
            long lastMsgTimestampMs,
            boolean deleteMedia
    ) throws Exception {
    }

    public static void fetchGroupLockState(String groupJid) throws Exception {
    }

    public static void sendReaction(
            String chatJid,
            String targetMessageId,
            String senderJid,
            boolean fromMe,
            String emoji
    ) throws Exception {
    }

    public static String getOwnProfileJson() throws Exception {
        return "{\"ownJid\":\"\",\"phone\":\"\",\"pushName\":\"" + sOwnPushName + "\",\"about\":\"" + sOwnAbout + "\"}";
    }

    public static void setOwnPushName(String name) throws Exception {
        if (name != null) sOwnPushName = name;
    }

    public static void setOwnAbout(String text) throws Exception {
        if (text != null) sOwnAbout = text;
    }

    public static String getAccountPrivacySettingsJson() throws Exception {
        return "{\"lastSeen\":\"all\",\"online\":\"all\",\"profile\":\"all\",\"status\":\"contacts\",\"groupAdd\":\"all\",\"readReceipts\":\"all\",\"callAdd\":\"all\"}";
    }

    public static String setAccountPrivacySetting(String category, String value) throws Exception {
        return getAccountPrivacySettingsJson();
    }

    public static void setDefaultDisappearingTimerSec(long seconds) throws Exception {
    }

    public static String getBlocklistJson() throws Exception {
        return "[]";
    }

    public static void setContactBlocked(String jidStr, boolean blocked) throws Exception {
    }
}
