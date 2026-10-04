package com.example.ui

import com.example.R
import com.example.ui.components.ResourcePreWarmer
import com.example.ui.theme.AppTransitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourcePreWarmerTest {

    @Test
    fun testDrawablesInventoryExactCount52() {
        assertEquals(52, ResourcePreWarmer.TOTAL_DRAWABLES_COUNT)
        assertEquals(52, ResourcePreWarmer.allDrawables.size)
        // Verify no duplicate IDs
        assertEquals(52, ResourcePreWarmer.allDrawables.toSet().size)
    }

    @Test
    fun testAllRequiredDrawablesPresentInInventory() {
        val requiredIds = listOf(
            // Circular buttons & icons
            R.drawable.ic_magnet,
            R.drawable.ic_url_link,
            R.drawable.ic_bookmark_saved,
            R.drawable.ic_bookmark_save,
            R.drawable.ic_action_save,
            R.drawable.ic_edit_pencil,
            R.drawable.ic_delete_trash,
            R.drawable.ic_action_cancel,
            R.drawable.ic_quality_hd,
            R.drawable.ic_quality_4k,
            // CardLink & screens
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
            R.drawable.ic_sexmex,
            // Nav & drawer
            R.drawable.ic_nav_home,
            R.drawable.ic_nav_bookmark,
            R.drawable.ic_nav_actor,
            R.drawable.ic_nav_studio,
            R.drawable.ic_nav_stashdb,
            R.drawable.ic_nav_settings,
            // Video player
            R.drawable.ic_player_play,
            R.drawable.ic_player_pause,
            R.drawable.ic_player_back,
            R.drawable.ic_player_forward,
            R.drawable.ic_player_rewind,
            R.drawable.ic_player_fullscreen,
            R.drawable.ic_player_fullscreen_exit,
            R.drawable.ic_player_pip,
            R.drawable.ic_player_external,
            // Gestures & settings
            R.drawable.ic_gesture_brightness,
            R.drawable.ic_gesture_volume_up,
            R.drawable.ic_gesture_volume_mute,
            R.drawable.ic_settings_backup,
            R.drawable.ic_settings_display,
            R.drawable.ic_settings_filter,
            R.drawable.ic_settings_integrations,
            R.drawable.ic_settings_privacy,
            R.drawable.ic_settings_sample_data,
            // Alternate launcher icons
            R.drawable.ic_launcher_background,
            R.drawable.ic_launcher_foreground,
            R.drawable.ic_launcher_foreground_black,
            R.drawable.ic_launcher_bg_blue,
            R.drawable.ic_launcher_bg_dark,
            R.drawable.ic_launcher_bg_inverted,
            R.drawable.ic_launcher_bg_orange
        )

        assertEquals(52, requiredIds.size)
        requiredIds.forEach { id ->
            assertTrue("Missing required drawable in inventory: $id", ResourcePreWarmer.allDrawables.contains(id))
        }
    }

    @Test
    fun testAppTransitionsCentralizedTimings() {
        assertEquals(220, AppTransitions.DURATION_SLIDE)
        assertEquals(200, AppTransitions.DURATION_FADE_IN)
        assertEquals(170, AppTransitions.DURATION_FADE_OUT)
        assertEquals(340, AppTransitions.DURATION_ENTRANCE)
        assertEquals(300, AppTransitions.DURATION_ENTRANCE_FADE)
    }
}
