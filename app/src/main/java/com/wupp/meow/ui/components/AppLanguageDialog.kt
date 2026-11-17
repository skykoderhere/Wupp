package com.wupp.meow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wupp.meow.R
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.LocaleHelper
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger

@Composable
fun AppLanguageDialog(
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    val options = listOf(
        LocaleHelper.LANG_SYSTEM to stringResource(R.string.language_system),
        LocaleHelper.LANG_EN to stringResource(R.string.language_english),
        LocaleHelper.LANG_TR to stringResource(R.string.language_turkish)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textPrimary,
        title = {
            Text(
                text = stringResource(R.string.language_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (code, label) ->
                    val selected = currentLanguage == code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) colors.accentGreen.copy(alpha = 0.16f)
                                else colors.pillInactiveBg
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) colors.accentGreen else colors.divider,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptic()
                                onSelectLanguage(code)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (selected) colors.accentGreen else colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.accentGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
            }
        }
    )
}
