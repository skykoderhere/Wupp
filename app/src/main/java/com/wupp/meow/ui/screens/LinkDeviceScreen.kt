package com.wupp.meow.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.wupp.meow.R
import com.wupp.meow.core.service.BridgeStatus
import com.wupp.meow.ui.components.WuppIcons
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.LocalWaMonoFamily
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LinkDeviceScreen(
    bridgeStatus: BridgeStatus,
    onPairPhone: (String, (String) -> Unit) -> Unit,
    onRefreshConnection: () -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    val mono = LocalWaMonoFamily.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // 0 = QR code, 1 = Phone number
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }
    var phoneInput by rememberSaveable { mutableStateOf("") }
    var requestedCode by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingCode by remember { mutableStateOf(false) }

    val activePairCode = requestedCode?.takeIf { it.isNotBlank() }
        ?: bridgeStatus.latestPairCode?.takeIf { it.isNotBlank() }

    val qrBitmap = remember(bridgeStatus.latestQrCode) {
        bridgeStatus.latestQrCode?.takeIf { it.isNotBlank() }?.let { generateQrImage(it, 520) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.appSurface
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Spacer(modifier = Modifier.height(10.dp))

            Image(
                painter = painterResource(R.drawable.wupp_logo),
                contentDescription = "Wupp",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.link_title),
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.link_subtitle),
                color = colors.textSecondary,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.brightGreen.copy(alpha = 0.12f))
                    .border(1.dp, colors.brightGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colors.brightGreen,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.link_companion_notice),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Material 3 Expressive connected toggle button group (QR code vs Phone number)
            val toggleColors = ToggleButtonDefaults.colors(
                containerColor = colors.elevatedBar,
                contentColor = colors.textSecondary,
                checkedContainerColor = colors.pillActiveBg,
                checkedContentColor = colors.pillActiveText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                ToggleButton(
                    checked = selectedMode == 0,
                    onCheckedChange = {
                        if (it) {
                            haptic()
                            selectedMode = 0
                        }
                    },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    colors = toggleColors,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("link_mode_qr_button")
                ) {
                    Icon(
                        imageVector = WuppIcons.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.link_mode_qr),
                        fontSize = 13.5.sp,
                        fontWeight = if (selectedMode == 0) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
                ToggleButton(
                    checked = selectedMode == 1,
                    onCheckedChange = {
                        if (it) {
                            haptic()
                            selectedMode = 1
                        }
                    },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    colors = toggleColors,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("link_mode_phone_button")
                ) {
                    Icon(
                        imageVector = WuppIcons.Smartphone,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.link_mode_phone),
                        fontSize = 13.5.sp,
                        fontWeight = if (selectedMode == 1) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            AnimatedContent(
                targetState = selectedMode,
                label = "link_mode"
            ) { mode ->
                if (mode == 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(246.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White)
                                .border(1.dp, colors.divider, RoundedCornerShape(22.dp))
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "WhatsApp Link QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val hasQrError = bridgeStatus.connectionState == "QR_EXPIRED" ||
                                    bridgeStatus.connectionState == "CONNECT_FAILED" ||
                                    bridgeStatus.connectionState == "PAIR_ERROR" ||
                                    !bridgeStatus.lastError.isNullOrBlank()
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    if (!hasQrError) {
                                        ContainedLoadingIndicator(
                                            containerColor = Color(0xFFE7F8F2),
                                            indicatorColor = Color(0xFF00A884),
                                            modifier = Modifier.size(48.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = WuppIcons.QrCode,
                                            contentDescription = null,
                                            tint = Color(0xFF00A884),
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }
                                    Text(
                                        text = when {
                                            bridgeStatus.connectionState == "CONNECTED" || bridgeStatus.connectionState == "PAIRED" ->
                                                "Finishing link..."
                                            bridgeStatus.connectionState == "QR_EXPIRED" ->
                                                "QR code expired.\nTap Reload QR below."
                                            !bridgeStatus.lastError.isNullOrBlank() ->
                                                bridgeStatus.lastError
                                            else ->
                                                "Generating QR code..."
                                        },
                                        color = Color(0xFF3B4A54),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = {
                                haptic()
                                onRefreshConnection()
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("reload_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = colors.accentGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reload QR",
                                color = colors.accentGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        StepInstructionsCard(
                            steps = listOf(
                                "1. Open WhatsApp on your primary phone.",
                                "2. Tap Menu (or Settings) > Linked devices > Link a device.",
                                "3. Point your phone camera at this QR code."
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(onClick = { selectedMode = 1 }) {
                            Text(
                                text = "On this same phone? Link with phone number instead",
                                color = colors.brightGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.elevatedBar)
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Enter your phone number",
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Include your full country code without leading zeros or symbols (for example 923001234567 or +15551234567).",
                                color = colors.textSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )

                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("Phone number") },
                                placeholder = { Text("+92 300 1234567") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentGreen,
                                    unfocusedBorderColor = colors.divider,
                                    focusedLabelColor = colors.accentGreen,
                                    cursorColor = colors.accentGreen,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    haptic()
                                    requestedCode = null
                                    awaitingCode = true
                                    onPairPhone(phoneInput) { code ->
                                        awaitingCode = false
                                        if (code.isNotBlank()) {
                                            requestedCode = code
                                        }
                                    }
                                },
                                enabled = phoneInput.count { it.isDigit() } >= 7 && !awaitingCode,
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentGreen,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("get_linking_code_button")
                            ) {
                                if (awaitingCode) {
                                    LoadingIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = "Get linking code",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (!bridgeStatus.lastError.isNullOrBlank() && activePairCode.isNullOrBlank()) {
                                Text(
                                    text = bridgeStatus.lastError,
                                    color = Color(0xFFFF7A7A),
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        if (!activePairCode.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            val formattedCode = activePairCode.trim().replace("-", "")
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(colors.pillActiveBg)
                                    .border(1.dp, colors.accentGreen.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Enter code on WhatsApp",
                                    color = colors.pillActiveText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (formattedCode.length == 8) {
                                        "${formattedCode.substring(0, 4)} - ${formattedCode.substring(4)}"
                                    } else {
                                        activePairCode
                                    },
                                    color = colors.textPrimary,
                                    fontFamily = mono,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.appSurface.copy(alpha = 0.6f))
                                        .clickable {
                                            clipboard.setText(AnnotatedString(activePairCode))
                                            Toast.makeText(context, "Linking code copied", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "Copy linking code",
                                            tint = colors.accentGreen,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Copy code",
                                            color = colors.accentGreen,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        StepInstructionsCard(
                            steps = listOf(
                                "1. Tap the \"Enter code to link new device\" notification from WhatsApp.",
                                "2. Or open WhatsApp > Linked devices > Link a device > Link with phone number instead.",
                                "3. Enter the 8-character code shown above."
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = WuppIcons.Lock,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "End-to-end encrypted · Stored privately on your device",
                    color = colors.textSecondary,
                    fontSize = 11.5.sp
                )
            }
        }
        }
    }
}

@Composable
private fun StepInstructionsCard(steps: List<String>) {
    val colors = LocalWaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.elevatedBar.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        steps.forEach { line ->
            Text(
                text = line,
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

internal fun generateQrImage(content: String, sizePx: Int): ImageBitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(matrix.width * matrix.height)
        val fg = AndroidColor.rgb(17, 27, 33)
        val bg = AndroidColor.WHITE
        for (y in 0 until matrix.height) {
            val offset = y * matrix.width
            for (x in 0 until matrix.width) {
                pixels[offset + x] = if (matrix.get(x, y)) fg else bg
            }
        }
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }.asImageBitmap()
    }.getOrNull()
}
