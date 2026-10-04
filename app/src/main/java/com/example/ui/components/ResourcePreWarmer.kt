package com.example.ui.components

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import com.example.R

/**
 * Pre-warms and caches all drawable and font resources in the background.
 * Ensures zero delay and instant rendering when tapping buttons, menus, and cards.
 */
object ResourcePreWarmer {
    private const val TAG = "ResourcePreWarmer"
    const val TOTAL_DRAWABLES_COUNT = 52

    @DrawableRes
    val circularButtonDrawables = listOf(
        R.drawable.ic_magnet,
        R.drawable.ic_url_link,
        R.drawable.ic_bookmark_saved,
        R.drawable.ic_bookmark_save,
        R.drawable.ic_action_save,
        R.drawable.ic_edit_pencil,
        R.drawable.ic_delete_trash,
        R.drawable.ic_action_cancel,
        R.drawable.ic_quality_hd,
        R.drawable.ic_quality_4k
    )

    @DrawableRes
    val cardLinkAndScreenDrawables = listOf(
        R.drawable.ic_actor_add,
        R.drawable.ic_user_group,
        R.drawable.ic_app_add,
        R.drawable.ic_app_calendar,
        R.drawable.ic_app_paste,
        R.drawable.ic_app_search,
        R.drawable.ic_app_sort,
        R.drawable.ic_app_menu,
        R.drawable.ic_details_adjust_brush,
        R.drawable.ic_calendar_event,
        R.drawable.ic_sexmex
    )

    @DrawableRes
    val navAndDrawerDrawables = listOf(
        R.drawable.ic_nav_home,
        R.drawable.ic_nav_bookmark,
        R.drawable.ic_nav_actor,
        R.drawable.ic_nav_studio,
        R.drawable.ic_nav_stashdb,
        R.drawable.ic_nav_settings
    )

    @DrawableRes
    val videoPlayerDrawables = listOf(
        R.drawable.ic_player_play,
        R.drawable.ic_player_pause,
        R.drawable.ic_player_back,
        R.drawable.ic_player_forward,
        R.drawable.ic_player_rewind,
        R.drawable.ic_player_fullscreen,
        R.drawable.ic_player_fullscreen_exit,
        R.drawable.ic_player_pip,
        R.drawable.ic_player_external
    )

    @DrawableRes
    val gestureAndSettingsDrawables = listOf(
        R.drawable.ic_gesture_brightness,
        R.drawable.ic_gesture_volume_up,
        R.drawable.ic_gesture_volume_mute,
        R.drawable.ic_settings_backup,
        R.drawable.ic_settings_display,
        R.drawable.ic_settings_filter,
        R.drawable.ic_settings_integrations,
        R.drawable.ic_settings_privacy,
        R.drawable.ic_settings_sample_data
    )

    @DrawableRes
    val launcherDrawables = listOf(
        R.drawable.ic_launcher_background,
        R.drawable.ic_launcher_foreground,
        R.drawable.ic_launcher_foreground_black,
        R.drawable.ic_launcher_bg_blue,
        R.drawable.ic_launcher_bg_dark,
        R.drawable.ic_launcher_bg_inverted,
        R.drawable.ic_launcher_bg_orange
    )

    /**
     * Complete @RawRes-free inventory of all drawables in res/drawable (strictly 52/52).
     */
    @DrawableRes
    val allDrawables: List<Int> = circularButtonDrawables +
            cardLinkAndScreenDrawables +
            navAndDrawerDrawables +
            videoPlayerDrawables +
            gestureAndSettingsDrawables +
            launcherDrawables

    fun preWarmAll(context: Context): Int {
        var warmedDrawables = 0

        // 1. Pre-warm all 52/52 Drawables
        allDrawables.forEach { resId ->
            try {
                val d = ResourcesCompat.getDrawable(context.resources, resId, context.theme)
                if (d != null) {
                    warmedDrawables++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pre-warm drawable: $resId", e)
            }
        }

        if (warmedDrawables < TOTAL_DRAWABLES_COUNT) {
            val missing = TOTAL_DRAWABLES_COUNT - warmedDrawables
            Log.w(TAG, "Pre-warming incomplete: missing $missing drawables ($warmedDrawables/$TOTAL_DRAWABLES_COUNT)")
        } else {
            Log.i(TAG, "Pre-warming successful: all $warmedDrawables/$TOTAL_DRAWABLES_COUNT drawables cached")
        }

        // 2. Pre-warm Lexend font via ResourcesCompat.getFont
        try {
            ResourcesCompat.getFont(context, R.font.lexend)
            Log.i(TAG, "Pre-warmed font res/font/lexend.ttf successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Font pre-warming fallback: ${e.message}")
        }

        return warmedDrawables
    }
}
