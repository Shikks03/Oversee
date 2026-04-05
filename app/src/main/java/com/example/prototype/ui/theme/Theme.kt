package com.example.prototype.ui.theme

// --- COMPOSE UI ---
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.*


/**
 * Global UI Constants: Central source of truth for the app's design system.
 * Replaces old XML values from colors.xml and activity layouts.
 */
object AppTheme {
    // Colors (Refined from original XML hex values)
    val Background = Color(0xFFF2F2F2) // Light Blue
    val SecBackground = Color(0xFF457cba) // Dark Blue
    val Surface = Color(0xFFFFFFFF)    // Pure White
    val Primary = Color(0xFF2196F3)    // Parent Blue
    val Success = Color(0xFF4CAF50)    // Child Green
    val Warning = Color(0xFFFFA000)    // Medium Severity
    val Error = Color(0xFFD32F2F)      // High Severity
    val Border = Color(0xFFD9D9D9)     // Default Stroke

    val TextPrimary =  Color(0xFF000000) // Black
    val TextTertiary =  Color(0xFFB3B3B3) // Grey

    // Spacing & Sizing
    val PaddingDefault = 20.dp
    val ColumnGap = 16.dp
    val CardCorner = 24.dp
    val BadgeCorner = 8.dp

    val TitlePageStyle = TextStyle(
        fontSize = 48.sp,
        lineHeight = 57.6.sp,
        fontWeight = FontWeight(700),
        color = TextPrimary
    )

    val BodyBase = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.4.sp,
        fontWeight = FontWeight(400),
        color = TextPrimary
    )
}


val AppTypography = Typography(
    displayLarge = AppTheme.TitlePageStyle,
    bodyLarge = TextStyle(fontSize = 16.sp)
)

/**
 * Screen-relative sizing helpers. All values are computed as a fraction of the
 * current screen dimensions, so layouts adapt automatically to any phone or tablet.
 */
object Responsive {
    /** ~15 % of screen width — replaces the hard-coded 57 dp horizontal inset. */
    @Composable
    fun horizontalPadding(): Dp {
        val w = LocalConfiguration.current.screenWidthDp
        return (w * 0.15f).dp
    }

    /** ~12 % of screen height — replaces the hard-coded 103 dp vertical padding. */
    @Composable
    fun verticalPadding(): Dp {
        val h = LocalConfiguration.current.screenHeightDp
        return (h * 0.12f).dp.coerceIn(48.dp, 120.dp)
    }

    /** Logo icon on sign-in / sign-up screens (~35 % of width). */
    @Composable
    fun logoSize(): Dp {
        val w = LocalConfiguration.current.screenWidthDp
        return (w * 0.35f).dp.coerceIn(80.dp, 160.dp)
    }

    /** Section icon on setup / role screens (~22 % of width). */
    @Composable
    fun setupIconSize(): Dp {
        val w = LocalConfiguration.current.screenWidthDp
        return (w * 0.22f).dp.coerceIn(60.dp, 96.dp)
    }

    /** Vertical gap between content sections (~9 % of screen height). */
    @Composable
    fun sectionSpacing(): Dp {
        val h = LocalConfiguration.current.screenHeightDp
        return (h * 0.09f).dp.coerceIn(32.dp, 100.dp)
    }
}

//TEMP
object UserRoles{
    const val PARENT = "parent"
    const val CHILD = "child"
    const val UNSET = "NOT_SET"
}