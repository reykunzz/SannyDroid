package dev.sannydroid

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class GtaFont(
    val family: FontFamily,
    val weight: FontWeight,
    val custom: Boolean,
)

val LocalGtaFont = staticCompositionLocalOf {
    GtaFont(FontFamily.SansSerif, FontWeight.SemiBold, false)
}

// Tetap dipertahankan agar pemanggilan lama tidak rusak.
fun loadGtaFont(ctx: Context): GtaFont {
    return GtaFont(FontFamily.SansSerif, FontWeight.SemiBold, false)
}

@Composable
fun GtaText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = Color.Unspecified,
    shadow: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        style = TextStyle.Default,
    )
}

@Composable
fun GtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) cs.primary else Color(0xCC000000),
            contentColor = if (primary) cs.onPrimary else Color.White,
        ),
        border = BorderStroke(1.dp, cs.primary),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun SunsetBar() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFFFA64D),
                        Color(0xFFFF6FA8),
                        Color(0xFF7B4FBF),
                    ),
                ),
            ),
    )
}
