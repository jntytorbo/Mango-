package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.local.entity.ActorEntity
import com.example.ui.MainViewModel
import com.example.ui.ScreenState
import com.example.ui.SortMode
import com.example.ui.components.LinkCard
import com.example.ui.components.SkeletonCard
import com.example.ui.theme.AppTransitions
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    val allLinks by viewModel.allLinks.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsStateWithLifecycle()
    val actors by viewModel.allActors.collectAsStateWithLifecycle()
    val studios by viewModel.allStudios.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentSort by viewModel.sortMode.collectAsStateWithLifecycle()
    val resolvingStatus by viewModel.resolvingVideoStatus.collectAsStateWithLifecycle()
    val resolvingCardId by viewModel.resolvingCardId.collectAsStateWithLifecycle()
    val videoResolutionError by viewModel.videoResolutionError.collectAsStateWithLifecycle()
    val activeInlineVideo by viewModel.activeInlineVideo.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isInitialDataLoaded by viewModel.isInitialDataLoaded.collectAsStateWithLifecycle()

    val actorsMap = remember(actors) {
        val map = mutableMapOf<String, String>()
        actors.forEach { actor ->
            map[actor.id] = actor.name
            map[actor.name] = actor.name
            map[actor.name.trim().lowercase()] = actor.name
            if (!actor.stashDbId.isNullOrBlank()) {
                map[actor.stashDbId] = actor.name
            }
        }
        map
    }
    val fullActorsMap = remember(actors) {
        val map = mutableMapOf<String, ActorEntity>()
        actors.forEach { actor ->
            map[actor.id] = actor
            map[actor.name] = actor
            map[actor.name.trim().lowercase()] = actor
            if (!actor.stashDbId.isNullOrBlank()) {
                map[actor.stashDbId] = actor
            }
        }
        map
    }
    val studiosMap = remember(studios) {
        val map = mutableMapOf<String, String>()
        studios.forEach { studio ->
            map[studio.id] = studio.name
            map[studio.name] = studio.name
            map[studio.name.trim().lowercase()] = studio.name
            if (!studio.stashDbId.isNullOrBlank()) {
                map[studio.stashDbId] = studio.name
            }
        }
        map
    }

    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var activeOverlayCardId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = isSearchExpanded || showSortMenu) {
        if (showSortMenu) {
            showSortMenu = false
        } else if (isSearchExpanded) {
            isSearchExpanded = false
            viewModel.searchQuery.value = ""
        }
    }

    val scrollKey = "feed_bookmarks"
    val initialScroll = remember { viewModel.getScrollPosition(scrollKey) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScroll.first,
        initialFirstVisibleItemScrollOffset = initialScroll.second
    )

    var previousSort by rememberSaveable { mutableStateOf(currentSort.name) }
    var previousQuery by rememberSaveable { mutableStateOf(searchQuery) }

    val bookmarkedLinks = remember(allLinks, bookmarkedIds, searchQuery, currentSort, actors, studios) {
        val bookmarked = allLinks.filter { bookmarkedIds.contains(it.id) }
        val searched = if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            val matchingActorIds = actors.filter { it.name.lowercase().contains(q) }
                .flatMap { listOfNotNull(it.id, it.name, it.stashDbId) }
                .toSet()
            val matchingStudioIds = studios.filter { it.name.lowercase().contains(q) }
                .flatMap { listOfNotNull(it.id, it.name, it.stashDbId) }
                .toSet()
            bookmarked.filter { link ->
                link.title.lowercase().contains(q) ||
                link.actorIds.any { matchingActorIds.contains(it) || it.lowercase().contains(q) } ||
                link.studioIds.any { matchingStudioIds.contains(it) || it.lowercase().contains(q) }
            }
        } else {
            bookmarked
        }

        when (currentSort) {
            SortMode.NEW -> searched.sortedByDescending { it.assignedDate ?: it.createdAt }
            SortMode.OLD -> searched.sortedBy { it.assignedDate ?: it.createdAt }
            SortMode.RECENTLY_ADDED -> searched.sortedByDescending { it.createdAt }
            SortMode.OLDEST_ADDED -> searched.sortedBy { it.createdAt }
        }
    }

    // Continuously remember the user's exact scroll position in ViewModel when items are present
    LaunchedEffect(listState, bookmarkedLinks.isNotEmpty()) {
        if (bookmarkedLinks.isNotEmpty()) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    viewModel.saveScrollPosition(scrollKey, index, offset)
                }
        }
    }

    LaunchedEffect(currentSort, searchQuery) {
        if (previousSort != currentSort.name || previousQuery != searchQuery) {
            previousSort = currentSort.name
            previousQuery = searchQuery
            viewModel.saveScrollPosition(scrollKey, 0, 0)
            if (bookmarkedLinks.isNotEmpty()) {
                runCatching { listState.scrollToItem(0) }
            }
        }
        activeOverlayCardId = null
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val topBarContent = LocalTopBarContent.current
    DisposableEffect(Unit) {
        onDispose {
            topBarContent.value = null
        }
    }
    val currentTopBar: @Composable () -> Unit = remember(
        isSearchExpanded,
        searchQuery,
        showSortMenu,
        currentSort,
        bookmarkedLinks.size,
        palette
    ) {
        {
            TopAppBar(
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = palette.border,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }, // BG-FIX
                title = {
                    if (isSearchExpanded) {
                        LaunchedEffect(Unit) {
                            try {
                                focusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("search_bookmarks_input"),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search bookmarks...",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "Bookmarks (${bookmarkedLinks.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    if (isSearchExpanded) {
                        IconButton(
                            onClick = {
                                isSearchExpanded = false
                                viewModel.searchQuery.value = ""
                            },
                            modifier = Modifier.testTag("close_search_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Search",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.testTag("back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (isSearchExpanded) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.searchQuery.value = "" },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_action_cancel),
                                    contentDescription = "Clear Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    isSearchExpanded = false
                                    viewModel.searchQuery.value = ""
                                },
                                modifier = Modifier.testTag("close_search_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_action_cancel),
                                    contentDescription = "Close Search",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { isSearchExpanded = true },
                            modifier = Modifier.testTag("search_action_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_app_search),
                                contentDescription = "Search",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("sort_action_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_app_sort),
                                    contentDescription = "Sort Mode",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = palette.surface,
                                modifier = Modifier.background(palette.surface)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "New",
                                            fontWeight = if (currentSort == SortMode.NEW) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentSort == SortMode.NEW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (currentSort == SortMode.NEW) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.sortMode.value = SortMode.NEW
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Old",
                                            fontWeight = if (currentSort == SortMode.OLD) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentSort == SortMode.OLD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (currentSort == SortMode.OLD) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.sortMode.value = SortMode.OLD
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Recently Added",
                                            fontWeight = if (currentSort == SortMode.RECENTLY_ADDED) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentSort == SortMode.RECENTLY_ADDED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (currentSort == SortMode.RECENTLY_ADDED) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.sortMode.value = SortMode.RECENTLY_ADDED
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Oldest Added",
                                            fontWeight = if (currentSort == SortMode.OLDEST_ADDED) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentSort == SortMode.OLDEST_ADDED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (currentSort == SortMode.OLDEST_ADDED) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.sortMode.value = SortMode.OLDEST_ADDED
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface // BG-FIX
                )
            )
        }
    }
    SideEffect {
        topBarContent.value = currentTopBar
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.bg, // BG-FIX
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {}
    ) { padding ->
        if (!isInitialDataLoaded) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(3) {
                    SkeletonCard(height = 240)
                }
            }
        } else if (bookmarkedLinks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) "No results for '$searchQuery'" else "No bookmarked scenes yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try searching with different keywords" else "Tap the Save button on any link card to bookmark it for quick access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(bookmarkedLinks, key = { it.id }) { link ->
                    Box(
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = AppTransitions.itemFadeInSpec,
                                fadeOutSpec = AppTransitions.itemFadeOutSpec,
                                placementSpec = AppTransitions.itemPlacementSpec
                            )
                            .padding(vertical = 6.dp)
                    ) {
                        LinkCard(
                            link = link,
                            actorsMap = actorsMap,
                            studiosMap = studiosMap,
                            fullActorsMap = fullActorsMap,
                            isBookmarked = bookmarkedIds.contains(link.id),
                            isActiveCard = activeOverlayCardId == link.id,
                            onActivate = { activeOverlayCardId = link.id },
                            onDismissActive = {
                                if (activeOverlayCardId == link.id) activeOverlayCardId = null
                            },
                            onToggleBookmark = {
                                if (activeOverlayCardId == link.id) {
                                    activeOverlayCardId = null
                                }
                                viewModel.toggleBookmark(link.id)
                            },
                            onPlay = { url -> viewModel.playVideo(url, link.title, link.id) },
                            onOpenGallery = {
                                val gallery = if (link.galleryUrls.isNotEmpty()) link.galleryUrls else link.originalGalleryUrls
                                if (gallery.isNotEmpty()) {
                                    viewModel.openLightbox(gallery, 0)
                                }
                            },
                            onEdit = {
                                activeOverlayCardId = null
                                viewModel.navigateTo(ScreenState.AddEditLink(link.id))
                            },
                            onDelete = {
                                if (activeOverlayCardId == link.id) {
                                    activeOverlayCardId = null
                                }
                                viewModel.deleteLink(link.id)
                            },
                            onActorClick = { actorId ->
                                activeOverlayCardId = null
                                viewModel.navigateTo(ScreenState.ActorScenes(actorId))
                            },
                            onStudioClick = { studioId ->
                                activeOverlayCardId = null
                                viewModel.navigateTo(ScreenState.StudioScenes(studioId))
                            },
                            onImageError = { viewModel.autoRefreshSexMexCoverIfNeeded(link) },
                            resolvingStatus = resolvingStatus,
                            isResolvingThisCard = resolvingCardId == link.id,
                            resolutionError = if (resolvingCardId == link.id) videoResolutionError else null,
                            onDismissResolutionError = { viewModel.dismissVideoError() },
                            inlinePlayback = if (activeInlineVideo?.cardId == link.id) activeInlineVideo else null,
                            onCloseInlineVideo = { viewModel.closeInlineVideo(link.id) },
                            onFullscreenInlineVideo = { pos -> viewModel.openFullscreenFromInline(link.id, pos, startInLandscape = true) },
                            onFullscreenInlineVideoWithMode = { pos, startInLandscape -> viewModel.openFullscreenFromInline(link.id, pos, startInLandscape = startInLandscape) },
                            onEnterPipInlineVideo = { pos -> viewModel.enterPipFromInline(link.id, pos) },
                            exoPlayer = if (activeInlineVideo?.cardId == link.id) viewModel.sharedPlayerManager.getPlayer() else null,
                            enableVideoPlayerGestures = settings.enableVideoPlayerGestures
                        )
                    }
                }
            }
        }
    }
}
