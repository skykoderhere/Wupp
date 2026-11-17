package com.wupp.meow.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    @ColumnInfo(name = "jid")
    val jid: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "avatar_path")
    val avatarPath: String? = null,
    @ColumnInfo(name = "about_text")
    val aboutText: String = "",
    @ColumnInfo(name = "is_group")
    val isGroup: Boolean = false,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "pin_order")
    val pinOrder: Long = 0L,
    @ColumnInfo(name = "is_muted")
    val isMuted: Boolean = false,
    @ColumnInfo(name = "mute_end_time")
    val muteEndTime: Long = 0L,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "unread_count")
    val unreadCount: Int = 0,
    @ColumnInfo(name = "last_message_id")
    val lastMessageId: String? = null,
    @ColumnInfo(name = "last_message_preview")
    val lastMessagePreview: String = "",
    @ColumnInfo(name = "last_message_type")
    val lastMessageType: String = "TEXT",
    @ColumnInfo(name = "last_message_from_me")
    val lastMessageFromMe: Boolean = false,
    @ColumnInfo(name = "last_message_status")
    val lastMessageStatus: String = "SENT",
    @ColumnInfo(name = "last_message_is_deleted")
    val lastMessageIsDeleted: Boolean = false,
    @ColumnInfo(name = "last_message_timestamp")
    val lastMessageTimestamp: Long = 0L,
    @ColumnInfo(name = "has_deleted_messages")
    val hasDeletedMessages: Boolean = false,
    @ColumnInfo(name = "has_edited_messages")
    val hasEditedMessages: Boolean = false,
    @ColumnInfo(name = "is_online")
    val isOnline: Boolean = false,
    @ColumnInfo(name = "last_seen")
    val lastSeen: Long = 0L,
    @ColumnInfo(name = "chat_presence")
    val chatPresence: String = "",
    @ColumnInfo(name = "ephemeral_duration")
    val ephemeralDuration: Int = 0,
    @ColumnInfo(name = "is_community_parent", defaultValue = "0")
    val isCommunityParent: Boolean = false,
    @ColumnInfo(name = "parent_group_jid", defaultValue = "''")
    val parentGroupJid: String = "",
    @ColumnInfo(name = "is_default_subgroup", defaultValue = "0")
    val isDefaultSubgroup: Boolean = false,
    @ColumnInfo(name = "is_locked", defaultValue = "0")
    val isLocked: Boolean = false,
    @ColumnInfo(name = "local_archive_override", defaultValue = "0")
    val localArchiveOverride: Int = 0
) {
    /** True for empty DM stubs with no preview or message ID (e.g. unchatted @lid contacts). */
    fun isPhantomEmptyDm(): Boolean =
        !isGroup &&
            !isCommunityParent &&
            !jid.endsWith("@newsletter") &&
            lastMessagePreview.isBlank() &&
            lastMessageId.isNullOrBlank()
}

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chat_jid", "timestamp"]),
        Index(value = ["is_view_once", "timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "chat_jid")
    val chatJid: String,
    @ColumnInfo(name = "sender_jid")
    val senderJid: String,
    @ColumnInfo(name = "sender_name")
    val senderName: String = "",
    @ColumnInfo(name = "is_from_me")
    val isFromMe: Boolean = false,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "body")
    val body: String = "",
    @ColumnInfo(name = "msg_type")
    val msgType: String = "TEXT",
    @ColumnInfo(name = "mime_type")
    val mimeType: String = "",
    @ColumnInfo(name = "local_media_path")
    val localMediaPath: String? = null,
    @ColumnInfo(name = "media_sha256")
    val mediaSha256: String? = null,
    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0L,
    @ColumnInfo(name = "duration_sec")
    val durationSec: Int = 0,
    @ColumnInfo(name = "waveform_csv")
    val waveformCsv: String = "",
    @ColumnInfo(name = "audio_transcript")
    val audioTranscript: String? = null,
    @ColumnInfo(name = "delivery_status")
    val deliveryStatus: String = "SENT", // PENDING, SENT, DELIVERED, READ, PLAYED
    @ColumnInfo(name = "is_deleted_by_sender")
    val isDeletedBySender: Boolean = false,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long = 0L,
    @ColumnInfo(name = "is_edited")
    val isEdited: Boolean = false,
    @ColumnInfo(name = "edited_at")
    val editedAt: Long = 0L,
    @ColumnInfo(name = "is_view_once")
    val isViewOnce: Boolean = false,
    @ColumnInfo(name = "view_once_state")
    val viewOnceState: String = "NONE", // NONE, AVAILABLE, STRIPPED
    @ColumnInfo(name = "is_ephemeral")
    val isEphemeral: Boolean = false,
    @ColumnInfo(name = "ephemeral_duration")
    val ephemeralDuration: Int = 0,
    @ColumnInfo(name = "ephemeral_start")
    val ephemeralStart: Long = 0L,
    @ColumnInfo(name = "quoted_id")
    val quotedId: String? = null,
    @ColumnInfo(name = "quoted_sender")
    val quotedSender: String? = null,
    @ColumnInfo(name = "quoted_body")
    val quotedBody: String? = null,
    @ColumnInfo(name = "mentions_csv")
    val mentionsCsv: String = "",
    @ColumnInfo(name = "reaction_summary")
    val reactionSummary: String = ""
)

