package com.xiaozhangben.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 浅粉色主题（Material 3 色板）。
 *
 * 配色思路：
 *  · primary 用饱和一点的玫红，保证白字按钮对比度够（约 5.6:1）；
 *  · primaryContainer 是浅粉，chips / 选中态 / 图标底色都用它，"粉色感"主要来自这里；
 *  · background 是带粉调的浅底，surface 更接近白，卡片因此能从背景上浮起来；
 *  · 收入的绿、支出的红仍然保留语义色（见文件末尾 LedgerColors），不参与主题色。
 */
val LightColors = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF75565D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E1),
    onSecondaryContainer = Color(0xFF2B151A),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31101C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF5F7),
    onBackground = Color(0xFF201A1B),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFFF2DDE1),
    onSurfaceVariant = Color(0xFF524345),
    outline = Color(0xFF847375),
    outlineVariant = Color(0xFFD6C2C5),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF650033),
    primaryContainer = Color(0xFF8E0045),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE4BDC5),
    onSecondary = Color(0xFF422A31),
    secondaryContainer = Color(0xFF5A4047),
    onSecondaryContainer = Color(0xFFFFD9E1),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF4B2533),
    tertiaryContainer = Color(0xFF643B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1214),
    onBackground = Color(0xFFECE0E1),
    surface = Color(0xFF221A1C),
    onSurface = Color(0xFFECE0E1),
    surfaceVariant = Color(0xFF524345),
    onSurfaceVariant = Color(0xFFD6C2C5),
    outline = Color(0xFF9E8C8E),
    outlineVariant = Color(0xFF524345),
)

/** 收入/支出专用语义色，浅色深色各一套。与主题色无关，换主题时不用动。 */
object LedgerColors {

    val income: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF7ED492) else Color(0xFF1B873F)

    val expense: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFC62828)

    val warning: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFCC80) else Color(0xFFE65100)
}
