package ir.persiancalendar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import ir.persiancalendar.app.data.prefs.AccentPalette
import ir.persiancalendar.app.data.prefs.ThemeMode

private fun greenLightScheme() = lightColorScheme(
    primary = GreenPrimaryLight,
    onPrimary = GreenOnPrimaryLight,
    primaryContainer = GreenPrimaryContainerLight,
    onPrimaryContainer = GreenOnPrimaryContainerLight,
    secondary = GreenSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = GreenSecondaryContainerLight,
    onSecondaryContainer = GreenOnPrimaryContainerLight,
    tertiary = GreenTertiaryLight,
    onTertiary = Color.White,
    background = GreenBackgroundLight,
    onBackground = Color(0xFF191C1A),
    surface = GreenSurfaceLight,
    onSurface = Color(0xFF191C1A),
    surfaceVariant = GreenSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF414942),
    outline = GreenOutlineLight,
    error = HolidayRed
)

private fun greenDarkScheme() = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = GreenOnPrimaryDark,
    primaryContainer = GreenPrimaryContainerDark,
    onPrimaryContainer = GreenOnPrimaryContainerDark,
    secondary = GreenSecondaryDark,
    onSecondary = Color(0xFF20342A),
    secondaryContainer = GreenSecondaryContainerDark,
    onSecondaryContainer = GreenSecondaryContainerLight,
    tertiary = GreenTertiaryDark,
    onTertiary = Color(0xFF063742),
    background = GreenBackgroundDark,
    onBackground = Color(0xFFDFE4DE),
    surface = GreenSurfaceDark,
    onSurface = Color(0xFFDFE4DE),
    surfaceVariant = GreenSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = GreenOutlineDark,
    error = HolidayRedDark
)

private fun tealLightScheme() = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = TealPrimaryContainerLight,
    onPrimaryContainer = Color(0xFF00201B),
    background = Color(0xFFF5FBFA),
    surface = Color(0xFFF5FBFA),
    surfaceVariant = Color(0xFFDBE5E3),
    error = HolidayRed
)

private fun tealDarkScheme() = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF003731),
    primaryContainer = TealPrimaryContainerDark,
    onPrimaryContainer = TealPrimaryContainerLight,
    background = Color(0xFF0E1413),
    surface = Color(0xFF0E1413),
    surfaceVariant = Color(0xFF3F4947),
    error = HolidayRedDark
)

private fun blueLightScheme() = lightColorScheme(
    primary = BluePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = BluePrimaryContainerLight,
    onPrimaryContainer = Color(0xFF001C38),
    background = Color(0xFFF7FAFF),
    surface = Color(0xFFF7FAFF),
    surfaceVariant = Color(0xFFDFE2EB),
    error = HolidayRed
)

private fun blueDarkScheme() = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color(0xFF00315C),
    primaryContainer = BluePrimaryContainerDark,
    onPrimaryContainer = BluePrimaryContainerLight,
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    surfaceVariant = Color(0xFF41474D),
    error = HolidayRedDark
)

private fun purpleLightScheme() = lightColorScheme(
    primary = PurplePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = PurplePrimaryContainerLight,
    onPrimaryContainer = Color(0xFF21005D),
    background = Color(0xFFFDF7FF),
    surface = Color(0xFFFDF7FF),
    surfaceVariant = Color(0xFFE7E0EB),
    error = HolidayRed
)

private fun purpleDarkScheme() = darkColorScheme(
    primary = PurplePrimaryDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = PurplePrimaryContainerDark,
    onPrimaryContainer = PurplePrimaryContainerLight,
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    surfaceVariant = Color(0xFF49454F),
    error = HolidayRedDark
)

/** رنگ اصلی فعلی برای استفاده‌های مستقیم (مثلاً نقطه رویداد) */
@Composable
fun currentPrimary(): Color = MaterialTheme.colorScheme.primary

/**
 * تم اصلی اپلیکیشن — روشن/تاریک با پالت انتخابی کاربر.
 */
@Composable
fun PersianCalendarTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentPalette = AccentPalette.GREEN,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val scheme = when (accent) {
        AccentPalette.GREEN -> if (dark) greenDarkScheme() else greenLightScheme()
        AccentPalette.TEAL -> if (dark) tealDarkScheme() else tealLightScheme()
        AccentPalette.BLUE -> if (dark) blueDarkScheme() else blueLightScheme()
        AccentPalette.PURPLE -> if (dark) purpleDarkScheme() else purpleLightScheme()
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content
    )
}

/** رنگ تعطیلات متناسب با روشنایی تم فعلی */
@Composable
fun holidayColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) HolidayRedDark else HolidayRed