@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "audio_transcript")
    val audioTranscript: String?
)

/**
 * One row per (message, actor) so a participant switching or removing their reaction only
 * affects their own entry. `actor` is [MessageReactionEntity.ME_ACTOR] for this account.
 */
@Entity(
    tableName = "message_reactions",
    primaryKeys = ["message_id", "actor"],
    indices = [Index(value = ["message_id"])]
)
data class MessageReactionEntity(
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "actor")
    val actor: String,
    @ColumnInfo(name = "emoji")
    val emoji: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    companion object {
        const val ME_ACTOR = "me"
        const val LEGACY_PREFIX = "legacy:"
    }
}

@Entity(
    tableName = "message_edits",
    indices = [Index(value = ["message_id", "edited_at"])]
)
data class MessageEditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "old_text")
    val oldText: String,
    @ColumnInfo(name = "edited_at")
    val editedAt: Long
)

@Entity(
    tableName = "statuses",
    indices = [Index(value = ["timestamp"])]
)
data class StatusEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "sender_jid")
    val senderJid: String,
    @ColumnInfo(name = "sender_name")
    val senderName: String = "",
    @ColumnInfo(name = "caption")
    val caption: String = "",
    @ColumnInfo(name = "msg_type")
    val msgType: String = "IMAGE",
    @ColumnInfo(name = "mime_type")
    val mimeType: String = "",
    @ColumnInfo(name = "local_media_path")
    val localMediaPath: String? = null,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "is_revoked")
    val isRevoked: Boolean = false,
    @ColumnInfo(name = "revoked_at")
    val revokedAt: Long = 0L
)

@Entity(
    tableName = "presence_logs",
    indices = [Index(value = ["jid", "timestamp"])]
)
data class PresenceLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "jid")
    val jid: String,
    @ColumnInfo(name = "contact_name")
    val contactName: String = "",
    @ColumnInfo(name = "state")
    val state: String, // ONLINE, OFFLINE
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)

@Entity(
    tableName = "profile_history",
    indices = [Index(value = ["jid", "captured_at"])]
)
data class ProfileHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "jid")
    val jid: String,
    @ColumnInfo(name = "contact_name")
    val contactName: String = "",
    @ColumnInfo(name = "avatar_path")
    val avatarPath: String? = null,
    @ColumnInfo(name = "about_text")
    val aboutText: String = "",
    @ColumnInfo(name = "captured_at")
    val capturedAt: Long
)

@Entity(tableName = "privacy_rules")
data class PrivacyRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "jid")
    val jid: String, // "__GLOBAL__" or specific contact/group JID
    @ColumnInfo(name = "send_read_receipts")
    val sendReadReceipts: Boolean = false,
    @ColumnInfo(name = "send_played_receipts")
    val sendPlayedReceipts: Boolean = false,
    @ColumnInfo(name = "send_chat_presence")
    val sendChatPresence: Boolean = false
)

@Entity(
    tableName = "group_receipts",
    primaryKeys = ["message_id", "participant_jid"]
)
data class GroupReceiptEntity(
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "participant_jid")
    val participantJid: String,
    @ColumnInfo(name = "participant_name")
    val participantName: String = "",
    @ColumnInfo(name = "status")
    val status: String, // DELIVERED, READ, PLAYED
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "jid")
    val jid: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "about_text", defaultValue = "''")
    val aboutText: String = "",
    @ColumnInfo(name = "avatar_path")
    val avatarPath: String? = null,
    @ColumnInfo(name = "is_saved", defaultValue = "0")
    val isSaved: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L
)
