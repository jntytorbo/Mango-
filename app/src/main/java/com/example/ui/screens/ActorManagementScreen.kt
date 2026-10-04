package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.ActorEntity
import com.example.ui.MainViewModel
import com.example.ui.ManagementSortOption
import com.example.ui.ScreenState
import com.example.ui.theme.AppTransitions
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalBetaTestPrivacy
import com.example.ui.theme.LocalVaultPalette
import com.example.ui.theme.privacyImageBlur
import java.util.UUID

private val PrivacyScrim = Color.Black.copy(alpha = 0.75f) // BG-FIX

enum class ManagementSortOption {
    NAME_AZ,
    NAME_ZA,
    NEWEST,
    OLDEST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActorManagementScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val isLight = palette.name.equals("light", ignoreCase = true)
    val circleBorderColor = if (isLight) Color.Black else Color.White

    val actors by viewModel.allActors.collectAsStateWithLifecycle()
    val actorSceneCounts by viewModel.actorSceneCounts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val showCards = settings.showManagementCards

    val currentScreen by viewModel.screenState.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var actorToEdit by remember { mutableStateOf<ActorEntity?>(null) }
    var sortOption by rememberSaveable { mutableStateOf(ManagementSortOption.NAME_AZ) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(currentScreen, actors) {
        if (currentScreen is ScreenState.AddEditActor) {
            val targetId = (currentScreen as ScreenState.AddEditActor).actorId
            if (targetId != null) {
                actorToEdit = actors.firstOrNull { it.id == targetId }
            } else {
                showAddDialog = true
            }
        }
    }

    val scrollKey = "management_actors"
    val initialScroll = remember { viewModel.getScrollPosition(scrollKey) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialScroll.first,
        initialFirstVisibleItemScrollOffset = initialScroll.second
    )

    val sortedActors = remember(actors, sortOption) {
        when (sortOption) {
            ManagementSortOption.NAME_AZ -> actors.sortedBy { it.name.lowercase() }
            ManagementSortOption.NAME_ZA -> actors.sortedByDescending { it.name.lowercase() }
            ManagementSortOption.RECENTLY_ADDED -> actors.sortedByDescending { it.createdAt }
            ManagementSortOption.OLDEST_ADDED -> actors.sortedBy { it.createdAt }
        }
    }

    LaunchedEffect(gridState, sortedActors.isNotEmpty()) {
        if (sortedActors.isNotEmpty()) {
            snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    viewModel.saveScrollPosition(scrollKey, index, offset)
                }
        }
    }

    var previousSort by rememberSaveable { mutableStateOf(sortOption.name) }
    LaunchedEffect(sortOption) {
        if (previousSort != sortOption.name) {
            previousSort = sortOption.name
            viewModel.saveScrollPosition(scrollKey, 0, 0)
            gridState.scrollToItem(0)
        }
    }

    val topBarContent = LocalTopBarContent.current

