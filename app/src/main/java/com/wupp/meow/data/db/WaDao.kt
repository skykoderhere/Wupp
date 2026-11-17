package com.wupp.meow.data.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class UnreadMessageSenderTuple(
    val id: String,
    @ColumnInfo(name = "sender_jid") val senderJid: String
)

@Dao
interface WaDao {

    @Query(
        """
        SELECT * FROM chats
        WHERE (:includeArchived = 1 OR is_archived = 0)
        ORDER BY is_pinned DESC, pin_order DESC, last_message_timestamp DESC
        """
    )
    fun observeChats(includeArchived: Boolean = false): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE jid = :jid LIMIT 1")
    fun observeChat(jid: String): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE jid = :jid LIMIT 1")
    suspend fun getChat(jid: String): ChatEntity?

    @Query("SELECT * FROM chats ORDER BY last_message_timestamp DESC")
    suspend fun getAllChatsOnce(): List<ChatEntity>

    @Query("SELECT * FROM contacts ORDER BY is_saved DESC, LOWER(name) ASC")
    fun observeContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE jid = :jid LIMIT 1")
    suspend fun getContact(jid: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE jid = :jid LIMIT 1")
    fun observeContact(jid: String): Flow<ContactEntity?>

    @Query(
        """
        SELECT COUNT(*) FROM chats
        WHERE is_archived = 1
          AND is_community_parent = 0
          AND jid NOT LIKE '%@newsletter'
          AND jid != 'status@broadcast'
          AND jid NOT LIKE '%@broadcast'
        """
    )
    fun observeArchivedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContactRow(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE jid = :jid")
    suspend fun deleteContactRow(jid: String)

    @Query("UPDATE contacts SET avatar_path = :avatarPath WHERE jid = :jid")
    suspend fun updateContactAvatar(jid: String, avatarPath: String)

    @Query("UPDATE contacts SET about_text = :aboutText WHERE jid = :jid")
    suspend fun updateContactAbout(jid: String, aboutText: String)

    @Transaction
    suspend fun upsertContacts(incoming: List<ContactEntity>) {
        if (incoming.isEmpty()) return
        for (c in incoming) {
            val cleanJid = c.jid.trim()
            if (cleanJid.isBlank() ||
                cleanJid == "status@broadcast" ||
                cleanJid.endsWith("@broadcast") ||
                cleanJid.endsWith("@g.us") ||
                cleanJid.endsWith("@newsletter")
            ) continue
            val bare = cleanJid.substringBefore("@")
            val existing = getContact(cleanJid)
            if (existing == null) {
                val safeName = c.name.trim().ifBlank { bare }
                upsertContactRow(c.copy(jid = cleanJid, name = safeName))
            } else {
                val existingReal = existing.name.isNotBlank() &&
                    existing.name != cleanJid &&
                    existing.name != bare &&
                    !existing.name.startsWith("+")
                val incomingReal = c.name.isNotBlank() &&
                    c.name != cleanJid &&
                    c.name != bare &&
                    !c.name.startsWith("+")
                val resolvedName = when {
                    c.isSaved && incomingReal -> c.name.trim()
                    existing.isSaved && existingReal -> existing.name
                    incomingReal -> c.name.trim()
                    existingReal -> existing.name
                    else -> c.name.trim().ifBlank { existing.name.ifBlank { bare } }
                }
                upsertContactRow(
                    existing.copy(
                        name = resolvedName,
                        aboutText = c.aboutText.ifBlank { existing.aboutText },
                        avatarPath = c.avatarPath?.takeIf { it.isNotBlank() } ?: existing.avatarPath,
                        isSaved = existing.isSaved || c.isSaved,
                        updatedAt = maxOf(existing.updatedAt, c.updatedAt)
                    )
                )
            }
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: ChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChats(chats: List<ChatEntity>)

    @Query("UPDATE chats SET unread_count = 0 WHERE jid = :chatJid")
    suspend fun clearLocalUnread(chatJid: String)

    @Query("UPDATE chats SET unread_count = 0 WHERE jid LIKE '%@newsletter' AND unread_count > 0")
    suspend fun resetInflatedNewsletterUnread()

    @Query("UPDATE chats SET is_pinned = :pinned, pin_order = :pinOrder WHERE jid = :chatJid")
    suspend fun updatePinned(chatJid: String, pinned: Boolean, pinOrder: Long)

    @Query("UPDATE chats SET is_muted = :muted, mute_end_time = :muteEnd WHERE jid = :chatJid")
    suspend fun updateMuted(chatJid: String, muted: Boolean, muteEnd: Long)

    @Query(
        """
        UPDATE chats
        SET is_archived = :archived,
            local_archive_override = CASE WHEN :archived = 1 THEN 1 ELSE -1 END
        WHERE jid = :chatJid
        """
    )
    suspend fun updateArchived(chatJid: String, archived: Boolean)

    @Query("UPDATE chats SET is_archived = :archived WHERE jid = :chatJid AND local_archive_override = 0")
    suspend fun updateArchivedFromSync(chatJid: String, archived: Boolean)

    @Query("UPDATE chats SET is_favorite = :favorite WHERE jid = :chatJid")
    suspend fun updateFavorite(chatJid: String, favorite: Boolean)

    @Query("UPDATE chats SET unread_count = :unreadCount WHERE jid = :chatJid")
    suspend fun updateUnreadCount(chatJid: String, unreadCount: Int)

    @Query("DELETE FROM messages WHERE chat_jid = :chatJid")
    suspend fun deleteMessagesForChat(chatJid: String)

    @Query("DELETE FROM messages WHERE chat_jid = :chatJid AND timestamp <= :maxTimestamp")
    suspend fun deleteMessagesForChatUpTo(chatJid: String, maxTimestamp: Long)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM message_edits WHERE message_id = :messageId")
    suspend fun deleteEditsForMessage(messageId: String)

    @Query("DELETE FROM group_receipts WHERE message_id = :messageId")
    suspend fun deleteReceiptsForMessage(messageId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE local_media_path = :mediaPath")
    suspend fun countMediaPathReferences(mediaPath: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE chat_jid = :chatJid AND is_deleted_by_sender = 1)")
    suspend fun hasAnyDeletedMessage(chatJid: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE chat_jid = :chatJid AND is_edited = 1)")
    suspend fun hasAnyEditedMessage(chatJid: String): Boolean

    @Query("DELETE FROM chats WHERE jid = :chatJid")
    suspend fun deleteChatRow(chatJid: String)

    @Query(
        "UPDATE chats SET last_message_preview = :preview, last_message_type = :type, last_message_timestamp = :timestamp, unread_count = :unreadCount WHERE jid = :chatJid"
    )
    suspend fun updateChatPreviewAfterClear(
        chatJid: String,
        preview: String,
        type: String,
        timestamp: Long,
        unreadCount: Int
    )

    @Query(
        """
        UPDATE chats SET name = :name
        WHERE jid = :jid
          AND (
            name = ''
            OR name = jid
            OR (instr(jid, '@') > 1 AND (name = substr(jid, 1, instr(jid, '@') - 1) OR name = '+' || substr(jid, 1, instr(jid, '@') - 1)))
            OR (name LIKE '+%' AND :name NOT LIKE '+%')
            OR :overwrite = 1
          )
        """
    )
    suspend fun updateChatName(jid: String, name: String, overwrite: Boolean = false)

    @Query("UPDATE chats SET is_online = :isOnline, last_seen = CASE WHEN :lastSeen > 0 THEN :lastSeen ELSE last_seen END WHERE jid = :jid")
    suspend fun updateChatPresenceOnline(jid: String, isOnline: Boolean, lastSeen: Long)

    @Query("UPDATE chats SET chat_presence = :presenceText WHERE jid = :jid")
    suspend fun updateChatTypingState(jid: String, presenceText: String)

    @Query("UPDATE chats SET chat_presence = '' WHERE chat_presence != ''")
    suspend fun clearAllChatPresence()

    @Query("UPDATE messages SET audio_transcript = NULL WHERE audio_transcript LIKE '[Offline Acoustic Engine%' OR audio_transcript LIKE '[Whisper.cpp Local%'")
    suspend fun clearFakeTranscripts()

    @Query("DELETE FROM chats WHERE jid IN ('15550192834@s.whatsapp.net', '15550198811@s.whatsapp.net', '120363044556677889@g.us', '15550000463@s.whatsapp.net')")
    suspend fun deleteShowcaseChats()

    @Query("DELETE FROM messages WHERE chat_jid IN ('15550192834@s.whatsapp.net', '15550198811@s.whatsapp.net', '120363044556677889@g.us', '15550000463@s.whatsapp.net')")
    suspend fun deleteShowcaseMessages()

    @Query("DELETE FROM statuses WHERE sender_jid IN ('15550192834@s.whatsapp.net', '15550198811@s.whatsapp.net') OR id IN ('st_elena_1', 'st_marcus_1')")
    suspend fun deleteShowcaseStatuses()

    @Query(
        """
        DELETE FROM chats
        WHERE is_group = 0
          AND is_community_parent = 0
          AND jid NOT LIKE '%@newsletter'
          AND (
            jid = 'status@broadcast'
            OR jid LIKE '%@broadcast'
            OR name LIKE '@%'
            OR jid LIKE 'hanzalaramzan%'
            OR (
                last_message_preview = ''
                AND (last_message_id IS NULL OR last_message_id = '')
                AND NOT EXISTS (
                    SELECT 1 FROM messages m WHERE m.chat_jid = chats.jid
                )
            )
          )
        """
    )
    suspend fun cleanupEmptyProtocolChats()

    @Query("DELETE FROM messages WHERE msg_type = 'UNKNOWN' AND body = '' AND is_view_once = 0 AND (local_media_path IS NULL OR local_media_path = '')")
    suspend fun deleteEmptyUnknownMessages()

    @Query("UPDATE chats SET last_message_preview = 'Photo', last_message_type = 'VIEW_ONCE_IMAGE' WHERE last_message_preview LIKE 'View once media%' OR last_message_preview LIKE 'View once image%' OR last_message_preview LIKE 'View once text%' OR last_message_preview = 'View once photo'")
    suspend fun normalizeViewOncePreviews()

    @Transaction
    suspend fun upsertSyncedNewsletters(channels: List<ChatEntity>) {
        for (ch in channels) {
            val existing = getChat(ch.jid)
            if (existing == null) {
                upsertChat(ch)
            } else {
                val jidUser = ch.jid.substringBefore("@")
                val resolvedName = if (ch.name.isNotBlank() && ch.name != ch.jid && ch.name != jidUser) {
                    ch.name
                } else {
                    existing.name
                }
                upsertChat(
                    existing.copy(
                        name = resolvedName,
                        aboutText = ch.aboutText.ifBlank { existing.aboutText },
                        isMuted = ch.isMuted || existing.isMuted,
                        lastMessagePreview = existing.lastMessagePreview.ifBlank { ch.aboutText }
                    )
                )
            }
        }
    }

    @Transaction
    suspend fun upsertSyncedGroups(groups: List<ChatEntity>) {
        for (group in groups) {
            val existing = getChat(group.jid)
            if (existing == null) {
                upsertChat(group)
            } else {
                val jidUser = group.jid.substringBefore("@")
                val resolvedName = if (group.name.isNotBlank() && group.name != group.jid && group.name != jidUser) {
                    group.name
                } else {
                    existing.name
                }
                upsertChat(
                    existing.copy(
                        name = resolvedName,
                        aboutText = group.aboutText.ifBlank { existing.aboutText },
                        isGroup = true,
                        isCommunityParent = group.isCommunityParent || existing.isCommunityParent,
                        parentGroupJid = group.parentGroupJid.ifBlank { existing.parentGroupJid },
                        isDefaultSubgroup = group.isDefaultSubgroup || existing.isDefaultSubgroup,
                        ephemeralDuration = if (group.ephemeralDuration > 0) group.ephemeralDuration else existing.ephemeralDuration
                    )
                )
            }
        }
    }

    @Query("SELECT * FROM chats WHERE last_message_preview = '' OR last_message_type IN ('UNKNOWN', '')")
    suspend fun getChatsWithBlankPreview(): List<ChatEntity>

    @Query("SELECT * FROM messages WHERE chat_jid = :chatJid ORDER BY timestamp DESC, rowid DESC LIMIT 1")
    suspend fun getLatestMessageForChat(chatJid: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE chat_jid = :chatJid AND timestamp > :afterTimestamp ORDER BY timestamp ASC LIMIT 1")
    suspend fun getNextNewerMessage(chatJid: String, afterTimestamp: Long): MessageEntity?

    @Query("UPDATE chats SET avatar_path = :avatarPath WHERE jid = :jid")
    suspend fun updateChatAvatar(jid: String, avatarPath: String)

    @Query(
        """
        SELECT sender_name FROM messages
        WHERE chat_jid = :chatJid
          AND is_from_me = 0
          AND sender_name != ''
          AND sender_name != :chatJid
          AND sender_name != CASE
              WHEN instr(:chatJid, '@') > 1 THEN substr(:chatJid, 1, instr(:chatJid, '@') - 1)
              ELSE :chatJid
          END
        ORDER BY timestamp DESC, rowid DESC LIMIT 1
        """
    )
    suspend fun getLatestIncomingSenderName(chatJid: String): String?

    @Query(
        """
        UPDATE messages SET sender_name = :name
        WHERE sender_jid = :jid
          AND (sender_name = '' OR sender_name = :jid OR sender_name = :jidUser)
        """
    )
    suspend fun updateSenderNameForJid(jid: String, jidUser: String, name: String)

    @Query("UPDATE chats SET about_text = :aboutText WHERE jid = :jid")
    suspend fun updateChatAbout(jid: String, aboutText: String)

    @Query("SELECT * FROM messages WHERE chat_jid = :chatJid ORDER BY timestamp DESC, rowid DESC")
    fun pagingMessages(chatJid: String): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM messages WHERE chat_jid = :chatJid ORDER BY timestamp DESC, rowid DESC LIMIT :limit")
    fun observeRecentMessages(chatJid: String, limit: Int = 200): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE chat_jid = :chatJid
          AND is_view_once = 0
          AND (msg_type = 'IMAGE' OR msg_type = 'VIDEO')
          AND local_media_path IS NOT NULL
          AND local_media_path != ''
        ORDER BY timestamp ASC, rowid ASC
        """
    )
    fun observeChatGalleryMedia(chatJid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessage(messageId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE chat_jid = :chatJid")
    suspend fun countMessagesInChat(chatJid: String): Int

    @Query(
        """
        SELECT id FROM messages
        WHERE chat_jid = :chatJid AND is_from_me = 0 AND delivery_status != 'READ' AND delivery_status != 'PLAYED'
        ORDER BY timestamp DESC, rowid DESC
        LIMIT 25
        """
    )
    suspend fun getUnreadIncomingIds(chatJid: String): List<String>

    @Query(
        """
        SELECT id, sender_jid FROM messages
        WHERE chat_jid = :chatJid AND is_from_me = 0 AND delivery_status != 'READ' AND delivery_status != 'PLAYED'
        ORDER BY timestamp DESC, rowid DESC
        LIMIT 25
        """
    )
    suspend fun getUnreadIncomingTuples(chatJid: String): List<UnreadMessageSenderTuple>

    @Query(
        """
        SELECT id, sender_jid FROM messages
        WHERE chat_jid = :chatJid AND is_from_me = 0
        ORDER BY timestamp DESC, rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentIncomingTuplesForRead(chatJid: String, limit: Int = 15): List<UnreadMessageSenderTuple>

    @Query(
        """
        SELECT * FROM messages
        WHERE chat_jid = :chatJid
          AND msg_type IN ('PTT', 'AUDIO')
          AND is_view_once = 0
          AND view_once_state != 'STRIPPED'
          AND (local_media_path IS NULL OR local_media_path = '')
        ORDER BY timestamp DESC
        LIMIT 25
        """
    )
    suspend fun getUndownloadedAudioMessages(chatJid: String): List<MessageEntity>

    @Query("UPDATE messages SET delivery_status = 'READ' WHERE chat_jid = :chatJid AND is_from_me = 0 AND delivery_status != 'PLAYED'")
    suspend fun markIncomingLocallyRead(chatJid: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessagesIgnore(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query(
        """
        UPDATE messages
        SET local_media_path = :localPath,
            media_sha256 = :sha256,
            file_size = CASE WHEN :fileSize > 0 THEN :fileSize ELSE file_size END,
            waveform_csv = CASE WHEN :waveformCsv != '' THEN :waveformCsv ELSE waveform_csv END,
            view_once_state = CASE WHEN is_view_once = 1 THEN 'AVAILABLE' ELSE view_once_state END
        WHERE id = :messageId
        """
    )
    suspend fun updateMessageMedia(
        messageId: String,
        localPath: String,
        sha256: String,
        fileSize: Long,
        waveformCsv: String
    )

    @Query("UPDATE messages SET audio_transcript = :transcript WHERE id = :messageId")
    suspend fun updateAudioTranscript(messageId: String, transcript: String)

    @Query(
        """
        UPDATE messages SET delivery_status = :status
        WHERE id = :messageId
          AND (CASE delivery_status WHEN 'PLAYED' THEN 4 WHEN 'READ' THEN 3 WHEN 'DELIVERED' THEN 2 WHEN 'SENT' THEN 1 ELSE 0 END) < :newRank
        """
    )
    suspend fun updateMessageDeliveryStatusIfHigher(messageId: String, status: String, newRank: Int)

    @Query(
        """
        UPDATE chats SET last_message_status = :status
        WHERE last_message_id = :messageId
          AND (CASE last_message_status WHEN 'PLAYED' THEN 4 WHEN 'READ' THEN 3 WHEN 'DELIVERED' THEN 2 WHEN 'SENT' THEN 1 ELSE 0 END) < :newRank
        """
    )
    suspend fun updateChatLastMessageStatusIfHigher(messageId: String, status: String, newRank: Int)

    @Query(
        """
        UPDATE messages SET delivery_status = :status
        WHERE chat_jid = :chatJid
          AND is_from_me = 1
          AND timestamp <= :maxTs
          AND (CASE delivery_status WHEN 'PLAYED' THEN 4 WHEN 'READ' THEN 3 WHEN 'DELIVERED' THEN 2 WHEN 'SENT' THEN 1 ELSE 0 END) < :newRank
        """
    )
    suspend fun markDmOutgoingStatusUpTo(chatJid: String, maxTs: Long, status: String, newRank: Int)

    @Query(
        """
        UPDATE chats SET last_message_status = :status
        WHERE jid = :chatJid
          AND last_message_from_me = 1
          AND last_message_timestamp <= :maxTs
          AND (CASE last_message_status WHEN 'PLAYED' THEN 4 WHEN 'READ' THEN 3 WHEN 'DELIVERED' THEN 2 WHEN 'SENT' THEN 1 ELSE 0 END) < :newRank
        """
    )
    suspend fun markDmChatLastStatusIfHigher(chatJid: String, maxTs: Long, status: String, newRank: Int)

    @Transaction
    suspend fun updateDeliveryStatus(messageId: String, status: String) {
        val rank = deliveryRank(status)
        updateMessageDeliveryStatusIfHigher(messageId, status, rank)
        updateChatLastMessageStatusIfHigher(messageId, status, rank)
    }

    @Query("UPDATE messages SET reaction_summary = :summary WHERE id = :messageId")
    suspend fun updateReactionSummary(messageId: String, summary: String)

    @Query("SELECT * FROM message_reactions WHERE message_id = :messageId ORDER BY updated_at ASC, actor ASC")
    suspend fun getReactionsFor(messageId: String): List<MessageReactionEntity>

    @Query("SELECT emoji FROM message_reactions WHERE message_id = :messageId AND actor = 'me' LIMIT 1")
    suspend fun getMyReaction(messageId: String): String?

    @Query("SELECT emoji FROM message_reactions WHERE message_id = :messageId AND actor = 'me' LIMIT 1")
    fun observeMyReaction(messageId: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReaction(reaction: MessageReactionEntity)

    @Query("DELETE FROM message_reactions WHERE message_id = :messageId AND actor = :actor")
    suspend fun deleteReaction(messageId: String, actor: String)

    @Query("SELECT reaction_summary FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getReactionSummary(messageId: String): String?

    @Query("DELETE FROM message_reactions WHERE message_id NOT IN (SELECT id FROM messages)")
    suspend fun deleteOrphanReactions()

    /**
     * Applies one actor's reaction (blank [emoji] removes it) and rebuilds the message's
     * `reaction_summary` from the per-actor rows. A pre-existing summary that has no per-actor
     * rows yet is preserved as `legacy:` rows so older reactions are never lost.
     */
    @Transaction
    suspend fun applyReaction(messageId: String, actor: String, emoji: String, timestamp: Long) {
        if (getReactionsFor(messageId).isEmpty()) {
            getReactionSummary(messageId).orEmpty()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { token ->
                    upsertReaction(
                        MessageReactionEntity(
                            messageId = messageId,
                            actor = MessageReactionEntity.LEGACY_PREFIX + token,
                            emoji = token,
                            updatedAt = 0L
                        )
                    )
                }
        }
        val clean = emoji.trim()
        if (clean.isEmpty()) {
            deleteReaction(messageId, actor)
        } else {
            // A legacy token with the same emoji is assumed to be this reaction's earlier copy.
            if (actor == MessageReactionEntity.ME_ACTOR) {
                deleteReaction(messageId, MessageReactionEntity.LEGACY_PREFIX + clean)
            }
            upsertReaction(
                MessageReactionEntity(
                    messageId = messageId,
                    actor = actor,
                    emoji = clean,
                    updatedAt = timestamp
                )
            )
        }
        val summary = getReactionsFor(messageId)
            .map { it.emoji }
            .distinct()
            .joinToString(" ")
        updateReactionSummary(messageId, summary)
    }

    @Query(
        """
        SELECT messages.* FROM messages
        JOIN messages_fts ON messages.rowid = messages_fts.rowid
        WHERE messages_fts MATCH :ftsQuery
        ORDER BY messages.timestamp DESC
        LIMIT 80
        """
    )
    suspend fun searchMessagesFts(ftsQuery: String): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE chat_jid = :chatJid AND (body LIKE '%' || :query || '%' OR audio_transcript LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        LIMIT 5000
        """
    )
    suspend fun searchInChat(chatJid: String, query: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE is_view_once = 1 ORDER BY timestamp DESC")
    fun observeViewOnceVault(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message_edits WHERE message_id = :messageId ORDER BY edited_at DESC")
    fun observeMessageEdits(messageId: String): Flow<List<MessageEditEntity>>

    @Query("SELECT * FROM message_edits WHERE message_id = :messageId ORDER BY edited_at DESC")
    suspend fun getMessageEditsOnce(messageId: String): List<MessageEditEntity>

    @Insert
    suspend fun insertMessageEdit(edit: MessageEditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStatus(status: StatusEntity)

    @Query("SELECT * FROM statuses ORDER BY timestamp DESC")
    fun observeStatuses(): Flow<List<StatusEntity>>

    @Query("SELECT * FROM statuses WHERE id = :statusId LIMIT 1")
    suspend fun getStatusById(statusId: String): StatusEntity?

    @Query("UPDATE statuses SET local_media_path = :localPath WHERE id = :statusId")
    suspend fun updateStatusMedia(statusId: String, localPath: String)

    @Query("UPDATE statuses SET is_revoked = 1, revoked_at = :revokedAt WHERE id = :statusId")
    suspend fun markStatusRevoked(statusId: String, revokedAt: Long)

    @Query("UPDATE statuses SET sender_jid = :canonicalJid WHERE sender_jid = :duplicateJid")
    suspend fun reassignStatusSenderJid(canonicalJid: String, duplicateJid: String)

    @Query(
        """
        UPDATE statuses SET sender_name = :name
        WHERE (
            sender_jid = :jid
            OR (:jidUser != '' AND (sender_jid LIKE :jidUser || '@%' OR sender_jid LIKE :jidUser || ':%@%'))
          )
          AND (
            sender_name = ''
            OR sender_name = 'Status'
            OR sender_name = sender_jid
            OR (:jidUser != '' AND (sender_name = :jidUser OR sender_name = '+' || :jidUser))
            OR (sender_name LIKE '+%' AND :name NOT LIKE '+%')
            OR :overwrite = 1
          )
        """
    )
    suspend fun updateStatusSenderNameForJid(
        jid: String,
        jidUser: String,
        name: String,
        overwrite: Boolean = false
    )

    @Insert
    suspend fun insertPresenceLog(log: PresenceLogEntity)

    @Query("SELECT * FROM presence_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeAllPresenceLogs(): Flow<List<PresenceLogEntity>>

    @Query("SELECT * FROM presence_logs WHERE jid = :jid ORDER BY timestamp DESC LIMIT 100")
    fun observePresenceLogsForJid(jid: String): Flow<List<PresenceLogEntity>>

    @Insert
    suspend fun insertProfileHistory(entry: ProfileHistoryEntity)

    @Query("SELECT * FROM profile_history ORDER BY captured_at DESC LIMIT 150")
    fun observeAllProfileHistory(): Flow<List<ProfileHistoryEntity>>

    @Query("SELECT * FROM profile_history WHERE jid = :jid ORDER BY captured_at DESC")
    fun observeProfileHistoryForJid(jid: String): Flow<List<ProfileHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrivacyRule(rule: PrivacyRuleEntity)

    @Query("SELECT * FROM privacy_rules WHERE jid = :jid LIMIT 1")
    fun observePrivacyRule(jid: String): Flow<PrivacyRuleEntity?>

    @Query("SELECT * FROM privacy_rules WHERE jid = :jid LIMIT 1")
    suspend fun getPrivacyRuleOnce(jid: String): PrivacyRuleEntity?

    @Query("SELECT * FROM privacy_rules")
    fun observeAllPrivacyRules(): Flow<List<PrivacyRuleEntity>>

    @Query("DELETE FROM privacy_rules WHERE jid = :jid")
    suspend fun deletePrivacyRule(jid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroupReceipt(receipt: GroupReceiptEntity)

    @Query("SELECT * FROM group_receipts WHERE message_id = :messageId ORDER BY timestamp DESC")
    fun observeGroupReceipts(messageId: String): Flow<List<GroupReceiptEntity>>

    @Query("UPDATE messages SET reaction_summary = :summary WHERE id = :messageId AND reaction_summary = ''")
    suspend fun fillReactionSummaryIfBlank(messageId: String, summary: String)

    @Query("UPDATE messages SET chat_jid = :canonicalJid WHERE chat_jid = :duplicateJid")
    suspend fun reassignMessagesChatJid(canonicalJid: String, duplicateJid: String): Int

    @Query("UPDATE profile_history SET jid = :canonicalJid WHERE jid = :duplicateJid")
    suspend fun reassignProfileHistoryJid(canonicalJid: String, duplicateJid: String)

    @Transaction
    suspend fun mergeDuplicateChatAlias(canonicalJid: String, duplicateJid: String) {
        if (canonicalJid.isBlank() || duplicateJid.isBlank() || canonicalJid == duplicateJid) return
        if (canonicalJid.endsWith("@g.us") || canonicalJid.endsWith("@newsletter") || canonicalJid == "status@broadcast") return
        if (duplicateJid.endsWith("@g.us") || duplicateJid.endsWith("@newsletter") || duplicateJid == "status@broadcast") return

        val duplicate = getChat(duplicateJid)
        val canonical = getChat(canonicalJid)
        val dupContact = getContact(duplicateJid)
        if (dupContact != null) {
            upsertContacts(listOf(dupContact.copy(jid = canonicalJid)))
            deleteContactRow(duplicateJid)
        }
        if (duplicate == null && canonical == null) return

        reassignMessagesChatJid(canonicalJid, duplicateJid)
        reassignProfileHistoryJid(canonicalJid, duplicateJid)
        reassignStatusSenderJid(canonicalJid, duplicateJid)

        val dupPrivacy = getPrivacyRuleOnce(duplicateJid)
        if (dupPrivacy != null) {
            if (getPrivacyRuleOnce(canonicalJid) == null) {
                upsertPrivacyRule(dupPrivacy.copy(jid = canonicalJid))
            }
            deletePrivacyRule(duplicateJid)
        }

        if (duplicate == null) return

        fun isRealName(name: String, jid: String): Boolean {
            val bare = jid.substringBefore("@")
            return name.isNotBlank() && name != jid && name != bare && !name.startsWith("+")
        }

        if (canonical == null) {
            upsertChat(duplicate.copy(jid = canonicalJid))
            deleteChatRow(duplicateJid)
            return
        }

        val resolvedName = when {
            isRealName(canonical.name, canonicalJid) -> canonical.name
            isRealName(duplicate.name, duplicateJid) -> duplicate.name
            canonical.name.isNotBlank() -> canonical.name
            else -> duplicate.name
        }
        val resolvedAvatar = canonical.avatarPath?.takeIf { it.isNotBlank() }
            ?: duplicate.avatarPath?.takeIf { it.isNotBlank() }
        val resolvedAbout = canonical.aboutText.ifBlank { duplicate.aboutText }
        val dupIsNewer = duplicate.lastMessageTimestamp >= canonical.lastMessageTimestamp &&
            duplicate.lastMessagePreview.isNotBlank()
        val latestMsg = getLatestMessageForChat(canonicalJid)
        val mergedLastId = latestMsg?.id
            ?: if (dupIsNewer) duplicate.lastMessageId else canonical.lastMessageId
        val mergedPreview = latestMsg?.let { previewTextFor(it) }
            ?: if (dupIsNewer) duplicate.lastMessagePreview else canonical.lastMessagePreview
        val mergedType = latestMsg?.let { if (it.isViewOnce) "VIEW_ONCE_${it.msgType}" else it.msgType }
            ?: if (dupIsNewer) duplicate.lastMessageType else canonical.lastMessageType
        val mergedFromMe = latestMsg?.isFromMe
            ?: if (dupIsNewer) duplicate.lastMessageFromMe else canonical.lastMessageFromMe
        val mergedStatus = latestMsg?.deliveryStatus
            ?: if (dupIsNewer) duplicate.lastMessageStatus else canonical.lastMessageStatus
        val mergedIsDeleted = latestMsg?.isDeletedBySender
            ?: (canonical.lastMessageIsDeleted || duplicate.lastMessageIsDeleted)
        val mergedTs = maxOf(
            canonical.lastMessageTimestamp,
            duplicate.lastMessageTimestamp,
            latestMsg?.timestamp ?: 0L
        )
        val isOpen = com.wupp.meow.core.service.WaRealtimeService.activeChatJid.value == canonicalJid
        val mergedOverride = when {
            canonical.localArchiveOverride != 0 -> canonical.localArchiveOverride
            else -> duplicate.localArchiveOverride
        }
        val mergedArchived = when (mergedOverride) {
            1 -> true
            -1 -> false
            else -> canonical.isArchived && duplicate.isArchived
        }
        upsertChat(
            canonical.copy(
                name = resolvedName,
                avatarPath = resolvedAvatar,
                aboutText = resolvedAbout,
                unreadCount = if (isOpen) 0 else (canonical.unreadCount + duplicate.unreadCount),
                isArchived = mergedArchived,
                isLocked = canonical.isLocked || duplicate.isLocked,
                localArchiveOverride = mergedOverride,
                isPinned = canonical.isPinned || duplicate.isPinned,
                pinOrder = maxOf(canonical.pinOrder, duplicate.pinOrder),
                isMuted = canonical.isMuted || duplicate.isMuted,
                muteEndTime = maxOf(canonical.muteEndTime, duplicate.muteEndTime),
                isFavorite = canonical.isFavorite || duplicate.isFavorite,
                lastMessageId = mergedLastId,
                lastMessagePreview = mergedPreview,
                lastMessageType = mergedType,
                lastMessageFromMe = mergedFromMe,
                lastMessageStatus = mergedStatus,
                lastMessageIsDeleted = mergedIsDeleted,
                lastMessageTimestamp = mergedTs,
                hasDeletedMessages = canonical.hasDeletedMessages || duplicate.hasDeletedMessages,
                hasEditedMessages = canonical.hasEditedMessages || duplicate.hasEditedMessages,
                ephemeralDuration = maxOf(canonical.ephemeralDuration, duplicate.ephemeralDuration)
            )
        )
        deleteChatRow(duplicateJid)
    }

    @Transaction
    suspend fun insertHistorySyncBatch(chats: List<ChatEntity>, messages: List<MessageEntity>) {
        val openChatJid = com.wupp.meow.core.service.WaRealtimeService.activeChatJid.value
        val chatJidsWithMessages = messages.asSequence().map { it.chatJid }.toHashSet()
        for (chat in chats) {
            if (chat.jid == "status@broadcast" || chat.jid.endsWith("@broadcast")) continue
            val isCurrentlyOpen = openChatJid == chat.jid
            val existing = getChat(chat.jid)
            if (existing == null) {
                val isGroupOrChannel = chat.isGroup || chat.isCommunityParent || chat.jid.endsWith("@newsletter")
                if (!isGroupOrChannel && !chatJidsWithMessages.contains(chat.jid)) {
                    continue
                }
                upsertChat(if (isCurrentlyOpen) chat.copy(unreadCount = 0) else chat)
            } else {
                val jidUser = chat.jid.substringBefore("@")
                val incomingIsCustom = chat.name.isNotBlank() &&
                    chat.name != jidUser &&
                    chat.name != chat.jid &&
                    !chat.name.startsWith("+")
                val existingHasCustom = existing.name.isNotBlank() &&
                    existing.name != jidUser &&
                    existing.name != chat.jid &&
                    !existing.name.startsWith("+")
                val resolvedName = when {
                    incomingIsCustom -> chat.name
                    existingHasCustom -> existing.name
                    chat.name.isNotBlank() -> chat.name
                    else -> existing.name
                }
                val resolvedArchived = when (existing.localArchiveOverride) {
                    1 -> true
                    -1 -> false
                    else -> chat.isArchived
                }
                upsertChat(
                    existing.copy(
                        name = resolvedName,
                        isPinned = chat.isPinned || existing.isPinned,
                        isMuted = chat.isMuted || existing.isMuted,
                        isArchived = resolvedArchived,
                        isLocked = chat.isLocked || existing.isLocked,
                        localArchiveOverride = existing.localArchiveOverride,
                        unreadCount = if (isCurrentlyOpen) 0 else chat.unreadCount,
                        lastMessageTimestamp = maxOf(existing.lastMessageTimestamp, chat.lastMessageTimestamp),
                        isCommunityParent = chat.isCommunityParent || existing.isCommunityParent,
                        parentGroupJid = chat.parentGroupJid.ifBlank { existing.parentGroupJid },
                        isDefaultSubgroup = chat.isDefaultSubgroup || existing.isDefaultSubgroup
                    )
                )
            }
        }
        // Preserve existing rows that may already have local Anti-Delete or Edit flags
        messages.chunked(250).forEach { chunk ->
            insertMessagesIgnore(chunk)
        }
        // Heal / upgrade existing outgoing delivery statuses & backfill synced reactions from WebMessageInfo
        messages.forEach { m ->
            if (m.isFromMe) {
                val r = deliveryRank(m.deliveryStatus)
                if (r >= 2) {
                    updateMessageDeliveryStatusIfHigher(m.id, m.deliveryStatus, r)
                    updateChatLastMessageStatusIfHigher(m.id, m.deliveryStatus, r)
                }
            }
            if (m.reactionSummary.isNotBlank()) {
                fillReactionSummaryIfBlank(m.id, m.reactionSummary)
            }
        }
        // Ensure chat last message previews are refreshed for any newly inserted latest message
        val grouped = messages.groupBy { it.chatJid }
        for ((jid, list) in grouped) {
            val latest = list.maxByOrNull { it.timestamp } ?: continue
            val chat = getChat(jid)
            if (chat != null && (latest.timestamp >= chat.lastMessageTimestamp || chat.lastMessagePreview.isBlank())) {
                val upgradedStatus = if (deliveryRank(latest.deliveryStatus) >= deliveryRank(chat.lastMessageStatus)) {
                    latest.deliveryStatus
                } else {
                    chat.lastMessageStatus
                }
                upsertChat(
                    chat.copy(
                        lastMessageId = latest.id,
                        lastMessagePreview = previewTextFor(latest),
                        lastMessageType = if (latest.isViewOnce) "VIEW_ONCE_${latest.msgType}" else latest.msgType,
                        lastMessageFromMe = latest.isFromMe,
                        lastMessageStatus = upgradedStatus,
                        lastMessageTimestamp = maxOf(chat.lastMessageTimestamp, latest.timestamp)
                    )
                )
            }
        }
    }

    @Transaction
    suspend fun upsertIncomingMessageAndRefreshChat(
        message: MessageEntity,
        chatNameHint: String,
        isGroup: Boolean,
        incrementUnread: Boolean
    ): Boolean {
        val existing = getMessage(message.id)
        val isNewMessage = existing == null
        var effectiveMessage = message
        if (isNewMessage && message.timestamp > 0L) {
            val latestInChat = getLatestMessageForChat(message.chatJid)
            if (latestInChat != null &&
                message.timestamp <= latestInChat.timestamp &&
                message.timestamp / 1000L == latestInChat.timestamp / 1000L
            ) {
                effectiveMessage = message.copy(timestamp = latestInChat.timestamp + 1L)
            }
        }
        if (existing != null) {
            val existingHasFile = !existing.localMediaPath.isNullOrBlank() &&
                java.io.File(existing.localMediaPath).let { it.exists() && it.length() > 0L }
            val resolvedVo = effectiveMessage.isViewOnce || existing.isViewOnce
            val resolvedVoState = when {
                existingHasFile && resolvedVo -> "AVAILABLE"
                effectiveMessage.viewOnceState != "NONE" -> effectiveMessage.viewOnceState
                else -> existing.viewOnceState
            }
            // Preserve Anti-Delete, Edit History, Local Media, View-Once state, and Transcripts
            upsertMessage(
                effectiveMessage.copy(
                    timestamp = maxOf(existing.timestamp, effectiveMessage.timestamp),
                    isDeletedBySender = existing.isDeletedBySender,
                    deletedAt = existing.deletedAt,
                    isEdited = existing.isEdited,
                    editedAt = existing.editedAt,
                    isViewOnce = resolvedVo,
                    viewOnceState = resolvedVoState,
                    localMediaPath = effectiveMessage.localMediaPath ?: existing.localMediaPath,
                    waveformCsv = effectiveMessage.waveformCsv.ifBlank { existing.waveformCsv },
                    audioTranscript = effectiveMessage.audioTranscript ?: existing.audioTranscript,
                    reactionSummary = effectiveMessage.reactionSummary.ifBlank { existing.reactionSummary }
                )
            )
        } else {
            upsertMessage(effectiveMessage)
        }

        val existingChat = getChat(effectiveMessage.chatJid)
        if (effectiveMessage.chatJid == "status@broadcast" || effectiveMessage.chatJid.endsWith("@broadcast")) {
            return isNewMessage
        }
        val preview = previewTextFor(effectiveMessage)
        val effectiveType = if (effectiveMessage.isViewOnce) "VIEW_ONCE_${effectiveMessage.msgType}" else effectiveMessage.msgType
        val shouldBumpUnread = incrementUnread && isNewMessage && !effectiveMessage.isFromMe
        if (existingChat == null) {
            upsertChat(
                ChatEntity(
                    jid = effectiveMessage.chatJid,
                    name = chatNameHint.ifBlank { effectiveMessage.chatJid.substringBefore("@") },
                    isGroup = isGroup,
                    unreadCount = if (shouldBumpUnread) 1 else 0,
                    lastMessageId = effectiveMessage.id,
                    lastMessagePreview = preview,
                    lastMessageType = effectiveType,
                    lastMessageFromMe = effectiveMessage.isFromMe,
                    lastMessageStatus = effectiveMessage.deliveryStatus,
                    lastMessageIsDeleted = effectiveMessage.isDeletedBySender,
                    lastMessageTimestamp = effectiveMessage.timestamp,
                    hasDeletedMessages = effectiveMessage.isDeletedBySender,
                    hasEditedMessages = effectiveMessage.isEdited,
                    ephemeralDuration = effectiveMessage.ephemeralDuration
                )
            )
        } else {
            val shouldAdvancePreview = effectiveMessage.timestamp >= existingChat.lastMessageTimestamp
            val resolvedUnread = when {
                effectiveMessage.isFromMe && shouldAdvancePreview -> 0
                shouldBumpUnread -> existingChat.unreadCount + 1
                else -> existingChat.unreadCount
            }
            upsertChat(
                existingChat.copy(
                    name = if (existingChat.name.isBlank() || existingChat.name == existingChat.jid.substringBefore("@")) {
                        chatNameHint.ifBlank { existingChat.name }
                    } else existingChat.name,
                    unreadCount = resolvedUnread,
                    lastMessageId = if (shouldAdvancePreview) effectiveMessage.id else existingChat.lastMessageId,
                    lastMessagePreview = if (shouldAdvancePreview) preview else existingChat.lastMessagePreview,
                    lastMessageType = if (shouldAdvancePreview) effectiveType else existingChat.lastMessageType,
                    lastMessageFromMe = if (shouldAdvancePreview) effectiveMessage.isFromMe else existingChat.lastMessageFromMe,
                    lastMessageStatus = if (shouldAdvancePreview) effectiveMessage.deliveryStatus else existingChat.lastMessageStatus,
                    lastMessageIsDeleted = if (shouldAdvancePreview) effectiveMessage.isDeletedBySender else existingChat.lastMessageIsDeleted,
                    lastMessageTimestamp = maxOf(existingChat.lastMessageTimestamp, effectiveMessage.timestamp),
                    ephemeralDuration = if (effectiveMessage.ephemeralDuration > 0) effectiveMessage.ephemeralDuration else existingChat.ephemeralDuration
                )
            )
        }
        return isNewMessage
    }

    @Transaction
    suspend fun markMessageRevoked(targetId: String, deletedAt: Long) {
        val msg = getMessage(targetId) ?: return
        if (!msg.isDeletedBySender) {
            upsertMessage(
                msg.copy(
                    isDeletedBySender = true,
                    deletedAt = deletedAt
                )
            )
        }
        val chat = getChat(msg.chatJid)
        if (chat != null) {
            upsertChat(
                chat.copy(
                    hasDeletedMessages = true,
                    lastMessageIsDeleted = if (chat.lastMessageId == targetId) true else chat.lastMessageIsDeleted
                )
            )
        }
    }

    /**
     * Permanently removes a single message and its metadata from the local database,
     * recalculates the parent chat preview/flags, and returns any now-unreferenced local media path.
     */
    @Transaction
    suspend fun deleteMessagePermanentlyFromDb(messageId: String): String? {
        val existing = getMessage(messageId) ?: return null
        deleteMessageById(messageId)
        deleteEditsForMessage(messageId)
        deleteReceiptsForMessage(messageId)

        val chat = getChat(existing.chatJid)
        if (chat != null) {
            val latest = getLatestMessageForChat(existing.chatJid)
            val hasDeleted = hasAnyDeletedMessage(existing.chatJid)
            val hasEdited = hasAnyEditedMessage(existing.chatJid)
            val shouldRefreshPreview = chat.lastMessageId == messageId || chat.lastMessageId.isNullOrBlank()
            upsertChat(
                chat.copy(
                    hasDeletedMessages = hasDeleted,
                    hasEditedMessages = hasEdited,
                    lastMessageId = if (shouldRefreshPreview) latest?.id.orEmpty() else chat.lastMessageId,
                    lastMessagePreview = if (shouldRefreshPreview) latest?.let { previewTextFor(it) }.orEmpty() else chat.lastMessagePreview,
                    lastMessageType = if (shouldRefreshPreview) latest?.msgType ?: "TEXT" else chat.lastMessageType,
                    lastMessageFromMe = if (shouldRefreshPreview) latest?.isFromMe ?: false else chat.lastMessageFromMe,
                    lastMessageStatus = if (shouldRefreshPreview) latest?.deliveryStatus.orEmpty() else chat.lastMessageStatus,
                    lastMessageIsDeleted = if (shouldRefreshPreview) latest?.isDeletedBySender ?: false else chat.lastMessageIsDeleted,
                    lastMessageTimestamp = if (shouldRefreshPreview && latest != null) latest.timestamp else chat.lastMessageTimestamp
                )
            )
        }

        val mediaPath = existing.localMediaPath?.takeIf { it.isNotBlank() } ?: return null
        return if (countMediaPathReferences(mediaPath) == 0) mediaPath else null
    }

    @Transaction
    suspend fun applyMessageEdit(targetId: String, newText: String, editedAt: Long) {
        val msg = getMessage(targetId) ?: return
        if (msg.body == newText && msg.isEdited) return
        insertMessageEdit(
            MessageEditEntity(
                messageId = targetId,
                oldText = msg.body,
                editedAt = editedAt
            )
        )
        upsertMessage(
            msg.copy(
                body = newText,
                isEdited = true,
                editedAt = editedAt
            )
        )
        val chat = getChat(msg.chatJid)
        if (chat != null) {
            upsertChat(
                chat.copy(
                    hasEditedMessages = true,
                    lastMessagePreview = if (chat.lastMessageId == targetId) newText else chat.lastMessagePreview
                )
            )
        }
    }

    @Transaction
    suspend fun markViewOnceStrippedIfUndownloaded(messageId: String, mediaTypeHint: String = "") {
        val existing = getMessage(messageId) ?: return
        val hasLocalFile = !existing.localMediaPath.isNullOrBlank() &&
            java.io.File(existing.localMediaPath).let { it.exists() && it.length() > 0L }
        if (hasLocalFile) {
            if (!existing.isViewOnce || existing.viewOnceState != "AVAILABLE") {
                upsertMessage(existing.copy(isViewOnce = true, viewOnceState = "AVAILABLE"))
            }
            return
        }
        val resolvedType = when {
            existing.msgType in setOf("IMAGE", "VIDEO", "PTT", "AUDIO") -> existing.msgType
            mediaTypeHint in setOf("IMAGE", "VIDEO", "PTT", "AUDIO") -> mediaTypeHint
            else -> "VIEW_ONCE_STRIPPED"
        }
        val updated = existing.copy(
            msgType = resolvedType,
            isViewOnce = true,
            viewOnceState = "STRIPPED"
        )
        upsertMessage(updated)
        val chat = getChat(existing.chatJid)
        if (chat != null && chat.lastMessageId == messageId) {
            upsertChat(
                chat.copy(
                    lastMessagePreview = previewTextFor(updated),
                    lastMessageType = "VIEW_ONCE_$resolvedType"
                )
            )
        }
    }

    companion object {
        fun deliveryRank(status: String): Int = when (status.uppercase()) {
            "PLAYED" -> 4
            "READ" -> 3
            "DELIVERED" -> 2
            "SENT", "LOCAL_SENT" -> 1
            else -> 0
        }

        fun previewTextFor(msg: MessageEntity): String {
            val voStripped = (msg.viewOnceState == "STRIPPED" || msg.msgType == "VIEW_ONCE_STRIPPED") &&
                msg.localMediaPath.isNullOrBlank()
            return when {
                msg.isDeletedBySender -> "🚫 ${msg.body.ifBlank { "Deleted message" }}"
                voStripped -> when (msg.msgType) {
                    "VIDEO" -> "Opened · Video"
                    "AUDIO", "PTT" -> "Opened · Voice message"
                    "IMAGE" -> "Opened · Photo"
                    else -> "Opened"
                }
                msg.isViewOnce -> when (msg.msgType) {
                    "VIDEO" -> "Video"
                    "AUDIO", "PTT" -> "Voice message"
                    else -> "Photo"
                }
                msg.msgType == "PTT" -> "🎤 Voice message (${formatDurationShort(msg.durationSec)})"
                msg.msgType == "AUDIO" -> "🎵 Audio (${formatDurationShort(msg.durationSec)})"
                msg.msgType == "CALL" -> "📞 ${msg.body.replace("|", " · ").ifBlank { "Voice call" }}"
                msg.msgType == "IMAGE" -> msg.body.ifBlank { "📷 Photo" }
                msg.msgType == "VIDEO" -> msg.body.ifBlank { "🎥 Video" }
                msg.msgType == "DOCUMENT" -> msg.body.ifBlank { "📄 Document" }
                msg.msgType == "STICKER" -> "💟 Sticker"
                else -> msg.body
            }
        }

        private fun formatDurationShort(sec: Int): String {
            val s = sec.coerceAtLeast(0)
            return "%d:%02d".format(s / 60, s % 60)
        }
    }
}
