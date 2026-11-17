package com.wupp.meow.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.wupp.meow.R
import com.wupp.meow.domain.security.AppLockManager
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class StatusComposerMode {
    TEXT,
    MEDIA
}

private val STATUS_BG_PALETTE = listOf(
    Color(0xFF1F384A),
    Color(0xFF1B5E20),
    Color(0xFF4A2545),
    Color(0xFF6B3A26),
    Color(0xFF283593),
    Color(0xFF263238)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusComposerSheet(
    initialMode: StatusComposerMode,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var textBody by remember { mutableStateOf("") }
    var selectedPaletteIndex by remember { mutableIntStateOf(0) }
    var pickedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var pickedMimeType by remember { mutableStateOf<String?>(null) }
    var mediaCaption by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isVideoSelected by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedMediaUri = uri
            pickedMimeType = context.contentResolver.getType(uri)
        }
    }

    LaunchedEffect(pickedMediaUri, pickedMimeType) {
        val uri = pickedMediaUri
        if (uri == null) {
            previewBitmap = null
            isVideoSelected = false
            return@LaunchedEffect
        }
        val mime = pickedMimeType.orEmpty()
        isVideoSelected = mime.startsWith("video/", ignoreCase = true)
        previewBitmap = withContext(Dispatchers.IO) {
            loadStatusMediaPreviewBitmap(context, uri, isVideoSelected)
        }
    }

    val noWaFallbackText = stringResource(R.string.status_composer_no_wa_fallback)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevatedBar,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.status_composer_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = mode == StatusComposerMode.TEXT,
                    onClick = { mode = StatusComposerMode.TEXT },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(stringResource(R.string.status_composer_text_tab)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF005C4B),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color(0xFF25D366),
                        containerColor = Color(0xFF202C33),
                        labelColor = Color(0xFFD1D7DB),
                        iconColor = Color(0xFF8696A0)
                    )
                )
                FilterChip(
                    selected = mode == StatusComposerMode.MEDIA,
                    onClick = { mode = StatusComposerMode.MEDIA },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(stringResource(R.string.status_composer_media_tab)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF005C4B),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color(0xFF25D366),
                        containerColor = Color(0xFF202C33),
                        labelColor = Color(0xFFD1D7DB),
                        iconColor = Color(0xFF8696A0)
                    )
                )
            }

            if (mode == StatusComposerMode.TEXT) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(STATUS_BG_PALETTE[selectedPaletteIndex])
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = textBody.ifBlank { stringResource(R.string.status_composer_placeholder) },
                        color = if (textBody.isBlank()) Color.White.copy(alpha = 0.55f) else Color.White,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    STATUS_BG_PALETTE.forEachIndexed { idx, color ->
                        val selected = idx == selectedPaletteIndex
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, colors.brightGreen, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                    }
                                )
                                .clickable {
                                    haptic()
                                    selectedPaletteIndex = idx
                                }
                        )
                    }
                }
                OutlinedTextField(
                    value = textBody,
                    onValueChange = { if (it.length <= 700) textBody = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.status_composer_placeholder),
                            color = Color(0xFF8696A0)
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF25D366),
                        unfocusedBorderColor = Color(0xFF2A3942),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF202C33),
                        unfocusedContainerColor = Color(0xFF202C33)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.appSurface)
                        .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
                        .clickable {
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isVideoSelected) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = stringResource(R.string.status_composer_pick_media),
                                color = Color(0xFFD1D7DB),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (pickedMediaUri != null) {
                    OutlinedTextField(
                        value = mediaCaption,
                        onValueChange = { if (it.length <= 500) mediaCaption = it },
                        placeholder = {
                            Text(
                                text = "Add a caption…",
                                color = Color(0xFF8696A0)
                            )
                        },
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF25D366),
                            unfocusedBorderColor = Color(0xFF2A3942),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF202C33),
                            unfocusedContainerColor = Color(0xFF202C33)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2A30))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = Color(0xFF25D366),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.status_composer_zero_risk_note),
                    color = Color(0xFFAEB8BF),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            val canShare = !isSharing && if (mode == StatusComposerMode.TEXT) {
                textBody.trim().isNotEmpty()
            } else {
                pickedMediaUri != null
            }

            Button(
                onClick = {
                    if (!canShare) return@Button
                    isSharing = true
                    scope.launch {
                        val launchedDirectly = if (mode == StatusComposerMode.TEXT) {
                            launchWhatsAppTextStatusShare(context, textBody.trim())
                        } else {
                            val uri = pickedMediaUri
                            if (uri != null) {
                                launchWhatsAppMediaStatusShare(
                                    context = context,
                                    sourceUri = uri,
                                    rawMimeType = pickedMimeType,
                                    caption = mediaCaption.trim()
                                )
                            } else {
                                true
                            }
                        }
                        if (!launchedDirectly) {
                            Toast.makeText(context, noWaFallbackText, Toast.LENGTH_SHORT).show()
                        }
                        isSharing = false
                        onDismiss()
                    }
                },
                enabled = canShare,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366),
                    contentColor = Color(0xFF072B17),
                    disabledContainerColor = Color(0xFF20382D),
                    disabledContentColor = Color(0xFF6B8576)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.status_composer_share_wa),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private suspend fun launchWhatsAppTextStatusShare(context: Context, text: String): Boolean =
    withContext(Dispatchers.Main) {
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startWhatsAppShareIntent(context, base)
    }

private suspend fun launchWhatsAppMediaStatusShare(
    context: Context,
    sourceUri: Uri,
    rawMimeType: String?,
    caption: String
): Boolean {
    val mime = rawMimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: "image/*"
    val shareUri = withContext(Dispatchers.IO) {
        val ext = when {
            mime.contains("mp4", ignoreCase = true) -> "mp4"
            mime.contains("png", ignoreCase = true) -> "png"
            mime.contains("webp", ignoreCase = true) -> "webp"
            mime.contains("gif", ignoreCase = true) -> "gif"
            mime.startsWith("video/", ignoreCase = true) -> "mp4"
            else -> "jpg"
        }
        runCatching {
            val tmpFile = File(context.cacheDir, "status_share_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tmpFile).use { output -> input.copyTo(output) }
            }
            if (tmpFile.exists() && tmpFile.length() > 0L) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
            } else {
                sourceUri
            }
        }.getOrDefault(sourceUri)
    }

    return withContext(Dispatchers.Main) {
        val base = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, shareUri)
            if (caption.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, caption)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startWhatsAppShareIntent(context, base)
    }
}

private fun startWhatsAppShareIntent(context: Context, baseIntent: Intent): Boolean {
    AppLockManager.suspendNextBackgroundLock()
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        try {
            val targeted = Intent(baseIntent).setPackage(pkg)
            context.startActivity(targeted)
            return true
        } catch (_: ActivityNotFoundException) {
            // Try next WhatsApp package or chooser
        }
    }
    runCatching {
        context.startActivity(Intent.createChooser(baseIntent, context.getString(R.string.status_composer_title)))
    }
    return false
}

private fun loadStatusMediaPreviewBitmap(context: Context, uri: Uri, isVideo: Boolean): Bitmap? {
    return runCatching {
        if (isVideo) {
            val tmp = File(context.cacheDir, "status_thumb_probe.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            val thumb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(tmp, Size(480, 480), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(tmp.absolutePath, 1)
            }
            tmp.delete()
            thumb
        } else {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            }
        }
    }.getOrNull()
}