    DisposableEffect(Unit) {
        onDispose {
            topBarContent.value = null
        }
    }
    val currentTopBar: @Composable () -> Unit = remember(
        actors.size,
        showSortMenu,
        sortOption,
        palette,
        accent
    ) {
        {
            TopAppBar(
                title = { Text("Actors (${actors.size})", color = palette.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary, modifier = Modifier.size(24.dp))
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("sort_actors_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_app_sort),
                                contentDescription = "Sort",
                                tint = palette.textPrimary,
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
                                text = { Text("A - Z", color = if (sortOption == ManagementSortOption.NAME_AZ) accent else palette.textPrimary) },
                                leadingIcon = {
                                    if (sortOption == ManagementSortOption.NAME_AZ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = accent)
                                    }
                                },
                                onClick = {
                                    sortOption = ManagementSortOption.NAME_AZ
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Z - A", color = if (sortOption == ManagementSortOption.NAME_ZA) accent else palette.textPrimary) },
                                leadingIcon = {
                                    if (sortOption == ManagementSortOption.NAME_ZA) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = accent)
                                    }
                                },
                                onClick = {
                                    sortOption = ManagementSortOption.NAME_ZA
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recently Added", color = if (sortOption == ManagementSortOption.RECENTLY_ADDED) accent else palette.textPrimary) },
                                leadingIcon = {
                                    if (sortOption == ManagementSortOption.RECENTLY_ADDED) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = accent)
                                    }
                                },
                                onClick = {
                                    sortOption = ManagementSortOption.RECENTLY_ADDED
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Oldest Added", color = if (sortOption == ManagementSortOption.OLDEST_ADDED) accent else palette.textPrimary) },
                                leadingIcon = {
                                    if (sortOption == ManagementSortOption.OLDEST_ADDED) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = accent)
                                    }
                                },
                                onClick = {
                                    sortOption = ManagementSortOption.OLDEST_ADDED
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_actor_button")) {
                        Icon(painter = painterResource(id = R.drawable.ic_actor_add), contentDescription = "Add Actor", tint = palette.textPrimary, modifier = Modifier.size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        }
    }
    SideEffect {
        topBarContent.value = currentTopBar
    }

    Scaffold(
        modifier = modifier,
        containerColor = palette.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {}
    ) { padding ->
        if (sortedActors.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painter = painterResource(id = R.drawable.ic_nav_actor), contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(54.dp))
                    Text("No actors in vault", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Add Actor")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = 16.dp,
                    start = 12.dp,
                    end = 12.dp
                ),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedActors, key = { it.id }) { actor ->
                    val sceneCount = actorSceneCounts[actor.id] ?: 0
                    val itemContent = @Composable {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp, bottom = 18.dp, start = 8.dp, end = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val isBetaTest = LocalBetaTestPrivacy.current

                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(palette.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (actor.imageUrl.isNotBlank()) {
                                    val context = LocalContext.current
                                    val imageRequest = remember(actor.imageUrl, context) {
                                        ImageRequest.Builder(context)
                                            .data(actor.imageUrl)
                                            .size(200, 200)
                                            .crossfade(true)
                                            .crossfade(150)
                                            .build()
                                    }
                                    val rawZoom = actor.imageZoom
                                    val z = if (rawZoom > 10f) (rawZoom / 100f).coerceIn(1f, 3f) else rawZoom.coerceIn(1f, 3f)
                                    val biasX = (actor.imagePositionX.coerceIn(0f, 100f) - 50f) / 50f
                                    val biasY = (actor.imagePositionY.coerceIn(0f, 100f) - 50f) / 50f
                                    val hasCustomTransform = z > 1.02f || biasX != 0f || biasY != 0f

                                    AsyncImage(
                                        model = imageRequest,
                                        contentDescription = actor.name,
                                        contentScale = ContentScale.Crop,
                                        alignment = BiasAlignment(biasX, biasY),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .privacyImageBlur(isBetaTest)
                                            .then(
                                                if (hasCustomTransform) {
                                                    Modifier.graphicsLayer {
                                                        val maxX = size.width * (z - 1f) / 2f
                                                        val maxY = size.height * (z - 1f) / 2f
                                                        scaleX = z
                                                        scaleY = z
                                                        translationX = -biasX * maxX
                                                        translationY = -biasY * maxY
                                                    }
                                                } else Modifier
                                            )
                                    )
                                    if (isBetaTest) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(PrivacyScrim) // BG-FIX
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(palette.cardBg.copy(alpha = 0.85f)), // BG-FIX
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_nav_actor),
                                            contentDescription = null,
                                            tint = palette.textSecondary.copy(alpha = 0.9f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(1.5.dp, circleBorderColor, CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = actor.name,
                                color = palette.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "$sceneCount scenes",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (showCards) {
                        Card(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = AppTransitions.itemFadeInSpec,
                                    fadeOutSpec = AppTransitions.itemFadeOutSpec,
                                    placementSpec = AppTransitions.itemPlacementSpec
                                )
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.navigateTo(ScreenState.ActorScenes(actor.id))
                                },
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isLight) 3.dp else 1.5.dp
                            ),
                            colors = CardDefaults.cardColors(containerColor = palette.cardBg) // BG-FIX
                        ) {
                            itemContent()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = AppTransitions.itemFadeInSpec,
                                    fadeOutSpec = AppTransitions.itemFadeOutSpec,
                                    placementSpec = AppTransitions.itemPlacementSpec
                                )
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.navigateTo(ScreenState.ActorScenes(actor.id))
                                }
                        ) {
                            itemContent()
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Actor Dialog
    if (showAddDialog || actorToEdit != null) {
        val editing = actorToEdit
        var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
        var imageUrl by remember(editing) { mutableStateOf(editing?.imageUrl ?: "") }
        var isSaving by remember { mutableStateOf(false) }

        val dismissDialog = {
            showAddDialog = false
            actorToEdit = null
            if (currentScreen is ScreenState.AddEditActor) {
                viewModel.navigateBack()
            }
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            containerColor = palette.cardBg, // BG-FIX
            title = { Text(if (editing != null) "Edit Actor" else "Add Actor", color = palette.textPrimary) }, // BG-FIX
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Actor Name *") },
                        singleLine = true,
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.fillMaxWidth().testTag("actor_name_input")
                    )
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("Profile Image URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Live Circular Preview Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(palette.surface) // BG-FIX
                                .border(1.5.dp, circleBorderColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUrl.trim().isNotEmpty()) {
                                AsyncImage(
                                    model = imageUrl.trim(),
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_nav_actor),
                                    contentDescription = null,
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Preview",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = palette.textPrimary
                            )
                            Text(
                                text = if (imageUrl.trim().isNotEmpty()) "Live actor photo preview" else "No image URL",
                                fontSize = 11.5.sp,
                                color = palette.textMuted
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && !isSaving) {
                            isSaving = true
                            val actor = editing?.copy(
                                name = name.trim(),
                                imageUrl = imageUrl.trim()
                            ) ?: ActorEntity(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                imageUrl = imageUrl.trim()
                            )
                            viewModel.saveActor(actor)
                            dismissDialog()
                        }
                    },
                    enabled = name.isNotBlank() && !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = dismissDialog) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }
}
