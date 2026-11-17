package com.wupp.meow.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.wupp.meow.R
import com.wupp.meow.data.db.MessageEntity
import com.wupp.meow.domain.security.AppLockManager
import com.wupp.meow.ui.theme.WaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val DOC_EXTENSIONS = setOf(
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "txt", "csv", "rtf", "odt", "ods", "odp", "zip", "rar", "7z", "apk", "json", "xml"
)

private data class PdfFirstPagePreview(
    val bitmap: ImageBitmap,
    val pageCount: Int
)

/**
 * Returns true when [msg] represents a document attachment (PDF, Office file, archive, etc.).
 */
internal fun isDocumentMessage(msg: MessageEntity): Boolean {
    if (msg.isDeletedBySender || msg.isViewOnce || msg.msgType == "VIEW_ONCE_STRIPPED") return false
    val upper = msg.msgType.uppercase(Locale.US)
    if (upper == "DOCUMENT") return true
    val mime = msg.mimeType.lowercase(Locale.US)
    if (mime == "application/pdf" ||
        mime.startsWith("application/vnd.") ||
        mime == "application/msword" ||
        mime == "application/zip"
    ) {
        return true
    }
    if (upper in setOf("TEXT", "IMAGE", "VIDEO", "AUDIO", "PTT", "STICKER", "CALL", "SYSTEM")) {
        if (upper != "TEXT" || (msg.fileSize <= 0L && msg.localMediaPath.isNullOrBlank())) {
            return false
        }
    }
    val candidate = msg.body.trim()
    val ext = candidate.substringAfterLast('.', "").lowercase(Locale.US)
    return (msg.fileSize > 0L || !msg.localMediaPath.isNullOrBlank()) &&
        candidate.length <= 160 &&
        !candidate.contains('\n') &&
        ext in DOC_EXTENSIONS
}

internal fun isPdfDocument(msg: MessageEntity, localFile: File? = null): Boolean {
    if (msg.mimeType.contains("pdf", ignoreCase = true)) return true
    if (msg.body.trim().endsWith(".pdf", ignoreCase = true)) return true
    if (localFile != null && localFile.extension.equals("pdf", ignoreCase = true)) return true
    if (localFile != null && localFile.exists() && localFile.length() >= 5L) {
        return runCatching {
            val magic = ByteArray(5)
            localFile.inputStream().use { input -> input.read(magic) == 5 } &&
                String(magic, Charsets.US_ASCII) == "%PDF-"
        }.getOrDefault(false)
    }
    return false
}

internal fun resolveDocumentMimeType(msg: MessageEntity, localFile: File? = null): String {
    val rawMime = msg.mimeType.trim()
    if (rawMime.isNotBlank() && !rawMime.equals("application/octet-stream", ignoreCase = true)) {
        return rawMime
    }
    if (isPdfDocument(msg, localFile)) return "application/pdf"
    val bodyExt = msg.body.trim().substringAfterLast('.', "").lowercase(Locale.US)
    val fileExt = localFile?.extension?.lowercase(Locale.US).orEmpty()
    val ext = bodyExt.takeIf { it in DOC_EXTENSIONS } ?: fileExt
    if (ext.isNotBlank()) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
    }
    return rawMime.ifBlank { "application/octet-stream" }
}

internal fun resolveDocumentExtensionLabel(mimeType: String, fileName: String): String {
    val ext = fileName.trim().substringAfterLast('.', "").lowercase(Locale.US)
    return when {
        mimeType.contains("pdf", ignoreCase = true) || ext == "pdf" -> "PDF"
        ext in DOC_EXTENSIONS -> ext.uppercase(Locale.US)
        mimeType.contains("msword", ignoreCase = true) || mimeType.contains("wordprocessingml", ignoreCase = true) -> "DOCX"
        mimeType.contains("spreadsheetml", ignoreCase = true) || mimeType.contains("ms-excel", ignoreCase = true) -> "XLSX"
        mimeType.contains("presentationml", ignoreCase = true) || mimeType.contains("ms-powerpoint", ignoreCase = true) -> "PPTX"
        mimeType.contains("zip", ignoreCase = true) -> "ZIP"
        mimeType.startsWith("text/", ignoreCase = true) -> "TXT"
        else -> "FILE"
    }
}

internal fun resolveDocumentDisplayName(msg: MessageEntity, mimeType: String, localFile: File?): String {
    val body = msg.body.trim()
    if (body.isNotBlank()) return body
    val extLabel = resolveDocumentExtensionLabel(mimeType, localFile?.name.orEmpty()).lowercase(Locale.US)
    val safeExt = if (extLabel == "file") "bin" else extLabel
    return "Document.$safeExt"
}

