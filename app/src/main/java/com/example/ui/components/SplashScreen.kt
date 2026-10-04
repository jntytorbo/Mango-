package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette

@Composable
fun SplashScreen(
    progress: Float = 0f,
    status: String = "",
    appIconStyle: Int = 0,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    val (iconBgColor, iconFgRes) = when (appIconStyle) {
        0 -> Color(0xFFE3E4E6) to R.drawable.ic_launcher_foreground_black // Inverted
        1 -> Color(0xFF66676C) to R.drawable.ic_launcher_foreground       // Default
        2 -> Color(0xFF223A73) to R.drawable.ic_launcher_foreground       // Blue
        3 -> Color(0xFF8F6038) to R.drawable.ic_launcher_foreground       // Orange
        4 -> Color(0xFFF2545B) to R.drawable.ic_launcher_foreground       // Dark
        else -> Color(0xFFF3F4F6) to R.drawable.ic_launcher_foreground_black
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("app_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Selected App Icon from Settings
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = if (appIconStyle == 0) Color.Black.copy(alpha = 0.2f) else iconBgColor.copy(alpha = 0.35f),
                        spotColor = if (appIconStyle == 0) Color.Black.copy(alpha = 0.3f) else iconBgColor.copy(alpha = 0.45f)
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (appIconStyle == 0) Color(0xFFE5E7EB) else iconBgColor.copy(alpha = 0.4f),
                        shape = CircleShape
                    ),
                shape = CircleShape,
                color = iconBgColor
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        painter = painterResource(id = iconFgRes),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(72.dp)
                            .scale(1.15f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bubble Loading Animation under the icon
            BubbleLoadingIndicator(
                bubbleColor = if (appIconStyle == 2 || appIconStyle == 3) iconBgColor else accent,
                bubbleCount = 4,
                bubbleSize = 10.dp,
                spaceBetween = 8.dp
            )
        }
    }
}

/**
 * Clean & Smooth Bubble Loading Indicator with staggered floating bounce & scale wave.
 */
@Composable
fun BubbleLoadingIndicator(
    bubbleColor: Color,
    bubbleCount: Int = 4,
    bubbleSize: Dp = 10.dp,
    spaceBetween: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bubble_loading_anim")

    Row(
        modifier = modifier.height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(spaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until bubbleCount) {
            val delayOffset = i * 160

            val floatOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0f at 0 using FastOutSlowInEasing
                        -12f at (300 + delayOffset) % 1200 using FastOutSlowInEasing
                        0f at (600 + delayOffset) % 1200 using FastOutSlowInEasing
                        0f at 1200 using FastOutSlowInEasing
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "bubble_float_$i"
            )

            val bubbleScale by infiniteTransition.animateFloat(
                initialValue = 0.75f,
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.75f at 0 using FastOutSlowInEasing
                        1.25f at (300 + delayOffset) % 1200 using FastOutSlowInEasing
                        0.75f at (600 + delayOffset) % 1200 using FastOutSlowInEasing
                        0.75f at 1200 using FastOutSlowInEasing
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "bubble_scale_$i"
            )

            val bubbleAlpha by infiniteTransition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.45f at 0 using FastOutSlowInEasing
                        1f at (300 + delayOffset) % 1200 using FastOutSlowInEasing
                        0.45f at (600 + delayOffset) % 1200 using FastOutSlowInEasing
                        0.45f at 1200 using FastOutSlowInEasing
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "bubble_alpha_$i"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = floatOffset
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                        alpha = bubbleAlpha
                    }
                    .size(bubbleSize)
                    .clip(CircleShape)
                    .background(bubbleColor)
            )
        }
    }
}
