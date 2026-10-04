package com.example.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.MainViewModel
import com.example.ui.ScreenState
import com.example.ui.components.GoPlayer
import com.example.ui.components.PhotosetLightbox
import com.example.ui.components.SmoothProgressIndicator
import com.example.ui.components.SplashScreen
import com.example.ui.theme.AppTransitions
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette
import kotlinx.coroutines.launch

private val DialogScrim = Color.Black.copy(alpha = 0.65f) // BG-FIX

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

val LocalTopBarContent = compositionLocalOf<MutableState<(@Composable () -> Unit)?>> {
    error("No LocalTopBarContent provided")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(viewModel: MainViewModel) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentScreen by viewModel.screenState.collectAsStateWithLifecycle()
    val navDirection by viewModel.navDirection.collectAsStateWithLifecycle()
    val activeVideo by viewModel.activeVideo.collectAsStateWithLifecycle()
    val activeLightbox by viewModel.activeLightbox.collectAsStateWithLifecycle()
    val resolvingStatus by viewModel.resolvingVideoStatus.collectAsStateWithLifecycle()
    val resolvingCardId by viewModel.resolvingCardId.collectAsStateWithLifecycle()
    val videoResolutionError by viewModel.videoResolutionError.collectAsStateWithLifecycle()
    val activeInlineVideo by viewModel.activeInlineVideo.collectAsStateWithLifecycle()

    val isSplashLoading by viewModel.isSplashLoading.collectAsStateWithLifecycle()
    val splashProgress by viewModel.splashProgress.collectAsStateWithLifecycle()
    val splashStatus by viewModel.splashStatus.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isInPipMode by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }

    DisposableEffect(activity) {
        if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
                isInPipMode = info.isInPictureInPictureMode
                if (info.isInPictureInPictureMode) {
                    val currentInline = viewModel.activeInlineVideo.value
                    if (viewModel.activeVideo.value == null && currentInline != null) {
                        viewModel.enterPipFromInline(
                            currentInline.cardId,
                            viewModel.sharedPlayerManager.getPlayer().currentPosition
                        )
                    }
                } else {
                    if (viewModel.enteredPipFromInlineCard) {
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        viewModel.returnToInlineFromPip()
                    }
                }
            }
            activity.addOnPictureInPictureModeChangedListener(listener)
            onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
        } else {
            onDispose { }
        }
    }

    // Smooth App Launch Entrance Animation (Matches Add Scene motion)
    var appEntranceVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        appEntranceVisible = true
    }

    // Handle back button press
    val canHandleBack = activeLightbox != null || activeVideo != null || drawerState.isOpen || currentScreen != ScreenState.Home
    BackHandler(enabled = canHandleBack) {
        if (activeLightbox != null) {
            viewModel.closeLightbox()
        } else if (activeVideo != null) {
            viewModel.closeVideo()
        } else if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else {
            val handled = viewModel.navigateBack()
            if (!handled) {
                activity?.finish()
            }
        }
    }

    val topBarMap = remember { mutableStateMapOf<ScreenState, @Composable () -> Unit>() }
    val topBarContent = remember(currentScreen) {
        val state = mutableStateOf<(@Composable () -> Unit)?>(null)
        object : MutableState<(@Composable () -> Unit)?> {
            override var value: (@Composable () -> Unit)?
                get() = state.value
                set(newValue) {
                    if (state.value !== newValue) {
                        state.value = newValue
                        if (newValue != null) {
                            topBarMap[currentScreen] = newValue
                        }
                    }
                }
            override fun component1(): (@Composable () -> Unit)? = state.value
            override fun component2(): ((@Composable () -> Unit)?) -> Unit = { value = it }
        }
    }
    CompositionLocalProvider(LocalTopBarContent provides topBarContent) {
        if (isInPipMode) {
            activeVideo?.let { video ->
                GoPlayer(
                    title = video.title,
                    qualities = video.qualities,
                    subtitles = video.subtitles,
                    defaultHeaders = video.headers,
                    initialPositionMs = video.initialPositionMs,
                    startInLandscape = false,
                    exoPlayer = viewModel.sharedPlayerManager.getPlayer(),
                    onClose = { viewModel.closeVideo() }
                )
            }
        } else {
        val isDrawerSwipeEnabled = drawerState.isOpen || (activeVideo == null && activeLightbox == null && (currentScreen is ScreenState.Home || currentScreen is ScreenState.Bookmarks || currentScreen is ScreenState.StashDb))
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = isDrawerSwipeEnabled,
            scrimColor = Color.Black.copy(alpha = 0.5f),
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = palette.cardBg, // BG-FIX
                    drawerContentColor = palette.textPrimary,
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accent,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                        contentDescription = "Goony Logo",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Text("Goony", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = palette.textPrimary)
                        }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = palette.border)
                    Spacer(modifier = Modifier.height(12.dp))

                    val isHomeSelected = currentScreen is ScreenState.Home
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_home),
                                contentDescription = "Home",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Home", fontWeight = if (isHomeSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isHomeSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.Home)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    val isActorsSelected = currentScreen is ScreenState.Actors || currentScreen is ScreenState.ActorScenes
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_actor),
                                contentDescription = "Actors",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Actors", fontWeight = if (isActorsSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isActorsSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.Actors)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    val isStudiosSelected = currentScreen is ScreenState.Studios || currentScreen is ScreenState.StudioScenes
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_studio),
                                contentDescription = "Studios",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Studios", fontWeight = if (isStudiosSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isStudiosSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.Studios)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    val isBookmarksSelected = currentScreen is ScreenState.Bookmarks
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_bookmark),
                                contentDescription = "Bookmarks",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Bookmarks", fontWeight = if (isBookmarksSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isBookmarksSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.Bookmarks)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    val isStashDbSelected = currentScreen is ScreenState.StashDb
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_stashdb),
                                contentDescription = "StashDB",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("StashDB", fontWeight = if (isStashDbSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isStashDbSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.StashDb)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = palette.border)
                    Spacer(modifier = Modifier.height(12.dp))

                    val isSettingsSelected = currentScreen is ScreenState.Settings
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_settings),
                                contentDescription = "Settings",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Settings", fontWeight = if (isSettingsSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isSettingsSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.Settings)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = palette.bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (activeVideo == null && activeLightbox == null) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            AppTransitions.screenTransition(navDirection)
                        },
                        label = "global_top_bar_transition"
                    ) { screen ->
                        val content = if (screen == currentScreen) (topBarContent.value ?: topBarMap[screen]) else topBarMap[screen]
                        content?.invoke()
                    }
                }
            }
        ) { globalPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (activeVideo == null && activeLightbox == null) globalPadding.calculateTopPadding() else 0.dp)
            ) {
                AnimatedVisibility(
                    visible = appEntranceVisible,
                    enter = AppTransitions.appEntranceEnter,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val openDrawerLambda: () -> Unit = { coroutineScope.launch { drawerState.open() } }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            AppTransitions.screenTransition(navDirection)
                        },
                        label = "screen_motion_transition"
                    ) { screen ->
                        when (screen) {
                            is ScreenState.Home -> HomeScreen(
                                viewModel = viewModel,
                                onOpenDrawer = openDrawerLambda
                            )
                            is ScreenState.Bookmarks -> BookmarksScreen(
                                viewModel = viewModel,
                                onOpenDrawer = openDrawerLambda
                            )
                            is ScreenState.AddEditLink -> AddEditLinkScreen(viewModel, screen.linkId)
                            is ScreenState.Actors -> ActorManagementScreen(viewModel)
                            is ScreenState.AddEditActor -> ActorManagementScreen(viewModel)
                            is ScreenState.ActorScenes -> HomeScreen(
                                viewModel = viewModel,
                                onOpenDrawer = openDrawerLambda
                            )
                            is ScreenState.Studios -> StudioManagementScreen(viewModel)
                            is ScreenState.AddEditStudio -> StudioManagementScreen(viewModel)
                            is ScreenState.StudioScenes -> HomeScreen(
                                viewModel = viewModel,
                                onOpenDrawer = openDrawerLambda
                            )
                            is ScreenState.StashDb -> StashDbScreen(
                                viewModel = viewModel,
                                onOpenDrawer = openDrawerLambda
                            )
                            is ScreenState.Settings -> SettingsScreen(viewModel)
                        }
                    }
                }

                // GoPlayer / ExoPlayer Video Player Overlay
                activeVideo?.let { video ->
                    GoPlayer(
                        title = video.title,
                        qualities = video.qualities,
                        subtitles = video.subtitles,
                        defaultHeaders = video.headers,
                        initialPositionMs = video.initialPositionMs,
                        startInLandscape = video.startInLandscape,
                        exoPlayer = viewModel.sharedPlayerManager.getPlayer(),
                        onClose = { viewModel.closeVideo() }
                    )
                }

                // High-Res Photoset Lightbox Overlay
                activeLightbox?.let { (images, startIndex) ->
                    PhotosetLightbox(
                        images = images,
                        initialIndex = startIndex,
                        onClose = { viewModel.closeLightbox() }
                    )
                }

                // Video Resolving / Debrid Progress Overlay (Only for non-card actions, cards handle inline)
                if (resolvingCardId == null) {
                    resolvingStatus?.let { statusText ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DialogScrim),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .padding(20.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = palette.cardBg)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    SmoothProgressIndicator(
                                        modifier = Modifier.size(44.dp),
                                        color = accent,
                                        strokeWidth = 3.5.dp
                                    )
                                    Text(
                                        text = statusText,
                                        color = palette.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Video Resolution / Debrid Error Dialog (Only shown globally if not triggered by an inline card)
                if (resolvingCardId == null) {
                    videoResolutionError?.let { errText ->
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissVideoError() },
                            icon = {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = {
                                Text(
                                    text = "Stream Playback Error",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            },
                            text = {
                                Text(
                                    text = errText,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = palette.textSecondary
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.dismissVideoError() },
                                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                                ) {
                                    Text("OK")
                                }
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isSplashLoading,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(450, easing = FastOutSlowInEasing)) + scaleOut(targetScale = 1.05f, animationSpec = tween(450, easing = FastOutSlowInEasing))
        ) {
            SplashScreen(
                progress = splashProgress,
                status = splashStatus,
                appIconStyle = settings.appIconStyle,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
}
}
