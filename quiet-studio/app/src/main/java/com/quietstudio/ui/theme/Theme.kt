package com.quietstudio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quietstudio.R

/* ---------------------------------------------------------------------------
 * Quiet Studio design system — matched to the reference mock:
 * near-black surfaces, violet accent system, warm mic glow, Poppins display
 * type over Inter body type.
 * ------------------------------------------------------------------------ */

val Ink = Color(0xFF0D0D12)            // app background
val Card = Color(0xFF17171E)           // cards / sheets
val CardHigh = Color(0xFF1E1E28)       // raised cards, chips
val Hairline = Color(0xFF262633)       // outlines
val TextPrimary = Color(0xFFF4F4F8)
val TextSecondary = Color(0xFF9CA0AF)

val Violet = Color(0xFF7C5CFC)         // primary accent
val VioletDeep = Color(0xFF6D28D9)
val VioletSoft = Color(0xFF9D7DFF)
val Highlight = Color(0xFFF5C044)      // caption emphasis / gold
val Ember = Color(0xFFF97316)          // mic-glow warm edge
val Rose = Color(0xFFEC4899)
val Teal = Color(0xFF2DD4BF)
val Sky = Color(0xFF60A5FA)
val Lime = Color(0xFF84CC16)
val Danger = Color(0xFFEF5350)

/** Signature gradients from the mock. */
object QuietGradients {
    val micGlow = Brush.sweepGradient(
        listOf(Violet, Rose, Ember, Highlight, Violet)
    )
    val primary = Brush.linearGradient(listOf(VioletSoft, VioletDeep))
    val heroSky = Brush.verticalGradient(
        listOf(Color(0xFF2A1E4F), Color(0xFF4A2B66), Color(0xFF8A4C63), Color(0xFF12101C))
    )
    val cardEdge = Brush.linearGradient(listOf(Color(0xFF23232F), Color(0xFF17171E)))
}

val Poppins = FontFamily(
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight.SemiBold),
)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = CardHigh,
    onPrimaryContainer = TextPrimary,
    secondary = VioletSoft,
    onSecondary = Ink,
    tertiary = Highlight,
    background = Ink,
    onBackground = TextPrimary,
    surface = Card,
    onSurface = TextPrimary,
    surfaceVariant = CardHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Card,
    surfaceContainerHigh = CardHigh,
    outline = Hairline,
    error = Danger,
)

val QuietTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Bold,
        fontSize = 52.sp, letterSpacing = (-1).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
    ),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 15.5.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 17.sp, color = TextSecondary),
    labelLarge = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp, letterSpacing = 0.2.sp, color = TextSecondary,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, letterSpacing = 0.3.sp, color = TextSecondary,
    ),
)

@Composable
fun QuietStudioTheme(
    darkTheme: Boolean = true, // this design is dark, full stop
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = QuietTypography,
        content = content,
    )
}