internal fun buildDocumentExportFileName(msg: MessageEntity, localFile: File): String {
    val resolvedMime = resolveDocumentMimeType(msg, localFile)
    val extLabel = resolveDocumentExtensionLabel(resolvedMime, msg.body).lowercase(Locale.US)
    val fallbackExt = localFile.extension.ifBlank { if (extLabel == "file") "bin" else extLabel }
    val rawTitle = msg.body.trim()
        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .take(96)
    return when {
        rawTitle.isNotBlank() && rawTitle.contains('.') -> rawTitle
        rawTitle.isNotBlank() -> "$rawTitle.$fallbackExt"
        else -> "WA_DOCUMENT_${msg.id}.$fallbackExt"
    }
}

internal fun formatDocumentFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${max(1, kb.roundToInt())} KB"
    val mb = kb / 1024.0
    return if (mb >= 100.0) "${mb.roundToInt()} MB" else String.format(Locale.US, "%.1f MB", mb)
}

internal fun formatDocumentMetaLine(
    pageCount: Int?,
    fileSize: Long,
    extLabel: String,
    isDownloading: Boolean,
    errorMsg: String?,
    hasLocalFile: Boolean
): String {
    val sizeText = formatDocumentFileSize(fileSize)
    return when {
        isDownloading -> listOfNotNull(
            "Downloading...",
            sizeText.takeIf { it.isNotBlank() },
            extLabel
        ).joinToString(" · ")
        errorMsg != null && !hasLocalFile -> listOfNotNull(
            "Tap to retry",
            sizeText.takeIf { it.isNotBlank() },
            extLabel
        ).joinToString(" · ")
        else -> listOfNotNull(
            pageCount?.takeIf { it > 0 }?.let { if (it == 1) "1 page" else "$it pages" },
            sizeText.takeIf { it.isNotBlank() },
            extLabel
        ).joinToString(" · ")
    }
}

/**
 * Opens [localFile] in Android's native PDF/document viewer via [FileProvider].
 * Falls back cleanly to [onFallbackPdfViewer] when no external PDF reader is installed.
 */
fun openDocumentNatively(
    context: Context,
    msg: MessageEntity,
    localFile: File,
    onFallbackPdfViewer: (File) -> Unit
) {
    val isPdf = isPdfDocument(msg, localFile)
    val resolvedMime = resolveDocumentMimeType(msg, localFile)
    val cleanName = buildDocumentExportFileName(msg, localFile)

    // Stage under cacheDir with proper display filename + extension so external viewers recognize PDFs even if vaulted as .bin
    val stagedFile = runCatching {
        val previewDir = File(context.cacheDir, "doc_preview").apply { mkdirs() }
        val target = File(previewDir, cleanName)
        if (!target.exists() || target.length() != localFile.length()) {
            localFile.copyTo(target, overwrite = true)
        }
        target
    }.getOrDefault(localFile)

    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", stagedFile)
    }.getOrNull()

    if (uri == null) {
        if (isPdf) {
            onFallbackPdfViewer(localFile)
        } else {
            Toast.makeText(context, context.getString(R.string.chat_doc_no_viewer), Toast.LENGTH_SHORT).show()
        }
        return
    }

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, resolvedMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    AppLockManager.suspendNextBackgroundLock()
    try {
        context.startActivity(viewIntent)
    } catch (_: ActivityNotFoundException) {
        if (isPdf) {
            onFallbackPdfViewer(localFile)
        } else {
            Toast.makeText(context, context.getString(R.string.chat_doc_no_viewer), Toast.LENGTH_SHORT).show()
        }
    }
}

