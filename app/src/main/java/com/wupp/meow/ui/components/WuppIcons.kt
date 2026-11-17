package com.wupp.meow.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Unified Font Awesome & Material Design vector icon registry for Wupp.
 * Replaces cluttered custom XML drawables with crisp, consistently proportioned vectors.
 */
object WuppIcons {
    // Navigation & Chrome
    val ChatsFilled: ImageVector = Icons.Rounded.ChatBubble
    val ChatsOutline: ImageVector = Icons.Outlined.ChatBubbleOutline
    val GroupsFilled: ImageVector = Icons.Rounded.Groups
    val GroupsOutline: ImageVector = Icons.Outlined.Groups
    val CommunitiesOutline: ImageVector = Icons.Outlined.Groups
    val SettingsFilled: ImageVector = Icons.Rounded.Settings
    val YouProfile: ImageVector = Icons.Outlined.PersonOutline

    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val ChevronRight: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val ChevronDown: ImageVector = Icons.Rounded.KeyboardArrowDown
    val Search: ImageVector = Icons.Outlined.Search
    val SearchBold: ImageVector = Icons.Rounded.Search
    val Share: ImageVector = Icons.Outlined.Share
    val Edit: ImageVector = Icons.Outlined.Edit
    val MoreVert: ImageVector = Icons.Rounded.MoreVert
    val Close: ImageVector = Icons.Rounded.Close
    val Add: ImageVector = Icons.Rounded.Add
    val Check: ImageVector = Icons.Rounded.Check

    // Messaging & Media actions
    val Send: ImageVector = Icons.AutoMirrored.Rounded.Send
    val Mic: ImageVector = Icons.Rounded.Mic
    val Camera: ImageVector = Icons.Outlined.PhotoCamera
    val CameraFilled: ImageVector = Icons.Rounded.PhotoCamera
    val DeleteSweep: ImageVector = Icons.Outlined.DeleteSweep
    val DeleteOutline: ImageVector = Icons.Outlined.DeleteOutline
    val Mute: ImageVector = Icons.Outlined.VolumeOff
    val PinFilled: ImageVector = Icons.Rounded.PushPin
    val PinOutline: ImageVector = Icons.Outlined.PushPin

    // Settings rows (Font Awesome & Material Outlined consistency)
    val Wallet: ImageVector = Icons.Outlined.AccountBalanceWallet
    val Premium: ImageVector = Icons.Outlined.Diamond
    val LinkedDevices: ImageVector = Icons.Outlined.Devices
    val Key: ImageVector = Icons.Outlined.Key
    val Lock: ImageVector = Icons.Outlined.Lock
    val Lists: ImageVector = Icons.Outlined.ListAlt
    val Palette: ImageVector = Icons.Outlined.Palette
    val Broadcast: ImageVector = Icons.Outlined.Campaign
    val Calendar: ImageVector = Icons.Outlined.CalendarMonth
    val Notifications: ImageVector = Icons.Outlined.Notifications
    val Storage: ImageVector = Icons.Outlined.DataUsage
    val ShieldFamily: ImageVector = Icons.Outlined.FamilyRestroom
    val Shield: ImageVector = Icons.Outlined.Shield
    val ShieldFilled: ImageVector = Icons.Rounded.Shield
    val Accessibility: ImageVector = Icons.Outlined.AccessibilityNew
    val Language: ImageVector = Icons.Outlined.Language
    val Help: ImageVector = Icons.AutoMirrored.Outlined.HelpOutline
    val Info: ImageVector = Icons.Outlined.Info
    val Bug: ImageVector = Icons.Outlined.BugReport
    val People: ImageVector = Icons.Outlined.PeopleOutline
    val Science: ImageVector = Icons.Outlined.Science
    val Smartphone: ImageVector = Icons.Outlined.Smartphone
    val Tune: ImageVector = Icons.Outlined.Tune
    val StealthEyeOff: ImageVector = Icons.Outlined.VisibilityOff
    val QrCode: ImageVector = Icons.Outlined.QrCode2
    val History: ImageVector = Icons.Outlined.History
    val Folder: ImageVector = Icons.Outlined.Folder

    // Crisp custom Material / Font Awesome-proportioned vectors

    /** Double delivery/read check mark (24x18 viewport for natural 4:3 width proportion). */
    val DoubleCheck: ImageVector by lazy {
        ImageVector.Builder(
            name = "WuppDoubleCheck",
            defaultWidth = 24.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 18f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.15f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // First check
                moveTo(2.0f, 9.6f)
                lineTo(6.2f, 13.8f)
                lineTo(14.5f, 4.4f)
                // Second check (parallel offset to the right)
                moveTo(8.3f, 12.1f)
                lineTo(10.0f, 13.8f)
                lineTo(18.6f, 4.4f)
            }
        }.build()
    }

    /** Single sent check mark (18x18 viewport). */
    val SingleCheck: ImageVector by lazy {
        ImageVector.Builder(
            name = "WuppSingleCheck",
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 18f,
            viewportHeight = 18f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.2f, 9.5f)
                lineTo(7.1f, 13.4f)
                lineTo(14.8f, 4.6f)
            }
        }.build()
    }

    /** Pending / clock icon for unsent messages. */
    val ClockPending: ImageVector = Icons.Outlined.AccessTime

    /** Status ring with segmented circle outline (Font Awesome / WA Status ring). */
    val StatusRing: ImageVector by lazy {
        ImageVector.Builder(
            name = "WuppStatusRing",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Outer broken ring arcs
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                // Top-right arc
                moveTo(12f, 3.2f)
                arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20.6f, 10.3f)
                // Bottom arc
                moveTo(20.1f, 15.2f)
                arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.4f, 16.5f)
                // Top-left arc
                moveTo(3.4f, 12.8f)
                arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.6f, 3.9f)
            }
            // Inner dot
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 9.2f)
                arcTo(2.8f, 2.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 11.99f, 9.2f)
                close()
            }
        }.build()
    }

    /** View-Once dashed circle with centered '1'. */
    val ViewOnce: ImageVector by lazy {
        ImageVector.Builder(
            name = "WuppViewOnce",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Left solid semicircle + dashed right arc
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 3.5f)
                arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 20.5f)
                moveTo(15.2f, 4.2f)
                arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.8f, 7.2f)
                moveTo(20.3f, 10.3f)
                arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20.3f, 13.7f)
                moveTo(18.8f, 16.8f)
                arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.2f, 19.8f)
            }
            // Centered "1"
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.5f, 9.4f)
                lineTo(12.4f, 8.1f)
                lineTo(12.4f, 16.0f)
            }
        }.build()
    }
}
