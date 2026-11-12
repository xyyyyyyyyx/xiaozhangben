package com.xiaozhangben.ledger.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** 把 0xAARRGGBB 的 Long 转成 Compose 颜色。 */
fun Long.toComposeColor(): Color = Color(this)

/** 金额数字统一用半粗体，视觉上更醒目。 */
val AmountFontWeight = FontWeight.SemiBold