private fun renderPdfFirstPagePreview(file: File): PdfFirstPagePreview? {
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = renderer.pageCount
                if (count <= 0) return null
                renderer.openPage(0).use { page ->
                    val targetW = 720
                    val fullH = max(1, (targetW.toFloat() * page.height.toFloat() / max(1, page.width)).roundToInt())
                    // Render full page first at clean width, then crop upper banner (official WA document preview look)
                    val fullBmp = Bitmap.createBitmap(targetW, fullH, Bitmap.Config.ARGB_8888)
                    Canvas(fullBmp).drawColor(AndroidColor.WHITE)
                    page.render(fullBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val bannerH = minOf(fullH, (targetW * 0.44f).roundToInt())
                    val bannerBmp = if (bannerH < fullH) {
                        Bitmap.createBitmap(fullBmp, 0, 0, targetW, bannerH).also {
                            fullBmp.recycle()
                        }
                    } else {
                        fullBmp
                    }
                    PdfFirstPagePreview(bitmap = bannerBmp.asImageBitmap(), pageCount = count)
                }
            }
        }
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentMessageCard(
    msg: MessageEntity,
    localFile: File?,
    colors: WaColors,
    isDownloading: Boolean,
    errorMsg: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val resolvedMime = remember(msg.mimeType, msg.body, localFile?.absolutePath) {
        resolveDocumentMimeType(msg, localFile)
    }
    val isPdf = remember(msg.mimeType, msg.body, localFile?.absolutePath) {
        isPdfDocument(msg, localFile)
    }
    val extLabel = remember(resolvedMime, msg.body, localFile?.name) {
        resolveDocumentExtensionLabel(resolvedMime, msg.body.ifBlank { localFile?.name.orEmpty() })
    }
    val titleText = remember(msg.body, resolvedMime, localFile?.name) {
        resolveDocumentDisplayName(msg, resolvedMime, localFile)
    }
    val hasLocalFile = localFile != null

    val pdfPreview by produceState<PdfFirstPagePreview?>(
        initialValue = null,
        key1 = localFile?.absolutePath,
        key2 = localFile?.length() ?: 0L,
        key3 = isPdf
    ) {
        value = if (localFile != null && isPdf) {
            withContext(Dispatchers.IO) { renderPdfFirstPagePreview(localFile) }
        } else {
            null
        }
    }

    val effectiveSize = max(msg.fileSize, localFile?.length() ?: 0L)
    val metaLine = formatDocumentMetaLine(
        pageCount = pdfPreview?.pageCount,
        fileSize = effectiveSize,
        extLabel = extLabel,
        isDownloading = isDownloading,
        errorMsg = errorMsg,
        hasLocalFile = hasLocalFile
    )

    val badgeColor = when (extLabel) {
        "PDF" -> Color(0xFFE53935)
        "DOC", "DOCX" -> Color(0xFF1E88E5)
        "XLS", "XLSX", "CSV" -> Color(0xFF2E7D32)
        "PPT", "PPTX" -> Color(0xFFFB8C00)
        "ZIP", "RAR", "7Z" -> Color(0xFF8E24AA)
        else -> colors.accentGreen
    }
    val badgeIcon = when (extLabel) {
        "PDF" -> Icons.Rounded.PictureAsPdf
        "DOC", "DOCX", "TXT", "RTF", "ODT" -> Icons.Rounded.Description
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.quotedBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        pdfPreview?.let { preview ->
            Image(
                bitmap = preview.bitmap,
                contentDescription = titleText,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = extLabel,
                    tint = badgeColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metaLine,
                    color = if (errorMsg != null && !isDownloading && !hasLocalFile) colors.warningAmber else colors.textSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!hasLocalFile) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.pillInactiveBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        LoadingIndicator(
                            color = colors.accentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.chat_doc_download, extLabel),
                            tint = colors.accentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Built-in multi-page PDF fallback viewer rendered via Android's native [PdfRenderer]
 * when a device has no external PDF reader installed.
 */
@Composable
fun InAppPdfViewerDialog(
    file: File,
    title: String,
    colors: WaColors,
    onDismiss: () -> Unit
) {
    val holder = remember(file.absolutePath) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pfd to renderer
        }.getOrNull()
    }
    DisposableEffect(holder) {
        onDispose {
            runCatching { holder?.second?.close() }
            runCatching { holder?.first?.close() }
        }
    }
    val pageCount = holder?.second?.pageCount ?: 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.elevatedBar)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pageCount > 0) {
                        Text(
                            text = "$pageCount ${if (pageCount == 1) "page" else "pages"} · PDF",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (holder == null || pageCount <= 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Unable to preview PDF", color = Color.White.copy(alpha = 0.7f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(count = pageCount, key = { it }) { pageIndex ->
                        val pageBmp by produceState<ImageBitmap?>(initialValue = null, pageIndex, holder) {
                            value = withContext(Dispatchers.IO) {
                                runCatching {
                                    synchronized(holder) {
                                        holder.second.openPage(pageIndex).use { page ->
                                            val w = 1080
                                            val h = max(1, (w.toFloat() * page.height / max(1, page.width)).roundToInt())
                                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                            Canvas(bmp).drawColor(AndroidColor.WHITE)
                                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            bmp.asImageBitmap()
                                        }
                                    }
                                }.getOrNull()
                            }
                        }
                        val bmp = pageBmp
                        if (bmp != null) {
                            val aspect = (bmp.width.toFloat() / max(1, bmp.height)).coerceIn(0.45f, 2.2f)
                            Image(
                                bitmap = bmp,
                                contentDescription = "Page ${pageIndex + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspect)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A1A1A))
                            )
                        }
                    }
                }
            }
        }
    }
}
