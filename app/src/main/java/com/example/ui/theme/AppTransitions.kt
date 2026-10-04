package com.example.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import com.example.ui.MainViewModel

/**
 * Centralized navigation and motion transition specifications.
 * Preserves exact visual timing, easing, and direction behavior across all app screens and lists.
 */
object AppTransitions {
    const val DURATION_SLIDE = 220
    const val DURATION_FADE_IN = 200
    const val DURATION_FADE_OUT = 170
    const val DURATION_ENTRANCE = 340
    const val DURATION_ENTRANCE_FADE = 300

    val itemFadeInSpec: FiniteAnimationSpec<Float> = tween(
        durationMillis = DURATION_FADE_IN,
        easing = LinearOutSlowInEasing
    )

    val itemFadeOutSpec: FiniteAnimationSpec<Float> = tween(
        durationMillis = DURATION_FADE_OUT
    )

    val itemPlacementSpec: FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = DURATION_SLIDE,
        easing = FastOutSlowInEasing
    )

    fun screenTransition(direction: MainViewModel.NavigationDirection): ContentTransform {
        return if (direction == MainViewModel.NavigationDirection.BACK) {
            (slideInHorizontally(animationSpec = tween(DURATION_SLIDE, easing = FastOutSlowInEasing)) { width -> -width / 4 } +
                    fadeIn(animationSpec = tween(DURATION_FADE_IN, easing = LinearOutSlowInEasing)))
                .togetherWith(
                    slideOutHorizontally(animationSpec = tween(DURATION_FADE_IN, easing = FastOutSlowInEasing)) { width -> width / 4 } +
                            fadeOut(animationSpec = tween(DURATION_FADE_OUT))
                )
        } else {
            (slideInHorizontally(animationSpec = tween(DURATION_SLIDE, easing = FastOutSlowInEasing)) { width -> width / 4 } +
                    fadeIn(animationSpec = tween(DURATION_FADE_IN, easing = LinearOutSlowInEasing)))
                .togetherWith(
                    slideOutHorizontally(animationSpec = tween(DURATION_FADE_IN, easing = FastOutSlowInEasing)) { width -> -width / 4 } +
                            fadeOut(animationSpec = tween(DURATION_FADE_OUT))
                )
        }
    }

    val appEntranceEnter: EnterTransition = slideInVertically(
        animationSpec = tween(DURATION_ENTRANCE, easing = FastOutSlowInEasing)
    ) { fullHeight -> fullHeight / 5 } + fadeIn(animationSpec = tween(DURATION_ENTRANCE_FADE))
}
